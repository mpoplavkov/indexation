package ru.mpoplavkov.indexation.index.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.util.ExecutorsUtil;

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
public class ConcurrentKeyMultiWeakValueStorage<K, V> implements KeyMultiValueStorage<K, V> {

    private final static int DEFAULT_CAPACITY = 16;
    private final static int DEFAULT_CLEAN_UP_DELAY_SECONDS = 30;

    private final Map<K, Cache<V, K>> storage;

    /**
     * Scheduled executor service to perform cleanup operations on all the underlying
     * caches. {@link Cache} will be cleaned up automatically only by performing write
     * or read operations on this cache, which is not always the case in this storage.
     * Therefore, a scheduler is needed - to schedule cleanups manually.
     */
    private final ScheduledExecutorService cleanupExecutorService;

    /**
     * Creates the storage and schedules cleanup operation with the given delay.
     *
     * @param cleanupExecutorService executor service to schedule cleanups on.
     * @param cleanUpDelay           the delay between two full cleanups.
     * @param unit                   the time unit of the cleanUpDelay parameter
     * @param initialCapacity        initial capacity of the storage.
     */
    public ConcurrentKeyMultiWeakValueStorage(ScheduledExecutorService cleanupExecutorService,
                                              long cleanUpDelay,
                                              TimeUnit unit,
                                              int initialCapacity) {
        this.storage = new ConcurrentHashMap<>(initialCapacity);
        this.cleanupExecutorService = cleanupExecutorService;
        cleanupExecutorService.scheduleWithFixedDelay(this::cleanUpAllCaches, cleanUpDelay, cleanUpDelay, unit);
    }

    public ConcurrentKeyMultiWeakValueStorage(ScheduledExecutorService cleanupExecutorService) {
        this(cleanupExecutorService, DEFAULT_CLEAN_UP_DELAY_SECONDS, TimeUnit.SECONDS, DEFAULT_CAPACITY);
    }

    public ConcurrentKeyMultiWeakValueStorage(long cleanUpDelay, TimeUnit unit, int initialCapacity) {
        this(Executors.newSingleThreadScheduledExecutor(new ExecutorsUtil.DaemonThreadFactory("storage-cleaner")),
                cleanUpDelay,
                unit,
                initialCapacity
        );
    }

    public ConcurrentKeyMultiWeakValueStorage(int initialCapacity) {
        this(DEFAULT_CLEAN_UP_DELAY_SECONDS, TimeUnit.SECONDS, initialCapacity);
    }

    public ConcurrentKeyMultiWeakValueStorage() {
        this(DEFAULT_CAPACITY);
    }

    @Override
    public void put(K key, V value) {
        storage.compute(key, (k, cache) -> {
            Cache<V, K> result;
            if (cache == null) {
                result = newWeakCache();
            } else {
                result = cache;
            }
            result.put(value, key);
            return result;
        });
    }

    @Override
    public void delete(K key, V value) {
        storage.computeIfPresent(key, (k, cache) -> {
                    cache.invalidate(value);
                    return cache;
                }
        );
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
