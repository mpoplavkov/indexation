package ru.mpoplavkov.indexation.index;

import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.Set;

public interface TextIndex<V> {

    void index(Term term, V value);

    Set<V> search(Query query);

    void deleteAllValueOccurrences(V value);
}
