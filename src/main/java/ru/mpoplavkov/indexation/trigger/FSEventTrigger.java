package ru.mpoplavkov.indexation.trigger;

import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;

import java.io.IOException;

public interface FSEventTrigger {

    /**
     * Processes the event, occurred in the file system.
     *
     * @param fileSystemEvent event that occurred in the system, where
     *                        this trigger is registered.
     */
    void onEvent(FileSystemEvent fileSystemEvent) throws IOException;

}
