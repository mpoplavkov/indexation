package ru.mpoplavkov.indexation.listener.impl;

import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.Source;
import ru.mpoplavkov.indexation.text.source.impl.FileSource;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

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
        Preconditions.checkArgument(!Files.isDirectory(changedFile));
        switch (fileSystemEvent.getKind()) {
            case ENTRY_CREATE:
            case ENTRY_MODIFY:
                if (!pathFilter.filter(changedFile)) {
                    log.log(Level.INFO, "File [{0}] did not pass the filter", changedFile.toAbsolutePath());
                    return;
                }
                indexFile(changedFile);
            case ENTRY_DELETE:
                index.delete(changedFile);
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
