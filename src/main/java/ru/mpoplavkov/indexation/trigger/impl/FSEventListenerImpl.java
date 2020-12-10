package ru.mpoplavkov.indexation.trigger.impl;

import lombok.SneakyThrows;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;
import ru.mpoplavkov.indexation.trigger.FileSystemEventListener;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.*;

public class FSEventListenerImpl implements FileSystemEventListener {

    private final WatchService watcher;
    private final List<FSEventTrigger> triggers;

    // TODO: concurrent?
    private final Map<WatchKey, Path> trackedPaths = new HashMap<>();

    // TODO: concurrent?
    /**
     * Contains the relation between a directory and its children, tracked by
     * this listener.
     * This is necessary because of limitations of the {@link WatchService},
     * which allows to register only directories to be watched.
     */
    private final Map<Path, Set<Path>> parentsResponsibleFofChildren = new HashMap<>();

    private final Map<WatchEvent.Kind<Path>, FileSystemEvent.Kind> watchEventKindToFileSystemEventKind = new HashMap<>();

    {
        watchEventKindToFileSystemEventKind.put(ENTRY_CREATE, FileSystemEvent.Kind.FILE_CREATE);
        watchEventKindToFileSystemEventKind.put(ENTRY_MODIFY, FileSystemEvent.Kind.FILE_UPDATE);
        watchEventKindToFileSystemEventKind.put(ENTRY_DELETE, FileSystemEvent.Kind.FILE_DELETE);
    }

    public FSEventListenerImpl(FSEventTrigger... triggers) throws IOException {
        this.triggers = Arrays.asList(triggers);
        watcher = FileSystems.getDefault().newWatchService();
    }

    @Override
    public void register(Path path) throws IOException {
        FileSystemEvent.Kind eventKind;
        if (Files.isDirectory(path)) {
            eventKind = FileSystemEvent.Kind.DIRECTORY_CREATE;
            // TODO: order, sync?
            WatchKey watchKey = path.register(watcher);
            trackedPaths.put(watchKey, path);
        } else {
            eventKind = FileSystemEvent.Kind.FILE_CREATE;
            // TODO: order, sync?
            Path parent = path.getParent();
            WatchKey watchKey = parent.register(watcher);
            trackedPaths.put(watchKey, path);
            Set<Path> children = parentsResponsibleFofChildren
                    .computeIfAbsent(parent, p -> new HashSet<>());
            children.add(path);
        }
        FileSystemEvent event = new FileSystemEvent(eventKind, path);
        processFSEvent(event);
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

    // TODO: think what to do in case of an error
    @SneakyThrows
    @Override
    public void listenLoop() {
        //noinspection InfiniteLoopStatement
        while (true) {
            waitForFSEventAndProcessIt();
        }
    }

    private void processFSEvent(FileSystemEvent event) throws IOException {
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

        Path dir = trackedPaths.get(key);
        if (dir != null) {
            List<WatchEvent<?>> events = key.pollEvents();
            for (WatchEvent<?> event : events) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path changedFile = dir.resolve(ev.context());
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
            trackedPaths.remove(key);
            if (dir != null) {
                parentsResponsibleFofChildren.remove(dir);
            }
        }
    }

    private FileSystemEvent watchEventToFSEvent(WatchEvent.Kind<Path> watchEventKind, Path changedFile) {
        FileSystemEvent.Kind kind = watchEventKindToFileSystemEventKind.get(watchEventKind);
        if (kind == null) {
            throw new UnsupportedOperationException(
                    String.format("Watch event kind '%s' is not supported", watchEventKind)
            );
        }
        return new FileSystemEvent(kind, changedFile);
    }

}
