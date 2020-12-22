package ru.mpoplavkov.indexation.listener.impl;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FileSystemSubscriber;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.util.FileUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * {@link FileSystemSubscriber}, based on the {@link WatchService}.
 * Takes care about processing the events for a particular path at
 * any time exclusively by only one thread. Allows to register
 * directories as well as regular files.
 */
@Log
public abstract class WatchServiceFSSubscriberBase implements FileSystemSubscriber {

    private final WatchService watcher;
    private final PathFilter pathFilter;
    private ExecutorService listenerExecutorService;

    /**
     * Mapping from watch keys to paths tracked by those keys.
     */
    private final Map<WatchKey, Path> watchKeysToDirs = new ConcurrentHashMap<>();

    /**
     * Mapping from paths to their watch keys.
     */
    private final Map<Path, WatchKey> dirsToWatchKeys = new ConcurrentHashMap<>();

    /**
     * Map from tracked paths to their registered children.
     */
    protected final Map<Path, Set<Path>> trackedPaths = new ConcurrentHashMap<>();

    /**
     * Set of registered directories. This set doesn't contain directories, that
     * were registered to the watcher in order to track specific files updates.
     */
    protected final Set<Path> dirsResponsibleForNewFiles = ConcurrentHashMap.newKeySet();

    /**
     * Map containing lock for every directory used in the subscriber. This is necessary
     * in order to process directories exclusively by one thread.
     */
    protected final Map<Path, Lock> locksMap = new ConcurrentHashMap<>();

    /**
     * Mapping between {@link WatchEvent.Kind} and {@link FileSystemEvent.Kind}.
     */
    private static final Map<WatchEvent.Kind<Path>, FileSystemEvent.Kind> EVENT_KINDS_MAP =
            ImmutableMap.of(
                    ENTRY_CREATE, FileSystemEvent.Kind.ENTRY_CREATE,
                    ENTRY_MODIFY, FileSystemEvent.Kind.ENTRY_MODIFY,
                    ENTRY_DELETE, FileSystemEvent.Kind.ENTRY_DELETE
            );

    /**
     * Initializes the subscriber base.
     *
     * @param pathFilter filter for paths to check while subscription and processing.
     * @throws IOException if an I/O error occurs.
     */
    public WatchServiceFSSubscriberBase(PathFilter pathFilter) throws IOException {
        this.pathFilter = pathFilter;
        watcher = FileSystems.getDefault().newWatchService();
    }

    /**
     * Records information about the path to track and processes it
     * in the same way as if it was just created in the file system.
     *
     * @param path the path to register.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void subscribe(Path path) throws IOException {
        Path canonicalPath = FileUtil.toCanonicalPath(path);
        checkPathExists(canonicalPath);
        if (!pathFilter.filter(canonicalPath)) {
            log.info(() ->
                    String.format("Skipping path '%s' due to filtration logic", FileUtil.getCanonicalPath(canonicalPath))
            );
            return;
        }

        if (Files.isDirectory(canonicalPath)) {
            subscribeInner(canonicalPath, Optional.empty());
        } else {
            Path parent = canonicalPath.getParent();
            subscribeInner(parent, Optional.of(canonicalPath));
        }
    }

    /**
     * Contains inner logic for subscription.
     * Registers given directory to the watcher and processes a create event of
     * the directory or the file (if {@code childToSubscribe} is not empty).
     *
     * @param dir              directory to subscribe.
     * @param childToSubscribe concrete file to listen in this directory. If empty,
     *                         the full directory will be listened.
     * @throws IOException if an I/O error occurs.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected void subscribeInner(Path dir, Optional<Path> childToSubscribe) throws IOException {
        Lock lock = getLockFor(dir);
        // events, associated with the directory must be processed
        // exclusively by one thread.
        lock.lock();
        try {
            Set<Path> pathsTrackedByThisDir = trackedPaths.getOrDefault(dir, Collections.emptySet());
            if (childToSubscribe.isPresent()) {
                if (pathsTrackedByThisDir.contains(childToSubscribe.get())) {
                    // nothing to do, already subscribed
                    return;
                }
            } else {
                boolean alreadyWas = !dirsResponsibleForNewFiles.add(dir);
                if (alreadyWas) {
                    // nothing to do, already subscribed
                    return;
                }
            }

            WatchKey watchKey = registerDirToTheWatcher(dir);
            watchKeysToDirs.put(watchKey, dir);
            dirsToWatchKeys.put(dir, watchKey);
            // TODO: get rid of synchronization chain?
            //  There could be no dead locks, cause every next lock associates
            //  with the child of the locked directory, i.e. locks are ordered.
            //  So this is not critical.
            onEvent(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_CREATE, childToSubscribe.orElse(dir)));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unsubscribe(Path path) throws IOException {
        Path canonicalPath = FileUtil.toCanonicalPath(path);
        checkPathExists(canonicalPath);
        if (Files.isDirectory(canonicalPath)) {
            unsubscribeInner(canonicalPath, Optional.empty());
        } else {
            Path parent = canonicalPath.getParent();
            unsubscribeInner(parent, Optional.of(canonicalPath));
        }
    }

    /**
     * Contains inner logic for unsubscription.
     * Removes {@code childToUnsubscribe} from {@code trackedPaths} if it
     * was not empty. Processes the event of deletion of the given path.
     *
     * @param dir                directory to unsubscribe from.
     * @param childToUnsubscribe concrete file to unsubscribe from in this directory.
     *                           If empty, the full directory will be unsubscribed.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void unsubscribeInner(Path dir, Optional<Path> childToUnsubscribe) {
        Set<Path> children = trackedPaths.get(dir);
        if (children == null) {
            // nothing to do, wasn't subscribed
            return;
        }
        Lock lock = getLockFor(dir);
        lock.lock();
        try {
            if (childToUnsubscribe.isPresent()) {
                Path child = childToUnsubscribe.get();
                boolean wasTracked = children.remove(child);
                if (!wasTracked) {
                    // nothing to do
                    return;
                }
                if (children.isEmpty() && !dirsResponsibleForNewFiles.contains(dir)) {
                    cancelWatchKeyFor(dir);
                }
            } else {
                cancelWatchKeyFor(dir);
                // unsubscribe all child directories
                for (Path path : children) {
                    if (Files.isDirectory(path)) {
                        unsubscribeInner(path, Optional.empty());
                    }
                }
            }

            onEvent(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_DELETE, childToUnsubscribe.orElse(dir)));
        } finally {
            lock.unlock();
        }
    }

    private void cancelWatchKeyFor(Path dir) {
        WatchKey watchKey = dirsToWatchKeys.get(dir);
        if (watchKey == null) {
            return;
        }
        watchKey.cancel();
    }

    private void checkPathExists(Path path) throws FileNotFoundException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException(
                    String.format("Path '%s' doesn't exist", FileUtil.getCanonicalPath(path))
            );
        }
    }

    /**
     * Specifies how to process an occurred file system event.
     *
     * @param event the event to process
     */
    protected abstract void onEvent(FileSystemEvent event);

    private final Lock listenerLock = new ReentrantLock();

    /**
     * Closes the underlying {@link WatchService} and the listener executor
     * service.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        log.info("Closing subscriber");
        watcher.close();
        listenerLock.lock();
        try {
            if (listenerExecutorService != null) {
                listenerExecutorService.shutdownNow();
            }
        } finally {
            listenerLock.unlock();
        }
    }

    /**
     * Creates an {@link ExecutorService} and submits to it a specified
     * number of threads that will listen for events in an infinite loop.
     *
     * @param numberOfListenerThreads number of threads to listen for events.
     */
    @Override
    public void startToListenForEvents(int numberOfListenerThreads) {
        listenerLock.lock();
        try {
            if (listenerExecutorService != null) {
                throw new RuntimeException("The listener has already been started");
            }
            listenerExecutorService = createListenerExecutorService(numberOfListenerThreads);

            for (int i = 0; i < numberOfListenerThreads; i++) {
                listenerExecutorService.execute(this::listenLoop);
            }
            log.info("Listener started");
        } finally {
            listenerLock.unlock();
        }
    }

    /**
     * Waits for events and processes them until the listener is cancelled.
     */
    @SneakyThrows
    private void listenLoop() {
        while (true) {
            try {
                waitForFSEventAndProcessIt();
            } catch (ClosedWatchServiceException e) {
                log.info("Leaving listener loop cause of closed watcher");
                break;
            } catch (Exception e) {
                log.log(Level.SEVERE, e, () -> "Exception occurred in the listener loop. Continue");
            }
        }
    }

    /**
     * Waits for a file system event, checks if it's appropriate and processes
     * it.
     */
    private void waitForFSEventAndProcessIt() {
        // wait for key to be signaled
        WatchKey key;
        try {
            key = watcher.take();
        } catch (InterruptedException x) {
            return;
        }
        Path dir = watchKeysToDirs.get(key);
        if (dir == null) {
            // wait until watchKeysToDir will be updated
            key.reset();
            return;
        }

        Lock lock = getLockFor(dir);
        // events, associated with this key are also processed during registration,
        // that's why synchronization is necessary.
        lock.lock();
        try {
            log.info(() -> String.format("Some events occurred for directory '%s'", FileUtil.getCanonicalPath(dir)));
            List<WatchEvent<?>> events = key.pollEvents();
            for (WatchEvent<?> event : events) {
                WatchEvent.Kind<?> kind = event.kind();

                // TODO: deal with it
                if (kind == OVERFLOW) {
                    log.severe("Overflow occurred");
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path changedPath = dir.resolve(ev.context());
                if (!pathFilter.filter(changedPath)) {
                    continue;
                }

                boolean needToHandleCreate = ev.kind() == ENTRY_CREATE &&
                        dirsResponsibleForNewFiles.contains(dir);
                Set<Path> pathsTrackedByThisDir = trackedPaths.getOrDefault(dir, Collections.emptySet());
                if (needToHandleCreate || pathsTrackedByThisDir.contains(changedPath)) {

                    FileSystemEvent fsEvent = watchEventToFSEvent(ev.kind(), changedPath);
                    onEvent(fsEvent);
                }
            }
        } finally {
            try {
                boolean valid = key.reset();
                // If the key is no longer valid, the directory is inaccessible so stop
                // listening to its changes.
                if (!valid) {
                    watchKeysToDirs.remove(key);
                    dirsToWatchKeys.remove(dir);
                    log.info(() -> String.format("Stop tracking directory '%s'", FileUtil.getCanonicalPath(dir)));
                    FileSystemEvent fsEvent = new FileSystemEvent(FileSystemEvent.Kind.ENTRY_DELETE, dir);
                    onEvent(fsEvent);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Registers given path to the watcher with all possible events to trigger.
     *
     * @param path the path to register.
     * @return a key representing the registration of this object with the watch
     * service.
     * @throws IOException if an I/O error occurs.
     */
    private WatchKey registerDirToTheWatcher(Path path) throws IOException {
        return path.register(watcher, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
    }

    private FileSystemEvent watchEventToFSEvent(WatchEvent.Kind<Path> watchEventKind, Path changedPath) {
        FileSystemEvent.Kind kind = EVENT_KINDS_MAP.get(watchEventKind);
        if (kind == null) {
            throw new UnsupportedOperationException(
                    String.format("Watch event kind '%s' is not supported", watchEventKind)
            );
        }
        return new FileSystemEvent(kind, changedPath);
    }

    private ExecutorService createListenerExecutorService(int parallelism) {
        return Executors.newFixedThreadPool(parallelism, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(@NotNull Runnable r) {
                String threadName = String.format("listener-%d", count.incrementAndGet());
                Thread thread = new Thread(r, threadName);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private Lock getLockFor(Path path) {
        return locksMap.computeIfAbsent(path, p -> new ReentrantLock());
    }

}
