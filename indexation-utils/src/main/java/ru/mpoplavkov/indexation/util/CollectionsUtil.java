package ru.mpoplavkov.indexation.util;

import java.util.*;
import java.util.stream.Collectors;

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

    public static <T> String makeSortedString(Collection<T> collection, String separator) {
        return collection.stream()
                .sorted()
                .map(Objects::toString)
                .collect(Collectors.joining(separator));
    }

    public static <T> String makeSortedString(Collection<T> collection) {
        return makeSortedString(collection, "");
    }
}
