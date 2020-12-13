package ru.mpoplavkov.indexation.listener;

import java.io.IOException;
import java.nio.file.Path;

public interface FSEventTrigger {

    /**
     * Processes the event, occurred in the file system.
     *
     * @param fileSystemEvent event that occurred in the system, where
     *                        this trigger is registered.
     */
    void onEvent(Path changedPath) throws IOException;

}
