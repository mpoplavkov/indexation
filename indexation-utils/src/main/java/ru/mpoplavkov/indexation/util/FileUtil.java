package ru.mpoplavkov.indexation.util;

import java.nio.file.Path;

public final class FileUtil {
    private FileUtil() {
    }

    public static String getCanonicalPath(Path path) {
        return toCanonicalPath(path).toString();
    }

    public static Path toCanonicalPath(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
