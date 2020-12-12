package ru.mpoplavkov.indexation.listener.impl;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.listener.FileSystemEventListener;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Implementation of {@link FileSystemEventListener}, based on the
 * {@link WatchService}. Takes care about processing the events for
 * a particular path at any time exclusively by only one thread.
 */
@Log
public class WatchServiceBasedListenerImpl implements FileSystemEventListener {

    private final WatchService watcher;
    private final List<FSEventTrigger> triggers;
    private final PathFilter pathFilter;

    /**
     * Mapping from watch keys to the paths tracked by those keys.
     */
    private final Map<WatchKey, Path> trackedDirs = new ConcurrentHashMap<>();

    /**
     * Contains the relation between a directory and its children, tracked by
     * this listener.
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
     * Creates the listener.
     *
     * @param pathFilter filter for files to check while registration and processing.
     * @param triggers   triggers to apply for found events.
     * @throws IOException if an I/O error occurs.
     */
    public WatchServiceBasedListenerImpl(PathFilter pathFilter, FSEventTrigger... triggers) throws IOException {
        this.pathFilter = pathFilter;
        this.triggers = Arrays.asList(triggers);
        watcher = FileSystems.getDefault().newWatchService();
    }

    /**
     * Records information about the path to track and processes the
     * path in the same way as if it was just created in the file system.
     *
     * @param path the path to register.
     * @return true iff the path passed the filter, false otherwise.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public boolean register(Path path) throws IOException {
        return registerInner(path);
    }

    private boolean registerInner(Path initialPath) throws IOException {
        if (!pathFilter.filter(initialPath)) {
            return false;
        }
        Queue<Path> queue = new LinkedList<>();
        queue.add(initialPath);
        while (!queue.isEmpty()) {
            Path path = queue.poll();
            Path directoryToRegister;
            if (Files.isDirectory(path)) {
                for (Path p : Files.newDirectoryStream(path)) {
                    if (Files.isDirectory(p)) {
                        queue.add(p);
                    }
                }
                parentsResponsibleFofChildren.remove(path);
                directoryToRegister = path;
            } else {
                Path parent = path.getParent();
                directoryToRegister = parent;
                Set<Path> children = parentsResponsibleFofChildren
                        .computeIfAbsent(parent, p -> ConcurrentHashMap.newKeySet());
                children.add(path);
            }

            FileSystemEvent event = new FileSystemEvent(FileSystemEvent.Kind.ENTRY_CREATE, path);
            WatchKey watchKey = registerPathToTheWatcher(directoryToRegister);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (watchKey) {
                // events, associated with the watch key must be processed
                // exclusively by one thread.
                trackedDirs.put(watchKey, directoryToRegister);
                processFSEvent(event);
            }
        }
        return true;
    }

    /**
     * Closes the underlying {@link WatchService}. This will also cause
     * cancellation of all the listen loops, associated with this listener.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        watcher.close();
    }

    /**
     * Waits for events and processes them until the listener is cancelled.
     */
    // TODO: think what to do in case of an error
    @SneakyThrows
    @Override
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
     * Passes the event to all triggers, associated with the listener.
     *
     * @param event occurred event.
     * @throws IOException if an I/O error occurs.
     */
    private void processFSEvent(FileSystemEvent event) throws IOException {
        log.log(Level.INFO, "Processing event [{0}]", event);
        for (FSEventTrigger trigger : triggers) {
            trigger.onEvent(event);
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
            Path dir = trackedDirs.get(key);
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
                    FileSystemEvent fsEvent = watchEventToFSEvent(ev.kind(), changedPath);

                    Set<Path> dependentChildren = parentsResponsibleFofChildren.get(dir);
                    if (dependentChildren != null && !dependentChildren.contains(changedPath)) {
                        // skip the processing of not dependent files
                        continue;
                    }

                    processFSEvent(fsEvent);
                }
            }

            // If the key is no longer valid, the directory is inaccessible so stop
            // listening to its changes.
            boolean valid = key.reset();
            if (!valid) {
                trackedDirs.remove(key);
                if (dir != null) {
                    log.log(Level.INFO, "Stop tracking directory [{0}]", dir.toAbsolutePath());
                    parentsResponsibleFofChildren.remove(dir);
                }
            }
        }
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

    /**
     * Registers given path to the watcher with all possible events to trigger.
     *
     * @param path the path to register.
     * @return a key representing the registration of this object with the watch
     * service.
     * @throws IOException if an I/O error occurs.
     */
    private WatchKey registerPathToTheWatcher(Path path) throws IOException {
        return path.register(watcher, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
    }

}
