package ru.mpoplavkov.indexation.filter.impl;

import ru.mpoplavkov.indexation.filter.FileFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextFileFilter implements FileFilter {

    private static final String TEXT_PLAIN = "text/plain";

    @Override
    public boolean filter(Path file) {
        try {
            return TEXT_PLAIN.equals(Files.probeContentType(file));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
