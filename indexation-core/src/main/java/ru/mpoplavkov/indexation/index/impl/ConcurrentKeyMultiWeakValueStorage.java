package ru.mpoplavkov.indexation.index.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Implementation of {@link KeyMultiValueStorage} based on {@link ConcurrentHashMap}
 * and {@link Cache} with weak keys. All values stored in this storage could
 * potentially be garbage collected if they don't have any strong or soft references
 * to them.
 *
 * @param <K> type of the key.
 * @param <V> type of the value.
 */
@Log
class ConcurrentKeyMultiWeakValueStorage<K, V> implements KeyMultiValueStorage<K, V> {

    private final Map<K, Cache<V, K>> storage = new ConcurrentHashMap<>();

    /**
     * Scheduled executor service to perform cleanup operations on all the underlying
     * caches. {@link Cache} will be cleaned up automatically only by performing write
     * or read operations on this cache, which is not always the case in this storage.
     * Therefore, a scheduler is needed - to schedule cleanups manually.
     */
    private final ScheduledExecutorService cleanupExecutorService =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Creates the storage and schedules cleanup operation with the given delay.
     *
     * @param cleanUpDelay the delay between two full cleanups.
     * @param unit         the time unit of the cleanUpDelay parameter
     */
    public ConcurrentKeyMultiWeakValueStorage(long cleanUpDelay, TimeUnit unit) {
        cleanupExecutorService.scheduleWithFixedDelay(this::cleanUpAllCaches, cleanUpDelay, cleanUpDelay, unit);
    }

    public ConcurrentKeyMultiWeakValueStorage() {
        this(30, TimeUnit.SECONDS);
    }

    @Override
    public void put(K key, V value) {
        Cache<V, K> associatedCache = storage.computeIfAbsent(key, k -> newWeakCache());
        associatedCache.put(value, key);
    }

    @Override
    public void delete(K key, V value) {
        Cache<V, K> associatedCache = storage.get(key);
        if (associatedCache != null) {
            associatedCache.invalidate(value);
        }
    }

    @Override
    public Set<V> get(K key) {
        Set<V> resultSet = new HashSet<>();
        Cache<V, K> associatedCache = storage.get(key);
        if (associatedCache != null) {
            resultSet.addAll(associatedCache.asMap().keySet());
        }
        return resultSet;
    }

    @Override
    public void close() {
        cleanupExecutorService.shutdownNow();
    }

    /**
     * Creates cache, which could be used as a {@link Map},
     * with keys, wrapped in {@link java.lang.ref.WeakReference}.
     * <p>The resulting cache will use identity ({@code ==}) comparison
     * to determine equality of keys.
     * Returned cache registers a {@link RemovalListener} to remove
     * empty caches from the storage.
     */
    private Cache<V, K> newWeakCache() {
        RemovalListener<V, K> removalListener = notification ->
                storage.computeIfPresent(notification.getValue(), (key, cache) -> {
                    if (cache.asMap().isEmpty()) {
                        log.config(() ->
                                String.format("Removing the cache for key '%s' because of '%s'", key, notification.getCause())
                        );
                        return null;
                    } else {
                        return cache;
                    }
                });

        return CacheBuilder.newBuilder()
                .weakKeys()
                .removalListener(removalListener)
                .build();
    }

    private void cleanUpAllCaches() {
        try {
            storage.values().forEach(Cache::cleanUp);
        } catch (Exception e) {
            log.log(Level.SEVERE, e, () -> "Exception occurred during the full cleanup");
            // ignore
        }
    }
}
