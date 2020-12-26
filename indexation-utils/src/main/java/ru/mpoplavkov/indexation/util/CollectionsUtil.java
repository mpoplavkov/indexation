package ru.mpoplavkov.indexation.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CollectionsUtil {
    private CollectionsUtil() {

    }

    @SafeVarargs
    public static <T> List<T> createList(T... ts) {
        return Arrays.asList(ts);
    }

    @SafeVarargs
    public static <T> Set<T> createSet(T... ts) {
        return new HashSet<>(Arrays.asList(ts));
    }
}
