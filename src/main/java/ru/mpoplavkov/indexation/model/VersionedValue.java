package ru.mpoplavkov.indexation.model;

import lombok.Data;

@Data
public class VersionedValue<V> {
    private final V value;
    private final int version;
}
