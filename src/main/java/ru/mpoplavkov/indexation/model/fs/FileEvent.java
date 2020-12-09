package ru.mpoplavkov.indexation.model.fs;

import lombok.Data;

import java.nio.file.Path;

/**
 * Event, associated with the concrete file in
 * th file system.
 */
@Data
public class FileEvent implements FileSystemEvent {

    public enum Kind {
        FILE_CREATE,
        FILE_UPDATE,
        FILE_DELETE
    }

    private final Kind kind;
    private final Path context;

}
