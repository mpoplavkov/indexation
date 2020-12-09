package ru.mpoplavkov.indexation.text.source;

import lombok.Data;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Implementation of the source for strings.
 */
@Data
public class StringSource implements Source {
    private final String data;

    @Override
    public String stringData() {
        return data;
    }

    @Override
    public Stream<String> lines() {
        return Arrays.stream(data.split("\n"));
    }
}
