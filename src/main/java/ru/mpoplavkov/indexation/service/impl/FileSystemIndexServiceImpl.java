package ru.mpoplavkov.indexation.service.impl;

import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.filter.impl.TextPathFilter;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.index.impl.VersionedTermIndex;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.listener.FileSystemEventListener;
import ru.mpoplavkov.indexation.listener.impl.IndexUpdateFSEventTrigger;
import ru.mpoplavkov.indexation.listener.impl.WatchServiceBasedListenerImpl;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;
import ru.mpoplavkov.indexation.text.transformer.impl.IdTermsTransformer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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
     * Underlying listener of file events.
     */
    private final FileSystemEventListener listener;

    private ExecutorService listenerExecutorService;

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
        FSEventTrigger trigger = new IndexUpdateFSEventTrigger(index, pathFilter, termsExtractor, termsTransformer);
        listener = new WatchServiceBasedListenerImpl(pathFilter, trigger);

        startListener(listenerThreadsCount);
    }

    public FileSystemIndexServiceImpl(TermsExtractor termsExtractor,
                                      int listenerThreadsCount) throws IOException {
        this(termsExtractor, new IdTermsTransformer(), new TextPathFilter(), listenerThreadsCount);
    }

    private void startListener(int parallelism) {
        listenerExecutorService = createListenerExecutorService(parallelism);

        for (int i = 0; i < parallelism; i++) {
            listenerExecutorService.execute(listener::listenLoop);
        }
    }

    @Override
    public Set<Path> search(Query query) {
        Query transformedQuery = query.transform(termsTransformer);
        return index.search(transformedQuery);
    }

    @Override
    public void addToIndex(Path path) throws IOException {
        listener.register(path);
    }

    @Override
    public void close() throws IOException {
        listener.close();
        if (listenerExecutorService != null) {
            listenerExecutorService.shutdownNow();
        }
    }

    private ExecutorService createListenerExecutorService(int parallelism) {
        return Executors.newFixedThreadPool(parallelism, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                String threadName = String.format("listener-%d", count.incrementAndGet());
                Thread thread = new Thread(r, threadName);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

}
