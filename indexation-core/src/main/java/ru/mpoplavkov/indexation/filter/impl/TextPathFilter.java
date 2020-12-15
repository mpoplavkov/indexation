package ru.mpoplavkov.indexation.filter.impl;

import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Checks if the path is a file with text content type or a directory.
 */
@Log
public class TextPathFilter implements PathFilter {

    private static final String TEXT_PLAIN = "text/plain";

    @Override
    public boolean filter(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.isDirectory(path) || TEXT_PLAIN.equals(Files.probeContentType(path));
        } catch (IOException e) {
            log.log(Level.SEVERE, e, () -> "Exception while filtering");
            return false;
        }
    }
}
