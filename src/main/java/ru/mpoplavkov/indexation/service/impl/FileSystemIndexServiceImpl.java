package ru.mpoplavkov.indexation.service.impl;

import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;
import ru.mpoplavkov.indexation.trigger.FileSystemEventListener;
import ru.mpoplavkov.indexation.trigger.impl.FSEventListenerImpl;
import ru.mpoplavkov.indexation.trigger.impl.IndexUpdateFSEventTrigger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class FileSystemIndexServiceImpl implements FileSystemIndexService {

    /**
     * Index to work with.
     */
    private final TermIndex<Path> index;

    private final TermsTransformer termsTransformer;

    private final FileSystemEventListener listener;

    private ExecutorService listenerExecutorService;

    public FileSystemIndexServiceImpl(TermIndex<Path> index,
                                      TermsExtractor termsExtractor,
                                      TermsTransformer termsTransformer) throws IOException {
        this.index = index;
        this.termsTransformer = termsTransformer;

        FSEventTrigger trigger = new IndexUpdateFSEventTrigger(index, termsExtractor, termsTransformer);
        listener = new FSEventListenerImpl(trigger);
    }

    @Override
    public boolean startListener(int parallelism) {
        if (listenerExecutorService != null) {
            return false;
        }
        listenerExecutorService = createListenerExecutorService(parallelism);

        for (int i = 0; i < parallelism; i++) {
            listenerExecutorService.execute(listener::listenLoop);
        }

        return true;
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
                String threadName = String.format("trigger-thread-%d", count.incrementAndGet());
                Thread thread = new Thread(r, threadName);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

}
