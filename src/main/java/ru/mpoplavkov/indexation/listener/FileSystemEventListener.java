package ru.mpoplavkov.indexation.listener;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Listener of file system events.
 *
 * <p>Allows to register files and directories to be listened.
 */
public interface FileSystemEventListener extends Closeable {

    /**
     * Starts an infinite loop of listening file system for events
     * ans processing them. Loop will be cancelled when the listener
     * is closed.
     */
    void listenLoop();

    /**
     * Registers path to be listened. If the given path is a directory,
     * all of its children files will be listened.
     *
     * <p>Returns a flag indicating whether the path was accepted or not.
     * Path could be rejected if it doesn't pass through some filters.
     * For example, if the listener is designed to track only text files,
     * the binaries will be rejected.
     *
     * <p>If the given path was already registered, than nothing happens.
     * <i>true</i> will be returned in this case.
     *
     * @param path the path to register.
     * @return true iff the path was accepted, false otherwise.
     * @throws IOException if an I/O error occurs.
     */
    void register(Path path) throws IOException;

    // TODO:
    //    void unregister(Path path);
}
