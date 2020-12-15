package ru.mpoplavkov.indexation.filter.impl;

import ru.mpoplavkov.indexation.filter.PathFilter;

import java.nio.file.Path;

public class AcceptAllPathFilter implements PathFilter {
    @Override
    public boolean filter(Path path) {
        return true;
    }
}
