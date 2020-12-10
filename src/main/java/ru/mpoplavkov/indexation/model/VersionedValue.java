package ru.mpoplavkov.indexation.model;

import lombok.Data;

@Data
public class VersionedValue<V> {
    public static final int INITIAL_VERSION = 1;

    private final V value;
    private final int version;
}
