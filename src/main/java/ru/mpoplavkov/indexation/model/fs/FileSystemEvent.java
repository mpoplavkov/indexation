package ru.mpoplavkov.indexation.model.fs;

import lombok.Data;

import java.nio.file.Path;

/**
 * Event that can occur in a file system.
 */
@Data
public class FileSystemEvent {
    public enum Kind {
        ENTRY_CREATE,
        ENTRY_MODIFY,
        ENTRY_DELETE
    }

    private final Kind kind;
    private final Path entry;
}
