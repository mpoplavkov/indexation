package ru.mpoplavkov.indexation.index.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.VersionedValue;
import ru.mpoplavkov.indexation.model.query.*;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: add the second implementation
@RequiredArgsConstructor
public class KMVStorageBasedTermIndex<V> implements TermIndex<V> {

    /**
     * The underlying storage. Stores values along with their versions.
     */
    private final KeyMultiValueStorage<Term, VersionedValue<V>> kmvStorage;

    /**
     * Association between values and their actual versions in the storage.
     * During the search, only values with actual versions are retrieved
     * from the storage.
     * If the value's version is negative, it means that the value has been
     * deleted from the index (but possibly not from the storage).
     */
    private final Map<V, Integer> valueVersions = new HashMap<>(); // TODO: deal with version overflow

    /**
     * Set of all values, stored in the index.
     */
    private final Set<V> allValues = new HashSet<>();

    @Override
    public void index(V value, Iterable<Term> terms) {
        // TODO: think about the order
        Integer version = valueVersions.compute(value, (v, oldVersion) -> incVersion(oldVersion));
        allValues.add(value);
        VersionedValue<V> versionedValue = new VersionedValue<>(value, version);
        for (Term term : terms) {
            kmvStorage.put(term, versionedValue);
        }
    }

    /**
     * Deletes all occurrences of the given value from the index.
     * Just negates the version associated with the value in index, so
     * that all the further lookups will not retrieve that value because
     * of the version mismatch.
     *
     * @param value value to delete from the index.
     */
    @Override
    public void delete(V value) {
        allValues.remove(value); // TODO: think about the order
        valueVersions.computeIfPresent(value, (v, oldVersion) -> negateVersion(oldVersion));
    }

    @Override
    public Set<V> search(Query query) {
        return searchInternal(query);
    }

    private Integer incVersion(Integer oldVersion) {
        if (oldVersion == null) {
            return VersionedValue.INITIAL_VERSION;
        } else {
            int absOldVersion = Math.abs(oldVersion);
            return ++absOldVersion;
        }
    }

    private Integer negateVersion(Integer oldVersion) {
        if (oldVersion == null) {
            return null;
        } else {
            return -Math.abs(oldVersion);
        }
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
