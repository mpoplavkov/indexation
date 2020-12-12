package ru.mpoplavkov.indexation.listener.impl;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.FileFilter;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.listener.FileSystemEventListener;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static java.nio.file.StandardWatchEventKinds.*;

@Log
public class FSEventListenerImpl implements FileSystemEventListener {

    private final WatchService watcher;
    private final List<FSEventTrigger> triggers;
    private final FileFilter fileFilter;

    private final Map<WatchKey, Path> trackedDirs = new ConcurrentHashMap<>();

    /**
     * Contains the relation between a directory and its children, tracked by
     * this listener.
     * This is necessary because of limitations of the {@link WatchService},
     * which allows to register only directories to be watched.
     */
    private final Map<Path, Set<Path>> parentsResponsibleFofChildren = new ConcurrentHashMap<>();

    private static final Map<WatchEvent.Kind<Path>, FileSystemEvent.Kind> EVENT_KINDS_MAP =
            ImmutableMap.of(
                    ENTRY_CREATE, FileSystemEvent.Kind.FILE_CREATE,
                    ENTRY_MODIFY, FileSystemEvent.Kind.FILE_UPDATE,
                    ENTRY_DELETE, FileSystemEvent.Kind.FILE_DELETE
            );

    public FSEventListenerImpl(FileFilter fileFilter, FSEventTrigger... triggers) throws IOException {
        this.fileFilter = fileFilter;
        this.triggers = Arrays.asList(triggers);
        watcher = FileSystems.getDefault().newWatchService();
    }

    @Override
    public boolean register(Path path) throws IOException {
        FileSystemEvent.Kind eventKind;
        Path directoryToRegister;
        if (Files.isDirectory(path)) {
            parentsResponsibleFofChildren.remove(path);
            eventKind = FileSystemEvent.Kind.DIRECTORY_CREATE;
            directoryToRegister = path;
        } else {
            if (!fileFilter.filter(path)) {
                return false;
            }
            eventKind = FileSystemEvent.Kind.FILE_CREATE;
            Path parent = path.getParent();
            directoryToRegister = parent;
            Set<Path> children = parentsResponsibleFofChildren
                    .computeIfAbsent(parent, p -> ConcurrentHashMap.newKeySet());
            children.add(path);
        }

        FileSystemEvent event = new FileSystemEvent(eventKind, path);
        WatchKey watchKey = registerPathToTheWatcher(directoryToRegister);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (watchKey) {
            trackedDirs.put(watchKey, directoryToRegister);
            processFSEvent(event);
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

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

    private void processFSEvent(FileSystemEvent event) throws IOException {
        log.log(Level.INFO, "Processing event [{0}]", event);
        for (FSEventTrigger trigger : triggers) {
            trigger.onEvent(event);
        }
    }

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
            Path dir = trackedDirs.get(key);
            if (dir != null) {
                log.log(Level.INFO, "Some events occurred for directory [{0}]", dir.toAbsolutePath());
                List<WatchEvent<?>> events = key.pollEvents();
                for (WatchEvent<?> event : events) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path changedFile = dir.resolve(ev.context());
                    if (!fileFilter.filter(changedFile)) {
                        continue;
                    }
                    FileSystemEvent fsEvent = watchEventToFSEvent(ev.kind(), changedFile);

                    Set<Path> dependentChildren = parentsResponsibleFofChildren.get(dir);
                    if (dependentChildren != null && !dependentChildren.contains(changedFile)) {
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

    private FileSystemEvent watchEventToFSEvent(WatchEvent.Kind<Path> watchEventKind, Path changedFile) {
        FileSystemEvent.Kind kind = EVENT_KINDS_MAP.get(watchEventKind);
        if (kind == null) {
            throw new UnsupportedOperationException(
                    String.format("Watch event kind '%s' is not supported", watchEventKind)
            );
        }
        return new FileSystemEvent(kind, changedFile);
    }

    private WatchKey registerPathToTheWatcher(Path path) throws IOException {
        return path.register(watcher, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
    }

}
