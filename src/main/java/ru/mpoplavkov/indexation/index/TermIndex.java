package ru.mpoplavkov.indexation.index;

import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.Set;

/**
 * Term based index of values.
 *
 * @param <V> type of value to be stored in the index.
 */
public interface TermIndex<V> {

    /**
     * Appends all given terms, associated with the given value
     * to the index.
     * If this value was already indexed, than this works like
     * reindex.
     *
     * @param value given value.
     * @param terms given terms.
     */
    void index(V value, Iterable<Term> terms);

    /**
     * Retrieves all values from the index that match the specified query.
     *
     * @param query given query.
     * @return matched values.
     */
    Set<V> search(Query query);

    /**
     * Deletes all occurrences of the given value from the index.
     *
     * @param value value to delete from the index.
     */
    void delete(V value);
}
