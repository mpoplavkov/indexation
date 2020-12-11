package ru.mpoplavkov.indexation.index.impl;

import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.VersionedValue;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final Map<V, Integer> valueVersions = new ConcurrentHashMap<>(); // TODO: deal with version overflow

    /**
     * Atomically associates given terms with the value in the storage.
     *
     * @param value given value.
     * @param terms given terms.
     */
    @Override
    public void index(V value, Iterable<Term> terms) {
        // TODO: think about the order
        Integer oldVersion = valueVersions.get(value);
        Integer newVersion = incVersion(oldVersion);
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
            return kmvStorage.get(exactTerm.getTerm()).stream()
                    .filter(this::versionIsActual)
                    .map(VersionedValue::getValue)
                    .collect(Collectors.toSet());
        }

        throw new UnsupportedOperationException(
                String.format("Query '%s' is not supported", query)
        );
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
}
