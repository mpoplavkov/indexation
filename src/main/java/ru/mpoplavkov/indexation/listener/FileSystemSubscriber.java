package ru.mpoplavkov.indexation.listener;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Subscriber to changes in the file system.
 *
 * <p>Allows to subscribe to files and directories.
 */
public interface FileSystemSubscriber extends Closeable {

    /**
     * Subscribes to the path for listening.
     * <p>If the path was already subscribed to, than nothing happens.
     *
     * @param path the path to subscribe to.
     * @throws IOException if an I/O error occurs.
     */
    void subscribe(Path path) throws IOException;

    // TODO: implement
    // void unsubscribe(Path path);

    /**
     * Starts threads for listening the file system for events
     * ans processing them. Threads will be cancelled when the
     * subscriber is closed.
     */
    void startToListenForEvents(int listenerParallelism);
}
