package ru.mpoplavkov.indexation.index.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link KeyMultiValueStorage} based on {@link ConcurrentHashMap}
 * and {@link Cache} with weak keys. All values stored in this storage could
 * potentially be garbage collected if they don't have no strong or soft references
 * on them.
 *
 * @param <K> type of the key.
 * @param <V> type of the value.
 */
public class ConcurrentKeyMultiWeakValueStorage<K, V> implements KeyMultiValueStorage<K, V> {

    private final Map<K, Cache<V, Boolean>> storage = new ConcurrentHashMap<>();

    @Override
    public void put(K key, V value) {
        Cache<V, Boolean> associatedCache = storage.computeIfAbsent(key, k -> newWeakCache());
        associatedCache.put(value, true);
    }

    @Override
    public void delete(K key, V value) {
        Cache<V, Boolean> associatedCache = storage.get(key);
        if (associatedCache != null) {
            associatedCache.invalidate(value);
        }
    }

    @Override
    public Set<V> get(K key) {
        Set<V> resultSet = new HashSet<>();
        Cache<V, Boolean> associatedCache = storage.get(key);
        if (associatedCache != null) {
            resultSet.addAll(associatedCache.asMap().keySet());
        }
        return resultSet;
    }

    /**
     * Creates cache with, which could be used as a {@link Map},
     * with keys, wrapped in {@link java.lang.ref.WeakReference}.
     * <p>The resulting cache will use identity ({@code ==}) comparison
     * to determine equality of keys.
     */
    private Cache<V, Boolean> newWeakCache() {
        return CacheBuilder.newBuilder().weakKeys().build();
    }
}
