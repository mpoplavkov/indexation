package ru.mpoplavkov.indexation.model.source;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Source of the data to be indexed.
 */
public interface Source {

    /**
     * Retrieves all data from the source and returns it as
     * a string.
     *
     * @return all data as a string.
     * @throws IOException if an I/O error occurs reading from th source.
     */
    String stringData() throws IOException;

    /**
     * Returns the stream of lines from the source.
     * Returned stream must be closed after usage.
     *
     * @return stream of lines.
     * @throws IOException if an I/O error occurs reading from th source.
     */
    Stream<String> lines() throws IOException;

}
