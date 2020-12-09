package ru.mpoplavkov.indexation.text.source;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementation of the source for text files.
 */
@RequiredArgsConstructor
public class FileSource implements Source {
    private final Path file;

    /**
     * Retrieves all data from the source and returns it as a string.
     * The file contents should not be modified during the execution of
     * this method. Otherwise, the result of this method is undefined.
     *
     * @return all data as a string.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    @Override
    public String stringData() throws IOException {
        List<String> lines = Files.readAllLines(file);
        return String.join("\n", lines);
    }

    /**
     * Returns the stream of lines from the source.
     * Returned stream must be closed after usage.
     * <p>
     * The file contents should not be modified during the execution of the
     * terminal stream operation. Otherwise, the result of the terminal stream
     * operation is undefined.
     *
     * @return stream of lines.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    @Override
    public Stream<String> lines() throws IOException {
        return Files.lines(file);
    }

}