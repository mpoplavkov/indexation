package ru.mpoplavkov.indexation.filter;

import java.nio.file.Path;

/**
 * Filtration logic for paths.
 */
public interface PathFilter {

    /**
     * Checks if file should be accepted.
     * @param path the path to check.
     * @return true if the path passed the filter, false otherwise.
     */
    boolean filter(Path path);

}
