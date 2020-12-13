package ru.mpoplavkov.indexation.listener.impl;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FileSystemSubscriber;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * Mapping from watch keys to the paths tracked by those keys.
     */
    private final Map<WatchKey, Path> watchKeysToDir = new ConcurrentHashMap<>();

    /**
     * Set of all tracked paths by this subscriber.
     */
    protected final Set<Path> trackedPaths = ConcurrentHashMap.newKeySet();

    /**
     * Contains the relation between a directory and its children, tracked by
     * this subscriber.
     * This is necessary because of limitations of the {@link WatchService},
     * which allows to register only directories to be watched.
     */
    private final Map<Path, Set<Path>> parentsResponsibleFofChildren = new ConcurrentHashMap<>();

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
     * Creates the subscriber.
     *
     * @param pathFilter filter for paths to check while registration and processing.
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
        if (!pathFilter.filter(path)) {
            return;
        }
        if (trackedPaths.contains(path)) {
            return;
        }
        Path directoryToRegister;
        if (Files.isDirectory(path)) {
            parentsResponsibleFofChildren.remove(path);
            directoryToRegister = path;
        } else {
            Path parent = path.getParent();
            directoryToRegister = parent;
            Set<Path> children = parentsResponsibleFofChildren
                    .computeIfAbsent(parent, p -> ConcurrentHashMap.newKeySet());
            children.add(path);
        }

        WatchKey watchKey = registerPathToTheWatcher(directoryToRegister);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (watchKey) {
            // events, associated with the watch key must be processed
            // exclusively by one thread.
            watchKeysToDir.put(watchKey, directoryToRegister);
            onEvent(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_CREATE, path));
        }
    }

    /**
     * Closes the underlying {@link WatchService} and the listener executor
     * service.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        watcher.close();
        if (listenerExecutorService != null) {
            listenerExecutorService.shutdownNow();
        }
    }

    private final Lock listenerLock = new ReentrantLock();

    /**
     * Creates an {@link ExecutorService} and submits to it a specified
     * number of threads that will listen for events in an infinite loop.
     *
     * @param numberOfListenerThreads number of threads to listen for
     *                                events.
     */
    @Override
    public void startToListenForEvents(int numberOfListenerThreads) {
        listenerLock.lock();
        try {
            if (listenerExecutorService != null) {
                log.log(Level.WARNING, "The listener has already been started");
                return;
            }
            listenerExecutorService = createListenerExecutorService(numberOfListenerThreads);

            for (int i = 0; i < numberOfListenerThreads; i++) {
                listenerExecutorService.execute(this::listenLoop);
            }
        } finally {
            listenerLock.unlock();
        }
    }

    /**
     * Waits for events and processes them until the listener is cancelled.
     */
    // TODO: think what to do in case of an error
    @SneakyThrows
    public void listenLoop() {
        while (true) {
            try {
                waitForFSEventAndProcessIt();
            } catch (ClosedWatchServiceException e) {
                break;
            }
        }
    }

    /**
     * Waits for a file system event, checks if it's appropriate and processes
     * it.
     *
     * @throws IOException if an I/O error occurs.
     */
    private void waitForFSEventAndProcessIt() throws IOException {
        // wait for key to be signaled
        WatchKey key;
        try {
            key = watcher.take();
        } catch (InterruptedException x) {
            return;
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (key) {
            // events, associated with this key are also processed during registration,
            // that's why synchronization is necessary.
            Path dir = watchKeysToDir.get(key);
            if (dir != null) {
                log.log(Level.INFO, "Some events occurred for directory [{0}]", dir.toAbsolutePath());
                List<WatchEvent<?>> events = key.pollEvents();
                for (WatchEvent<?> event : events) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // TODO: deal with it
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path changedPath = dir.resolve(ev.context());
                    if (!pathFilter.filter(changedPath)) {
                        continue;
                    }

                    Set<Path> dependentChildren = parentsResponsibleFofChildren.get(dir);
                    if (dependentChildren != null && !dependentChildren.contains(changedPath)) {
                        // skip the processing of not dependent files
                        continue;
                    }

                    FileSystemEvent fsEvent = watchEventToFSEvent(ev.kind(), changedPath);

                    onEvent(fsEvent);
                }
            }

            // If the key is no longer valid, the directory is inaccessible so stop
            // listening to its changes.
            boolean valid = key.reset();
            if (!valid) {
                watchKeysToDir.remove(key);
                if (dir != null) {
                    log.log(Level.INFO, "Stop tracking directory [{0}]", dir.toAbsolutePath());
                    Set<Path> removedChildren = parentsResponsibleFofChildren.remove(dir);
                    for (Path child : removedChildren) {
                        trackedPaths.remove(child);
                    }
                    trackedPaths.remove(dir);
                }
            }
        }
    }

    /**
     * Registers given path to the watcher with MODIFY and CREATE events to trigger.
     *
     * @param path the path to register.
     * @return a key representing the registration of this object with the watch
     * service.
     * @throws IOException if an I/O error occurs.
     */
    private WatchKey registerPathToTheWatcher(Path path) throws IOException {
        return path.register(watcher, ENTRY_MODIFY, ENTRY_CREATE);
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
            public Thread newThread(Runnable r) {
                    String threadName = String.format("listener-%d", count.incrementAndGet());
                Thread thread = new Thread(r, threadName);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

}
