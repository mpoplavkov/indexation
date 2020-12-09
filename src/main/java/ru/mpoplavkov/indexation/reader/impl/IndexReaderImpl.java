package ru.mpoplavkov.indexation.reader.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.reader.IndexReader;

import java.util.Set;

@RequiredArgsConstructor
public class IndexReaderImpl<V> implements IndexReader<V> {
    private final TermIndex<V> index;

    @Override
    public Set<V> search(Query query) {
        return index.search(query);
    }
}
