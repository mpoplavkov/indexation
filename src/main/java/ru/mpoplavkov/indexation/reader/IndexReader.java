package ru.mpoplavkov.indexation.reader;

import ru.mpoplavkov.indexation.model.query.Query;

import java.util.Set;

public interface IndexReader<V> {

    /**
     * Searches data in the index.
     *
     * @param query search query.
     * @return matched values.
     */
    Set<V> search(Query query);

}
