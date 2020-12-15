package ru.mpoplavkov.indexation.index.impl;

import lombok.Data;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Term index based on the storage of values along with their versions.
 * Each update operation changes the value's version. The version
 * correspondence is checked when values are retrieved.
 * <p>For each update, the value is fully re-indexed. Values, along with
 * their versions, are stored wrapped in the {@link java.lang.ref.WeakReference}.
 * This allows to not cleanup the storage manually.
 *
 * @param <V> type of value to be stored in the index.
 */
@Log
public class VersionedTermIndex<V> implements TermIndex<V> {

    /**
     * The underlying storage. Stores values along with their versions.
     */
    private final KeyMultiValueStorage<Term, VersionedValue<V>> kmvStorage =
            new ConcurrentKeyMultiWeakValueStorage<>();

    /**
     * Association between values and their actual versions in the storage.
     * During the search, only values with actual versions are retrieved
     * from the storage.
     * <br>
     * If the value's version is negative, it means that the value has been
     * deleted from the index (but possibly not from the storage).
     */
    // TODO: deal with the version overflow
    private final Map<V, VersionedValue<V>> valueVersions = new ConcurrentHashMap<>();

    /**
     * Atomically associates given terms with the value in the storage.
     * The indexed value will only become visible only after a new version
     * of that value is inserted into the
     * {@link VersionedTermIndex#valueVersions}, which is the last
     * operation in this method.
     * <br>
     * Only one parallel update for the concrete value is allowed. If two
     * or more threads will index the same value, then the behaviour is
     * not determined.
     *
     * @param value given value.
     * @param terms given terms.
     */
    @Override
    public void index(V value, Iterable<Term> terms) {
        VersionedValue<V> oldVersionedValue = valueVersions.get(value);
        VersionedValue<V> newVersionedValue;
        if (oldVersionedValue == null) {
            newVersionedValue = VersionedValue.withInitialVersion(value);
        } else {
            newVersionedValue = oldVersionedValue.withIncrementedVersion();
        }

        for (Term term : terms) {
            kmvStorage.put(term, newVersionedValue);
        }
        valueVersions.put(value, newVersionedValue);
        log.config(() -> String.format("Indexed '%s'", newVersionedValue));
    }

    /**
     * Atomically deletes all occurrences of the given value from the index.
     * Just negates the version associated with the value in index, so
     * that all the further lookups will not retrieve that value because
     * of the version mismatch.
     *
     * @param value value to delete from the index.
     */
    @Override
    public void delete(V value) {
        valueVersions.computeIfPresent(value, (v, versioned) -> versioned.withNegativeVersion());
        log.config(() -> String.format("Deleted '%s'", value));
    }

    /**
     * Retrieves all values from the index that match the specified query.
     * Since multiple versions of the same value are stored in the storage,
     * filtering is applied to the result of the storage query.
     * <br><br>
     * It would be wrong to filter result of the storage query using
     * {@link VersionedTermIndex#valueVersions}, because between the
     * retrieving the result from the storage and obtaining versions from
     * {@link VersionedTermIndex#valueVersions}, this map potentially
     * could be changed.
     * In this case, the following order of events is possible:
     * <ol>
     *     <li>storage returns <i>value1</i> along with the <i>version1</i>
     *     as a result of the <i>query</i>;</li>
     *     <li><i>value1</i> is reindexed, so that it still falls under the
     *     conditions of the <i>query</i>;</li>
     *     <li>{@link VersionedTermIndex#valueVersions} for the given
     *     <i>value1</i> contains already the next version - <i>version2</i>;
     *     </li>
     *     <li>since the result from the first step contains only non actual
     *     version of the <i>value1</i> (<i>version1</i>, when version
     *     <i>version2</i> is actual), <i>value1</i> would not be returned as
     *     a result of the select method, even though both versions of this
     *     value satisfy the <i>query</i>.</li>
     * </ol>
     * Therefore this is necessary to obtain a snapshot of actual value versions
     * before executing a request to the storage.
     * <br><br>
     * Current implementation executes two identical requests to the storage.
     * The first request is for identifying a set of values, that match to
     * the given query regardless of their version. Versions of these values are
     * then taken from the {@link VersionedTermIndex#valueVersions}. That
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

            Set<VersionedValue<V>> firstQueryResult = kmvStorage.get(termToSearch);

            Set<VersionedValue<V>> versionsSnapshot =
                    firstQueryResult
                            .stream()
                            .map(VersionedValue::getValue)
                            .distinct()
                            .flatMap(this::withActualVersion)
                            .collect(Collectors.toSet());

            Set<VersionedValue<V>> secondQueryResult = kmvStorage.get(termToSearch);

            return secondQueryResult
                    .stream()
                    .filter(versionsSnapshot::contains)
                    .map(VersionedValue::getValue)
                    .collect(Collectors.toSet());
        }

        throw new UnsupportedOperationException(
                String.format("Query '%s' is not supported", query)
        );
    }

    private Stream<VersionedValue<V>> withActualVersion(V value) {
        VersionedValue<V> actualVersionedValue = valueVersions.get(value);
        if (actualVersionedValue == null) {
            return Stream.empty();
        } else {
            return Stream.of(actualVersionedValue);
        }
    }

    @Data
    private static class VersionedValue<V> {
        private static final long INITIAL_VERSION = 1;

        private final V value;
        private final long version;

        static <T> VersionedValue<T> withInitialVersion(T value) {
            return new VersionedValue<>(value, INITIAL_VERSION);
        }

        VersionedValue<V> withIncrementedVersion() {
            return new VersionedValue<>(value, incVersion(version));
        }

        VersionedValue<V> withNegativeVersion() {
            return new VersionedValue<>(value, -version);
        }

        private long incVersion(long oldVersion) {
            // there could be a negative version if the value was deleted
            long absOldVersion = Math.abs(oldVersion);
            return ++absOldVersion;
        }
    }
}
