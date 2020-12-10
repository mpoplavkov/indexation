package ru.mpoplavkov.indexation.trigger.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.FileSource;
import ru.mpoplavkov.indexation.text.source.Source;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;

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
    public void onEvent(FileSystemEvent fileSystemEvent) {
        Path file = fileSystemEvent.getContext();
        Source fileSource = new FileSource(file);
        Iterable<Term> terms = termsExtractor.extractTerms(fileSource);
        switch (fileSystemEvent.getKind()) {
            case FILE_CREATE:
                index.index(file, terms);
            case FILE_UPDATE:
                index.index(file, terms);
            case FILE_DELETE:
                index.delete(file);
            default:
                throw new UnsupportedOperationException(
                        String.format("FileEvent kind '%s' is not supported", fileSystemEvent.getKind())
                );
        }
    }
}
