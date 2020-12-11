package ru.mpoplavkov.indexation.listener.impl;

import lombok.RequiredArgsConstructor;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.FileSource;
import ru.mpoplavkov.indexation.text.source.Source;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final TermsTransformer termsTransformer;

    @Override
    public void onEvent(FileSystemEvent fileSystemEvent) throws IOException {
        Path path = fileSystemEvent.getContext();
        switch (fileSystemEvent.getKind()) {
            case FILE_CREATE:
            case FILE_UPDATE:
                indexFile(path);
                break;
            case FILE_DELETE:
                index.delete(path);
                break;
            case DIRECTORY_CREATE:
                List<Path> allFiles = Files.walk(path)
                        .filter(f -> !Files.isDirectory(f))
                        .collect(Collectors.toList());
                for (Path file : allFiles) {
                    indexFile(file);
                }
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("FileEvent kind '%s' is not supported", fileSystemEvent.getKind())
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
