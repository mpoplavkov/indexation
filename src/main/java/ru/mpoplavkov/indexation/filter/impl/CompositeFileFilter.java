package ru.mpoplavkov.indexation.filter.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.filter.FileFilter;

import java.nio.file.Path;
import java.util.List;

@RequiredArgsConstructor
public class CompositeFileFilter implements FileFilter {
    private final List<FileFilter> filters;

    @Override
    public boolean filter(Path file) {
        for (FileFilter filter : filters) {
            if (!filter.filter(file)) {
                return false;
            }
        }
        return true;
    }
}
