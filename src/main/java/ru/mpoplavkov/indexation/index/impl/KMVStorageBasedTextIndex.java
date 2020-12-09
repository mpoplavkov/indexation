package ru.mpoplavkov.indexation.index.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.index.TextIndex;
import ru.mpoplavkov.indexation.model.VersionedValue;
import ru.mpoplavkov.indexation.model.query.*;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class KMVStorageBasedTextIndex<V> implements TextIndex<V> {

    private final KeyMultiValueStorage<Term, VersionedValue<V>> kmvStorage;
    private final Map<V, Integer> valueVersions = new HashMap<>();
    private final Set<V> allValues = new HashSet<>();

    @Override
    public void index(Term term, V value) {
        Integer version = valueVersions.computeIfAbsent(value, v -> 0);
        allValues.add(value); // TODO: think about the order
        kmvStorage.put(term, new VersionedValue<>(value, version));
    }

    @Override
    public void deleteAllValueOccurrences(V value) {
        allValues.remove(value); // TODO: think about the order
        valueVersions.computeIfPresent(value, (k, v) -> v++);
    }

    @Override
    public Set<V> search(Query query) {
        return searchInternal(query);
    }

    private boolean versionIsActual(VersionedValue<V> versionedValue) {
        Integer actualVersion = valueVersions.get(versionedValue.getValue());
        if (actualVersion == null) {
            return false;
        }
        return versionedValue.getVersion() == actualVersion;
    }

    // TODO: get rid of recursion
    private Set<V> searchInternal(Query query) {
        if (query instanceof ExactTerm) {
            ExactTerm exactTerm = (ExactTerm) query;
            return kmvStorage.get(exactTerm.getTerm()).stream()
                    .filter(this::versionIsActual)
                    .map(VersionedValue::getValue)
                    .collect(Collectors.toSet());
        }
        if (query instanceof QueryWithUnaryOperator) {
            QueryWithUnaryOperator queryWithUnaryOperator = (QueryWithUnaryOperator) query;
            Set<V> queryResult = searchInternal(queryWithUnaryOperator.getQuery());
            UnaryOperator operator = queryWithUnaryOperator.getOperator();
            switch (operator) {
                case NOT:
                    Set<V> values = new HashSet<>(allValues);
                    values.removeAll(queryResult);
                    return values;
                default:
                    throw new UnsupportedOperationException(
                            String.format("Unary operator '%s' is not supported", operator)
                    );
            }
        }
        if (query instanceof QueryWithBinaryOperator) {
            QueryWithBinaryOperator queryWithBinaryOperator = (QueryWithBinaryOperator) query;
            Set<V> leftQueryResult = searchInternal(queryWithBinaryOperator.getLeft());
            Set<V> rightQueryResult = searchInternal(queryWithBinaryOperator.getRight());
            BinaryOperator operator = queryWithBinaryOperator.getOperator();
            switch (operator) {
                case AND:
                    leftQueryResult.retainAll(rightQueryResult);
                    return leftQueryResult;
                case OR:
                    leftQueryResult.addAll(rightQueryResult);
                    return leftQueryResult;
                default:
                    throw new UnsupportedOperationException(
                            String.format("Binary operator '%s' is not supported", operator)
                    );
            }
        }

        throw new UnsupportedOperationException(
                String.format("Query '%s' is not supported", query)
        );
    }
}
