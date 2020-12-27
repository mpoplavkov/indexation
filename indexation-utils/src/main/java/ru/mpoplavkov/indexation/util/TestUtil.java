package ru.mpoplavkov.indexation.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestUtil {

    private TestUtil() {
    }

    public static String getFileContentFromResources(String path) throws IOException {
        Path file = getFilePathFromResources(path);
        return getFileContentFromResources(file);
    }

    public static String getFileContentFromResources(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes);
    }

    public static Path getFilePathFromResources(String path) {
        ClassLoader classLoader = TestUtil.class.getClassLoader();
        return new File(classLoader.getResource(path).getFile()).toPath();
    }

}
