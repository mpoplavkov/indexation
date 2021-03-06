package ru.mpoplavkov.indexation.trigger.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.Source;
import ru.mpoplavkov.indexation.text.source.impl.FileSource;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;
import ru.mpoplavkov.indexation.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Trigger that updates underlying index in accordance with events.
 */
@Log
@RequiredArgsConstructor
public class IndexUpdateFileChangeEventTrigger implements FSEventTrigger {

    /**
     * Index to update.
     */
    private final TermIndex<Path> index;

    /**
     * Filter to check files before processing.
     */
    private final PathFilter pathFilter;

    /**
     * Specifies how to extract terms from files.
     */
    private final TermsExtractor termsExtractor;

    /**
     * Specifies how to transform terms, extracted from files,
     * before indexation.
     */
    private final TermsTransformer termsTransformer;

    @Override
    public void onEvent(FileSystemEvent fileSystemEvent) throws IOException {
        Path changedFile = fileSystemEvent.getEntry();
        if (Files.isDirectory(changedFile)) {
            // skip
            return;
        }
        switch (fileSystemEvent.getKind()) {
            case ENTRY_CREATE:
            case ENTRY_MODIFY:
                if (!pathFilter.filter(changedFile)) {
                    log.info(() -> String.format("File '%s' did not pass the filter", FileUtil.getCanonicalPath(changedFile)));
                    return;
                }
                indexFile(changedFile);
                break;
            case ENTRY_DELETE:
                index.delete(changedFile);
                break;
            default:
                throw new RuntimeException(
                        String.format("FSEvent kind '%s' is not supported", fileSystemEvent.getKind())
                );
        }
    }

    private void indexFile(Path file) throws IOException {
        Set<Term> terms = termsFromFile(file);
        Set<Term> transformedTerms = new HashSet<>();
        terms.forEach(t -> transformedTerms.add(termsTransformer.transform(t)));
        index.index(file, transformedTerms);
    }

    private Set<Term> termsFromFile(Path file) throws IOException {
        Source fileSource = new FileSource(file);
        return termsExtractor.extractTerms(fileSource);
    }
}
