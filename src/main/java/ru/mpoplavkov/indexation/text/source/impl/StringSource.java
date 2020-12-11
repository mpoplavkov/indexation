package ru.mpoplavkov.indexation.text.source.impl;

import lombok.Data;
import ru.mpoplavkov.indexation.text.source.Source;

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
