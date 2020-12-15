package ru.mpoplavkov.indexation.listener.impl;

import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WatchServiceFSSubscriber extends WatchServiceFSSubscriberBase {

    private final List<FSEventTrigger> triggers;

    /**
     * Creates the subscriber.
     *
     * @param pathFilter filter for files to check while registration and processing.
     * @param triggers   triggers to apply for found events.
     * @throws IOException if an I/O error occurs.
     */
    public WatchServiceFSSubscriber(PathFilter pathFilter, FSEventTrigger... triggers) throws IOException {
        super(pathFilter);
        this.triggers = Arrays.asList(triggers);
    }

    /**
     * Handles some subscriber specific logic for event processing as well
     * as user specific processing afterwards.
     *
     * @param event the event to process
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void onEvent(FileSystemEvent event) throws IOException {
        if (isOrWasADirectory(event.getEntry())) {
            directoryEventSubscriberSpecificProcessing(event);
        } else {
            fileEventSubscriberSpecificProcessing(event);
        }

        // user defined processing
        for (FSEventTrigger trigger : triggers) {
            trigger.onEvent(event);
        }
    }

    private void directoryEventSubscriberSpecificProcessing(FileSystemEvent event) throws IOException {
        Path dir = event.getEntry();
        switch (event.getKind()) {
            case ENTRY_CREATE:
                List<Path> children = Files.list(dir).collect(Collectors.toList());
                for (Path child : children) {
                    if (Files.isDirectory(child)) {
                        subscribeInner(child, Optional.empty());
                    } else {
                        onEvent(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_CREATE, child));
                    }
                }
                break;
            case ENTRY_DELETE:
                Set<Path> directoryFiles =
                        trackedPaths.get(dir)
                                .stream()
                                .filter(p -> !isOrWasADirectory(p))
                                .collect(Collectors.toSet());
                trackedPaths.remove(dir);
                dirsResponsibleForEveryChild.remove(dir);
                for (Path file : directoryFiles) {
                    onEvent(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_DELETE, file));
                }
                locksMap.remove(dir);
                break;
            case ENTRY_MODIFY:
                // nothing, cause modified directory will be processed directly
                break;
        }
    }

    private void fileEventSubscriberSpecificProcessing(FileSystemEvent event) {
        if (event.getKind() == FileSystemEvent.Kind.ENTRY_CREATE) {
            Path file = event.getEntry();
            trackedPaths
                    .computeIfAbsent(file.getParent(), p -> ConcurrentHashMap.newKeySet())
                    .add(file);
        }
    }

    /**
     * Checks if the given path is a directory or was a directory (in case
     * if this path was already deleted).
     *
     * @param path path to check
     * @return true if is or was a directory.
     */
    private boolean isOrWasADirectory(Path path) {
        return Files.isDirectory(path) || trackedPaths.containsKey(path);
    }
}
