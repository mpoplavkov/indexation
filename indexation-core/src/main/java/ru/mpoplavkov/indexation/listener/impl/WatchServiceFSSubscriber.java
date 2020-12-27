package ru.mpoplavkov.indexation.listener.impl;

import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.listener.FileSystemSubscriber;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.util.RetryUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link FileSystemSubscriber}, based on the {@link WatchService}.
 * Accumulates file system events processing logic.
 */
@Log
public class WatchServiceFSSubscriber extends WatchServiceFSSubscriberBase {

    private final FSEventTrigger trigger;

    /**
     * Creates the subscriber.
     *
     * @param pathFilter filter for files to check while registration and processing.
     * @param trigger    trigger to apply for found events.
     * @throws IOException if an I/O error occurs.
     */
    public WatchServiceFSSubscriber(PathFilter pathFilter, FSEventTrigger trigger) throws IOException {
        super(pathFilter);
        this.trigger = trigger;
    }

    /**
     * Handles some subscriber specific logic for event processing as well
     * as user specific processing afterwards.
     *
     * @param event the event to process
     */
    @Override
    protected void onEvent(FileSystemEvent event) {
        log.info(() -> String.format("Processing event '%s'", event));
        RetryUtil.retry(() -> onEventInner(event), 3);
        log.info(() -> String.format("Event processed '%s'", event));
    }

    private void onEventInner(FileSystemEvent event) throws IOException {
        if (isOrWasADirectory(event.getEntry())) {
            directoryEventSubscriberSpecificProcessing(event);
        } else {
            fileEventSubscriberSpecificProcessing(event);
        }

        // user defined processing
        trigger.onEvent(event);
    }

    private void directoryEventSubscriberSpecificProcessing(FileSystemEvent event) throws IOException {
        Path dir = event.getEntry();
        switch (event.getKind()) {
            case ENTRY_CREATE:
                Path parentDir = dir.getParent();
                if (parentDir != null) {
                    trackedPaths
                            .computeIfAbsent(parentDir, p -> ConcurrentHashMap.newKeySet())
                            .add(dir);
                }
                List<Path> children = Files.list(dir).collect(Collectors.toList());
                for (Path child : children) {
                    if (Files.isDirectory(child)) {
                        subscribeInner(child, Optional.empty());
                    } else {
                        onEventInner(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_CREATE, child));
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
                dirsResponsibleForNewFiles.remove(dir);
                for (Path file : directoryFiles) {
                    onEventInner(new FileSystemEvent(FileSystemEvent.Kind.ENTRY_DELETE, file));
                }
                locksMap.remove(dir);
                break;
            case ENTRY_MODIFY:
                // nothing, cause modified directory will be processed directly
                break;
            default:
                throw new RuntimeException(
                        String.format("FSEvent kind '%s' is not supported", event.getKind())
                );
        }
    }

    private void fileEventSubscriberSpecificProcessing(FileSystemEvent event) {
        Path file = event.getEntry();
        switch (event.getKind()) {
            case ENTRY_CREATE:
                trackedPaths
                        .computeIfAbsent(file.getParent(), p -> ConcurrentHashMap.newKeySet())
                        .add(file);
                break;
            case ENTRY_DELETE:
                Set<Path> set = trackedPaths.getOrDefault(file.getParent(), Collections.emptySet());
                set.remove(file);
                break;
            case ENTRY_MODIFY:
                // nothing
                break;
            default:
                throw new RuntimeException(
                        String.format("FSEvent kind '%s' is not supported", event.getKind())
                );
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
