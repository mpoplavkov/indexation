package ru.mpoplavkov.indexation.service;

import ru.mpoplavkov.indexation.model.query.Query;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface FileSystemIndexService extends Closeable {

    /**
     * Searches data in the index.
     *
     * @param query search query.
     * @return matched values.
     */
    Set<Path> search(Query query);

    void addToIndex(Path path) throws IOException;

}
