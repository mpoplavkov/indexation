package ru.mpoplavkov.indexation.model.fs;

import lombok.Data;

import java.nio.file.Path;

/**
 * Event that can occur in a file system.
 */
@Data
public class FileSystemEvent {
    public enum Kind {
        FILE_CREATE,
        FILE_UPDATE,
        FILE_DELETE,
        DIRECTORY_CREATE,
        DIRECTORY_DELETE
    }

    private final Kind kind;
    private final Path context;
}
