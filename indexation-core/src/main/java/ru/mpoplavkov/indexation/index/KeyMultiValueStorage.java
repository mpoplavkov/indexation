package ru.mpoplavkov.indexation.index;

import java.io.Closeable;
import java.util.Set;

/**
 * Key-value storage, where each key is associated with a
 * set of values.
 * Values are unique within one key.
 *
 * @param <K> type of the key.
 * @param <V> type of the value.
 */
public interface KeyMultiValueStorage<K, V> extends Closeable {

    /**
     * Adds a value to the set, associated with the given key.
     * Does nothing if this value was already associated with the key.
     *
     * @param key   given key.
     * @param value value to add to the set.
     */
    void put(K key, V value);

    /**
     * Deletes value from the set, associated with the given key.
     * Does nothing if there are no such value for the key.
     *
     * @param key   given key.
     * @param value value to remove from the set.
     */
    void delete(K key, V value);

    /**
     * Retrieves the set of values, associated with the give key.
     *
     * @param key give key
     * @return set of values for the key.
     */
    Set<V> get(K key);

}
