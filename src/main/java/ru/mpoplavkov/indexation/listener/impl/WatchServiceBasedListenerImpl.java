package ru.mpoplavkov.indexation.listener.impl;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.listener.FileSystemEventListener;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
    private final Map<WatchKey, Path> watchKeysToDir = new ConcurrentHashMap<>();

    private final Set<Path> trackedDirs = ConcurrentHashMap.newKeySet();

    /**
     * Contains the relation between a directory and its children, tracked by
     * this listener.
     * This is necessary because of limitations of the {@link WatchService},
     * which allows to register only directories to be watched.
     */
    private final Map<Path, Set<Path>> parentsResponsibleFofChildren = new ConcurrentHashMap<>();

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
    public void register(Path path) throws IOException {
        registerInner(path);
    }

    private void registerInner(Path path) throws IOException {
        if (!pathFilter.filter(path)) {
            return;
        }
        Path directoryToRegister;
        if (Files.isDirectory(path)) {
            parentsResponsibleFofChildren.remove(path);
            if (trackedDirs.contains(path)) {
                return;
            }
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
            processFSEvent(path);
        }
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

    private void processFSEvent(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            processDirectoryEvent(path);
        } else {
            processFileEvent(path);
        }
    }

    private void processDirectoryEvent(Path dir) throws IOException {
        Preconditions.checkArgument(Files.isDirectory(dir));
        if (!trackedDirs.contains(dir)) {
            // else will be processed by the watcher, responsible for it
            List<Path> children = Files.list(dir).collect(Collectors.toList());
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    registerInner(child);
                } else {
                    processFileEvent(child);
                }
            }
            trackedDirs.add(dir);
        }
    }

    private void processFileEvent(Path file) throws IOException {
        Preconditions.checkArgument(!Files.isDirectory(file));
        for (FSEventTrigger trigger : triggers) {
            trigger.onEvent(file);
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

                    processFSEvent(changedPath);
                }
            }

            // If the key is no longer valid, the directory is inaccessible so stop
            // listening to its changes.
            boolean valid = key.reset();
            if (!valid) {
                watchKeysToDir.remove(key);
                if (dir != null) {
                    log.log(Level.INFO, "Stop tracking directory [{0}]", dir.toAbsolutePath());
                    parentsResponsibleFofChildren.remove(dir);
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

}
