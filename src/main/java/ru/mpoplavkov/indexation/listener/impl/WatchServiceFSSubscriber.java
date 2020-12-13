package ru.mpoplavkov.indexation.listener.impl;

import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WatchServiceFSSubscriber extends WatchServiceFSSubscriberBase {

    private final List<FSEventTrigger> triggers;

    /**
     * Creates the listener.
     *
     * @param pathFilter filter for files to check while registration and processing.
     * @param triggers   triggers to apply for found events.
     * @throws IOException if an I/O error occurs.
     */
    public WatchServiceFSSubscriber(PathFilter pathFilter, FSEventTrigger... triggers) throws IOException {
        super(pathFilter);
        this.triggers = Arrays.asList(triggers);
    }

    @Override
    public void onEvent(FileSystemEvent event) throws IOException {
        if (Files.isDirectory(event.getEntry())) {
            processDirectoryEvent(event);
        } else {
            processFileEvent(event);
        }
    }

    private void processDirectoryEvent(FileSystemEvent event) throws IOException {
        Path dir = event.getEntry();
        if (event.getKind() == FileSystemEvent.Kind.ENTRY_DELETE) {
            return;
        }
        if (trackedPaths.contains(dir)) {
            // will be processed by the watcher, responsible for it
            return;
        }
        List<Path> children = Files.list(dir).collect(Collectors.toList());
        for (Path child : children) {
            if (Files.isDirectory(child)) {
                subscribe(child);
            } else {
                processFileEvent(new FileSystemEvent(event.getKind(), child));
            }
        }
        trackedPaths.add(dir);
    }

    private void processFileEvent(FileSystemEvent event) throws IOException {
        for (FSEventTrigger trigger : triggers) {
            trigger.onEvent(event);
        }
    }
}
