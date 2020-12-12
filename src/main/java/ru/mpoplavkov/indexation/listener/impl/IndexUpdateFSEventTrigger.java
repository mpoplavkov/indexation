package ru.mpoplavkov.indexation.listener.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import ru.mpoplavkov.indexation.filter.FileFilter;
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
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

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

    private final FileFilter fileFilter;

    /**
     * Specifies how to extract terms from files.
     */
    private final TermsExtractor termsExtractor;

    private final TermsTransformer termsTransformer;

    @Override
    public void onEvent(FileSystemEvent fileSystemEvent) throws IOException {
        Path path = fileSystemEvent.getContext();
        FileSystemEvent.Kind kind = fileSystemEvent.getKind();
        switch (kind) {
            case FILE_CREATE:
            case FILE_UPDATE:
            case FILE_DELETE:
                processFileEvent(kind, path);
                break;
            case DIRECTORY_CREATE:
                List<Path> allFiles = Files.walk(path)
                        .filter(f -> !Files.isDirectory(f))
                        .collect(Collectors.toList());
                for (Path file : allFiles) {
                    processFileEvent(FileSystemEvent.Kind.FILE_CREATE, file);
                }
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("FileSystemEvent kind '%s' is not supported", fileSystemEvent.getKind())
                );
        }

    }

    private void processFileEvent(FileSystemEvent.Kind kind, Path file) throws IOException {
        if (fileFilter.filter(file)) {
            switch (kind) {
                case FILE_CREATE:
                case FILE_UPDATE:
                    indexFile(file);
                    break;
                case FILE_DELETE:
                    index.delete(file);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            String.format("FileSystemEvent kind '%s' is not a file event", kind)
                    );
            }
        } else {
            log.log(Level.INFO, "File [{0}] did not pass the filter", file.toAbsolutePath());
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
