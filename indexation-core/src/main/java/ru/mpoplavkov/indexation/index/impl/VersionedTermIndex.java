package ru.mpoplavkov.indexation.index.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Term index based on the storage of values wrapped into
 * {@link VersionedTermIndex.WrappedValue}. Each update operation forces
 * the creation of a new {@link VersionedTermIndex.WrappedValue} and full
 * reindex of all terms along with the newly created object. The index
 * remembers the last object, associated with a concrete value and its
 * correspondence is checked when retrieving data from the storage. Check
 * is performed via reference equality (==).
 * Each {@link VersionedTermIndex.WrappedValue} instance serves as a random
 * version for a specific value, ensuring that no two existing versions are
 * the same.
 *
 * <p>Values are stored wrapped in the {@link java.lang.ref.WeakReference}.
 * This allows to not cleanup the storage manually.
 *
 * @param <V> type of value to be stored in the index.
 */
@Log
public class VersionedTermIndex<V> implements TermIndex<V> {

    /**
     * The underlying storage.
     */
    private final KeyMultiValueStorage<Term, WrappedValue<V>> kmvStorage =
            new ConcurrentKeyMultiWeakValueStorage<>();

    /**
     * Association between values and their actual versions in the storage.
     * During the search, only values with actual versions are retrieved
     * from the storage.
     */
    private final Map<V, WrappedValue<V>> actualValues = new ConcurrentHashMap<>();

    /**
     * Atomically associates given terms with the value in the storage.
     * The indexed value will only become visible only after a new instance
     * of the {@link VersionedTermIndex.WrappedValue} is inserted into the
     * {@link VersionedTermIndex#actualValues}, which is the last
     * operation in this method.
     *
     * @param value given value.
     * @param terms given terms.
     */
    @Override
    public void index(V value, Iterable<Term> terms) {
        WrappedValue<V> newWrappedValue = new WrappedValue<>(value);

        for (Term term : terms) {
            kmvStorage.put(term, newWrappedValue);
        }
        actualValues.put(value, newWrappedValue);
        log.config(() -> String.format("Indexed '%s'", value));
    }

    /**
     * Atomically deletes all occurrences of the given value from the index.
     * Just removes the given value from {@link VersionedTermIndex#actualValues},
     * so that all the further lookups will not retrieve that value because
     * of the version mismatch.
     *
     * @param value value to delete from the index.
     */
    @Override
    public void delete(V value) {
        actualValues.remove(value);
        log.config(() -> String.format("Deleted '%s'", value));
    }

    /**
     * Retrieves all values from the index that match the specified query.
     * Since multiple versions of the same value are stored in the storage,
     * filtering is applied to the result of the storage query.
     * <br><br>
     * It would be wrong to filter result of the storage query using
     * {@link VersionedTermIndex#actualValues}, because between the
     * retrieving the result from the storage and obtaining versions from
     * {@link VersionedTermIndex#actualValues}, this map potentially
     * could be changed.
     * In this case, the following order of events is possible:
     * <ol>
     *     <li>storage returns <i>wrappedValue1</i> associated with the <i>value1</i>
     *     as a result of the <i>query</i>;</li>
     *     <li><i>value1</i> is reindexed, so that it still falls under the
     *     conditions of the <i>query</i>;</li>
     *     <li>{@link VersionedTermIndex#actualValues} for the given
     *     <i>value1</i> contains already the next version - <i>wrappedValue2</i>;
     *     </li>
     *     <li>since the result from the first step contains only non actual
     *     version of the <i>value1</i> (<i>wrappedValue1</i>, when
     *     <i>wrappedValue2</i> is actual), <i>value1</i> would not be returned as
     *     a result of the select method, even though both versions of this
     *     value satisfy the <i>query</i>.</li>
     * </ol>
     * Therefore this is necessary to obtain a snapshot of actual value versions
     * before executing a request to the storage.
     * <br><br>
     * Current implementation executes two identical requests to the storage.
     * The first request is for identifying a set of values, that match to
     * the given query regardless of their version. Versions of these values are
     * then taken from the {@link VersionedTermIndex#actualValues}. That
     * is how the snapshot of versions is made. This snapshot is used then to
     * filter the result of the second request.
     *
     * @param query given query.
     * @return matched values.
     */
    @Override
    public Set<V> search(Query query) {
        if (query instanceof ExactTerm) {
            ExactTerm exactTerm = (ExactTerm) query;
            Term termToSearch = exactTerm.getTerm();

            Set<WrappedValue<V>> firstQueryResult = kmvStorage.get(termToSearch);

            Set<WrappedValue<V>> actualValuesSnapshot =
                    firstQueryResult
                            .stream()
                            .map(WrappedValue::getValue)
                            .distinct()
                            .flatMap(this::withActualValue)
                            .collect(Collectors.toSet());

            Set<WrappedValue<V>> secondQueryResult = kmvStorage.get(termToSearch);

            return secondQueryResult
                    .stream()
                    .filter(actualValuesSnapshot::contains)
                    .map(WrappedValue::getValue)
                    .collect(Collectors.toSet());
        }

        throw new UnsupportedOperationException(
                String.format("Query '%s' is not supported", query)
        );
    }

    private Stream<WrappedValue<V>> withActualValue(V value) {
        WrappedValue<V> actualValue = actualValues.get(value);
        if (actualValue == null) {
            return Stream.empty();
        } else {
            return Stream.of(actualValue);
        }
    }

    @Override
    public void close() throws IOException {
        kmvStorage.close();
    }

    // TODO: discuss if this idea is clear. Reimplement, if not
    /**
     * A wrapper for the value. The main purpose of this class is to create
     * a unique object each time when the value is reindexed, so that this
     * value will be associated with this object.
     *
     * @param <V> type of the stored value.
     */
    @RequiredArgsConstructor
    @Getter
    private static class WrappedValue<V> {
        private final V value;

        /**
         * Reference equality check. Since each new version of the value is
         * associated with a new instance of a {@link WrappedValue}, the equality
         * check of {@link WrappedValue} must be performed via comparison of the
         * references.
         */
        @Override
        public boolean equals(Object another) {
            return this == another;
        }
    }
}
