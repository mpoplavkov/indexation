package ru.mpoplavkov.indexation.index.impl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Term index for path storage with check for their existence.
 */
public class VersionedTermPathIndex extends VersionedTermIndex<Path> {
    @Override
    protected boolean valueIsActual(Path path) {
        return Files.exists(path);
    }
}
