package ru.mpoplavkov.indexation.listener.impl;

import org.junit.jupiter.api.Test;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

import java.io.IOException;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static ru.mpoplavkov.indexation.TestUtils.createSet;
import static ru.mpoplavkov.indexation.TestUtils.getFilePathFromResources;

class IndexUpdateFileChangeEventTriggerTest {

    // TODO: deal with unchecked assignment
    TermIndex<Path> index = mock(TermIndex.class);
    TermsExtractor extractor = mock(TermsExtractor.class);
    TermsTransformer transformer = mock(TermsTransformer.class);
    PathFilter pathFilter = mock(PathFilter.class);
    FSEventTrigger trigger = new IndexUpdateFileChangeEventTrigger(index, pathFilter, extractor, transformer);

    Path file = getFilePathFromResources("text/empty_file.txt");

    Term term1 = new WordTerm("term1");
    Term term2 = new WordTerm("term2");

    @Test
    public void shouldCorrectlyReactOnFileChangeEvent() throws IOException {
        when(pathFilter.filter(any())).thenReturn(true);
        when(extractor.extractTerms(any())).thenReturn(createSet(term1));
        when(transformer.transform(term1)).thenReturn(term2);
        trigger.onEvent(file);

        verify(index).index(file, createSet(term2));
    }

    @Test
    public void shouldNotReactOnUninterestingPaths() throws IOException {
        when(pathFilter.filter(any())).thenReturn(false);
        trigger.onEvent(file);

        verifyNoInteractions(extractor);
        verifyNoInteractions(transformer);
        verifyNoInteractions(index);
    }

}
