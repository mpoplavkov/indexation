package ru.mpoplavkov.indexation.util;

import lombok.SneakyThrows;

import java.nio.file.Path;

public final class FileUtil {
    private FileUtil() {
    }

    @SneakyThrows
    public static String getCanonicalPath(Path path) {
        return path.toFile().getCanonicalPath();
    }
}
