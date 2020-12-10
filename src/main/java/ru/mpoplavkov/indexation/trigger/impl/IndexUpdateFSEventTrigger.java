package ru.mpoplavkov.indexation.trigger.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.FileSource;
import ru.mpoplavkov.indexation.text.source.Source;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Trigger that updates underlying index in accordance with events.
 */
@RequiredArgsConstructor
public class IndexUpdateFSEventTrigger implements FSEventTrigger {

    /**
     * Index to update.
     */
    private final TermIndex<Path> index;

    /**
     * Specifies how to extract terms from files.
     */
    private final TermsExtractor termsExtractor;

    @Override
    public void onEvent(FileSystemEvent fileSystemEvent) throws IOException {
        Path path = fileSystemEvent.getContext();
        switch (fileSystemEvent.getKind()) {
            case FILE_CREATE:
                indexFile(path);
            case FILE_UPDATE:
                indexFile(path);
            case FILE_DELETE:
                index.delete(path);
            case DIRECTORY_CREATE:
                Files.walk(path).forEach(this::indexFile);
            default:
                throw new UnsupportedOperationException(
                        String.format("FileEvent kind '%s' is not supported", fileSystemEvent.getKind())
                );
        }
    }

    private void indexFile(Path file) {
        Iterable<Term> terms = termsFromFile(file);
        index.index(file, terms);
    }

    private Iterable<Term> termsFromFile(Path file) {
        Source fileSource = new FileSource(file);
        return termsExtractor.extractTerms(fileSource);
    }
}
