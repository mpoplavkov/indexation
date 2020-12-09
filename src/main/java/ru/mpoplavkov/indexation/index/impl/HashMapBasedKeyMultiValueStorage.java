package ru.mpoplavkov.indexation.index.impl;

import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HashMapBasedKeyMultiValueStorage<K, V> implements KeyMultiValueStorage<K, V> {

    private final Map<K, Set<V>> storage = new HashMap<>();

    @Override
    public void put(K key, V value) {
        Set<V> associatedSet = storage.computeIfAbsent(key, k -> new HashSet<>());
        associatedSet.add(value);
    }

    @Override
    public void delete(K key, V value) {
        Set<V> associatedSet = storage.get(key);
        if (associatedSet != null) {
            associatedSet.remove(value);
        }
    }

    @Override
    public Set<V> get(K key) {
        Set<V> resultSet = new HashSet<>();
        Set<V> setFromTheStorage = storage.get(key);
        if (setFromTheStorage != null) {
            resultSet.addAll(setFromTheStorage);
        }
        return resultSet;
    }
}
