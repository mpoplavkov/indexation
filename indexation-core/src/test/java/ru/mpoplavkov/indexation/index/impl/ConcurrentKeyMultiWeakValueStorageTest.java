package ru.mpoplavkov.indexation.index.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.mpoplavkov.indexation.util.CollectionsUtil.createSet;

class ConcurrentKeyMultiWeakValueStorageTest {

    KeyMultiValueStorage<String, Integer> storage;

    String key1 = "key1";
    String key2 = "key2";

    Integer value1 = 1;
    Integer value2 = 2;

    @BeforeEach
    void init() {
        storage = new ConcurrentKeyMultiWeakValueStorage<>();
    }

    @Test
    public void shouldPutAndGetValue() {
        storage.put(key1, value1);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet(value1);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldPutSeveralValuesForTheSameKey() {
        storage.put(key1, value1);
        storage.put(key1, value2);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet(value1, value2);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldPutSeveralKeys() {
        storage.put(key1, value1);
        storage.put(key2, value2);

        Set<Integer> actual1 = storage.get(key1);
        Set<Integer> expected1 = createSet(value1);


        Set<Integer> actual2 = storage.get(key2);
        Set<Integer> expected2 = createSet(value2);

        Assertions.assertAll(
                () -> assertEquals(expected1, actual1),
                () -> assertEquals(expected2, actual2)
        );
    }

    @Test
    public void shouldPutTwoSameValuesForTheSameKey() {
        storage.put(key1, value1);
        storage.put(key1, value1);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet(value1);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldDeleteKeyValuePair() {
        storage.put(key1, value1);
        storage.put(key1, value2);
        storage.delete(key1, value2);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet(value1);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldAddTheSameValueAfterDeletion() {
        storage.put(key1, value1);
        storage.put(key1, value2);
        storage.delete(key1, value2);
        storage.put(key1, value2);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet(value1, value2);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldRetrieveAnEmptySetForMissedKey() {
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet();
        assertEquals(expected, actual);
    }

    @Test
    public void shouldRetrieveAnEmptySetForDeletedKey() {
        storage.put(key1, value1);
        storage.delete(key1, value1);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet();
        assertEquals(expected, actual);
    }

    @Test
    public void shouldDoesNothingForDeletionOfAMissedKey() {
        storage.delete(key1, value1);
        Set<Integer> actual = storage.get(key1);
        Set<Integer> expected = createSet();
        assertEquals(expected, actual);
    }

}
