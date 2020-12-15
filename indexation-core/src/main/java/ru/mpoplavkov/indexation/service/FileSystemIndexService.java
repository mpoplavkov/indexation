package ru.mpoplavkov.indexation.service;

import ru.mpoplavkov.indexation.model.query.Query;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Service to interact with the index.
 */
public interface FileSystemIndexService extends Closeable {

    /**
     * Searches data in the index.
     *
     * @param query search query.
     * @return matched values.
     */
    Set<Path> search(Query query);

    /**
     * Adds the given path to the index. Starts to listen to it's
     * changes and updates the index accordingly.
     *
     * @param path the path to add to the index.
     * @throws IOException if an I/O error occurs.
     */
    void addToIndex(Path path) throws IOException;

}
