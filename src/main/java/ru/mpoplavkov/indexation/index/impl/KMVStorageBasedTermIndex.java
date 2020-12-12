package ru.mpoplavkov.indexation.index.impl;

import lombok.Data;
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

// TODO: add the second implementation
public class KMVStorageBasedTermIndex<V> implements TermIndex<V> {

    /**
     * The underlying storage. Stores values along with their versions.
     */
    private final KeyMultiValueStorage<Term, VersionedValue<V>> kmvStorage =
            new ConcurrentHashMapBasedKeyMultiValueStorage<>();

    /**
     * Association between values and their actual versions in the storage.
     * During the search, only values with actual versions are retrieved
     * from the storage.
     * If the value's version is negative, it means that the value has been
     * deleted from the index (but possibly not from the storage).
     */
    private final Map<V, Long> valueVersions = new ConcurrentHashMap<>(); // TODO: deal with version overflow

    /**
     * Atomically associates given terms with the value in the storage.
     *
     * @param value given value.
     * @param terms given terms.
     */
    @Override
    public void index(V value, Iterable<Term> terms) {
        Long oldVersion = valueVersions.get(value);
        Long newVersion = incVersion(oldVersion);
        VersionedValue<V> versionedValue = new VersionedValue<>(value, newVersion);
        for (Term term : terms) {
            kmvStorage.put(term, versionedValue);
        }
        valueVersions.put(value, newVersion);
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
        valueVersions.computeIfPresent(value, (v, oldVersion) -> negateVersion(oldVersion));
    }

    @Override
    public Set<V> search(Query query) {
        if (query instanceof ExactTerm) {
            ExactTerm exactTerm = (ExactTerm) query;
            Term termToSearch = exactTerm.getTerm();

            Set<VersionedValue<V>> firstQueryResult = kmvStorage.get(termToSearch);

            Set<VersionedValue<V>> interestingVersionedValues =
                    firstQueryResult
                            .stream()
                            .map(VersionedValue::getValue)
                            .flatMap(this::withActualVersion)
                            .collect(Collectors.toSet());

            Set<VersionedValue<V>> secondQueryResult = kmvStorage.get(termToSearch);

            return secondQueryResult
                    .stream()
                    .filter(interestingVersionedValues::contains)
                    .map(VersionedValue::getValue)
                    .collect(Collectors.toSet());
        }

        throw new UnsupportedOperationException(
                String.format("Query '%s' is not supported", query)
        );
    }

    private Long incVersion(Long oldVersion) {
        if (oldVersion == null) {
            return VersionedValue.INITIAL_VERSION;
        } else {
            // there could be a negative version if the file was deleted
            long absOldVersion = Math.abs(oldVersion);
            return ++absOldVersion;
        }
    }

    private Long negateVersion(Long oldVersion) {
        if (oldVersion == null) {
            return null;
        } else {
            return -Math.abs(oldVersion);
        }
    }

    private Stream<VersionedValue<V>> withActualVersion(V value) {
        Long actualVersion = valueVersions.get(value);
        if (actualVersion == null) {
            return Stream.empty();
        } else {
            return Stream.of(new VersionedValue<>(value, actualVersion));
        }
    }

    @Data
    private static class VersionedValue<V> {
        public static final long INITIAL_VERSION = 1;

        private final V value;
        private final long version;
    }
}
