package ru.mpoplavkov.indexation.filter;

import java.nio.file.Path;

public interface FileFilter {

    boolean filter(Path file);

}
