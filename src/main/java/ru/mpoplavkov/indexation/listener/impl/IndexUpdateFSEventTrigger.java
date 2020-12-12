package ru.mpoplavkov.indexation.listener.impl;

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
public class IndexUpdateFSEventTrigger implements FSEventTrigger {

    /**
     * Index to update.
     */
    private final TermIndex<Path> index;

    /**
     * Filter to check files before the processing.
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
        Path path = fileSystemEvent.getEntry();
        FileSystemEvent.Kind kind = fileSystemEvent.getKind();
        if (Files.isDirectory(path)) {
            processDirectoryEvent(kind, path);
        } else {
            processFileEvent(kind, path);
        }
    }

    private void processFileEvent(FileSystemEvent.Kind kind, Path file) throws IOException {
        if (!checkPath(file)) {
            return;
        }
        switch (kind) {
            case ENTRY_CREATE:
            case ENTRY_MODIFY:
                indexFile(file);
                break;
            case ENTRY_DELETE:
                index.delete(file);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("FileSystemEvent kind '%s' is not a file event", kind)
                );
        }
    }

    private void processDirectoryEvent(FileSystemEvent.Kind kind, Path dir) throws IOException {
        if (!checkPath(dir)) {
            return;
        }
        switch (kind) {
            case ENTRY_CREATE:
                for (Path file : Files.newDirectoryStream(dir)) {
                    if (!Files.isDirectory(dir)) {
                        processFileEvent(FileSystemEvent.Kind.ENTRY_CREATE, file);
                    }
                }
                break;
            case ENTRY_MODIFY:
                // nothing
                break;
            case ENTRY_DELETE:

                // TODO: implement
                log.log(Level.WARNING,
                        String.format("Directory deletion is not implemented yet." +
                                " Files from [{0}] will remain in the index", dir.toAbsolutePath())
                );
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("FileSystemEvent kind '%s' is not a file event", kind)
                );
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkPath(Path path) {
        if (pathFilter.filter(path)) {
            return true;
        } else {
            log.log(Level.INFO, "Path [{0}] did not pass the filter", path.toAbsolutePath());
            return false;
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
