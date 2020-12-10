package ru.mpoplavkov.indexation.trigger;

import java.io.Closeable;
import java.io.IOException;

public interface EventListener<T> extends Closeable {

    void listenLoop();

    void register(T eventSource) throws IOException;

    // TODO:
    //    void unregister(T eventSource);
}
