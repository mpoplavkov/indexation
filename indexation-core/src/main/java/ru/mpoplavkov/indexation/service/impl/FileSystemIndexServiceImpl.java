package ru.mpoplavkov.indexation.service.impl;

import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.filter.impl.TextPathFilter;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.index.impl.VersionedTermIndex;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.listener.FileSystemSubscriber;
import ru.mpoplavkov.indexation.listener.impl.IndexUpdateFileChangeEventTrigger;
import ru.mpoplavkov.indexation.listener.impl.WatchServiceFSSubscriber;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;
import ru.mpoplavkov.indexation.text.transformer.impl.IdTermsTransformer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Implementation of the {@link FileSystemIndexService}.
 */
public class FileSystemIndexServiceImpl implements FileSystemIndexService {

    /**
     * Index to work with.
     */
    private final TermIndex<Path> index;

    /**
     * Specifies how to transform terms, both for index and search.
     */
    private final TermsTransformer termsTransformer;

    /**
     * Underlying subscriber to the file system events.
     */
    private final FileSystemSubscriber subscriber;

    /**
     * Creates the service to interact with the index.
     *
     * @param termsExtractor       specifies how to extract terms from files.
     * @param termsTransformer     specifies how to transform terms, both for index and search.
     * @param pathFilter           specifies which files to accept for indexation.
     * @param listenerThreadsCount number of threads to listen for file system events.
     * @throws IOException if an I/O error occurs.
     */
    public FileSystemIndexServiceImpl(TermsExtractor termsExtractor,
                                      TermsTransformer termsTransformer,
                                      PathFilter pathFilter,
                                      int listenerThreadsCount) throws IOException {
        this.termsTransformer = termsTransformer;

        index = new VersionedTermIndex<>();
        FSEventTrigger trigger =
                new IndexUpdateFileChangeEventTrigger(index, pathFilter, termsExtractor, termsTransformer);
        subscriber = new WatchServiceFSSubscriber(pathFilter, trigger);
        subscriber.startToListenForEvents(listenerThreadsCount);
    }

    public FileSystemIndexServiceImpl(TermsExtractor termsExtractor,
                                      int listenerThreadsCount) throws IOException {
        this(termsExtractor, new IdTermsTransformer(), new TextPathFilter(), listenerThreadsCount);
    }

    public FileSystemIndexServiceImpl(TermsExtractor termsExtractor,
                                      TermsTransformer termsTransformer,
                                      int listenerThreadsCount) throws IOException {
        this(termsExtractor, termsTransformer, new TextPathFilter(), listenerThreadsCount);
    }

    @Override
    public Set<Path> search(Query query) {
        Query transformedQuery = query.transform(termsTransformer);
        return index.search(transformedQuery);
    }

    @Override
    public void addToIndex(Path path) throws IOException {
        subscriber.subscribe(path);
    }

    @Override
    public void close() throws IOException {
        subscriber.close();
    }

}
