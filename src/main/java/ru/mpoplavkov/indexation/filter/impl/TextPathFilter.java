package ru.mpoplavkov.indexation.filter.impl;

import ru.mpoplavkov.indexation.filter.PathFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks if the path is a file with text content type.
 */
public class TextPathFilter implements PathFilter {

    private static final String TEXT_PLAIN = "text/plain";

    @Override
    public boolean filter(Path path) {
        try {
            return (!Files.isDirectory(path)) && TEXT_PLAIN.equals(Files.probeContentType(path));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
