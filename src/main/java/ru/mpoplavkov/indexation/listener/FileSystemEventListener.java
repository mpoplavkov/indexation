package ru.mpoplavkov.indexation.listener;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public interface FileSystemEventListener extends Closeable {

    void listenLoop();

    boolean register(Path path) throws IOException;

    // TODO:
    //    void unregister(Path path);
}
