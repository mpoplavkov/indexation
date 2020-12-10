package ru.mpoplavkov.indexation.trigger;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.*;

@RequiredArgsConstructor
public class FileSystemEventListener implements EventListener<Path> {

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

    @Override
    public void register(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            // TODO: order, sync?
            WatchKey watchKey = path.register(watcher);
            trackedPaths.put(watchKey, path);
        } else {
            // TODO: order, sync?
            Path parent = path.getParent();
            WatchKey watchKey = parent.register(watcher);
            trackedPaths.put(watchKey, path);
            Set<Path> children = parentsResponsibleFofChildren
                    .computeIfAbsent(parent, p -> new HashSet<>());
            children.add(path);
        }
        // TODO: add dir contents to the index
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

    @Override
    public void listenLoop() {
        //noinspection InfiniteLoopStatement
        while (true) {
            waitForFSEventAndProcessIt();
        }
    }

    private void waitForFSEventAndProcessIt() {
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

                triggers.forEach(tr -> tr.onEvent(fsEvent));
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
