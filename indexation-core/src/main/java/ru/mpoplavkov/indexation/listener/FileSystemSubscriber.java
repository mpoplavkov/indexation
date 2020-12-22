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

    /**
     * Deletes the path and its contents from the index and stops to
     * listen for its changes. Does nothing if the path was not subscribed to.
     * This path will be subscribed again in case of its recreation in the
     * file system and if one of its parent directories is subscribed to
     * changes.
     *
     * @param path the path to unsubscribe from.
     * @throws IOException if an I/O error occurs.
     */
    void unsubscribe(Path path) throws IOException;

    /**
     * Starts threads for listening the file system for events
     * ans processing them. Threads will be cancelled when the
     * subscriber is closed.
     */
    void startToListenForEvents(int listenerParallelism);
}
