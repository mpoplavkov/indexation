package ru.mpoplavkov.indexation.index;

import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.Set;

public interface TermIndex<V> {

    /**
     * Append the given term, associated with the given value
     * to the index.
     *
     * @param term  given term.
     * @param value given value.
     */
    void index(Term term, V value);

    /**
     * Append all given terms, associated with the given value
     * to the index.
     *
     * @param terms given terms.
     * @param value given value.
     */
    void index(Iterable<Term> terms, V value);

    /**
     * Retrieves all values from the index that match the specified query.
     *
     * @param query given query.
     * @return matched values.
     */
    Set<V> search(Query query);

    /**
     * Delete all occurrences of the given value from the index.
     *
     * @param value value to delete from the index.
     */
    void deleteAllValueOccurrences(V value);
}
