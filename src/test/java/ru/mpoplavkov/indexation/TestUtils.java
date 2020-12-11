package ru.mpoplavkov.indexation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class TestUtils {

    private TestUtils() {
    }

    @SafeVarargs
    public static <T> List<T> createList(T... ts) {
        return Arrays.asList(ts);
    }

    @SafeVarargs
    public static <T> Set<T> createSet(T... ts) {
        return new HashSet<>(Arrays.asList(ts));
    }

    public static String getFileContentFromResources(String path) throws IOException {
        Path file = getFilePathFromResources(path);
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes);
    }

    public static Path getFilePathFromResources(String path) {
        ClassLoader classLoader = TestUtils.class.getClassLoader();
        return new File(classLoader.getResource(path).getFile()).toPath();
    }

}
