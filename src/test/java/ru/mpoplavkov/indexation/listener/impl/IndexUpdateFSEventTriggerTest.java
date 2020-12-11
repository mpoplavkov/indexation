package ru.mpoplavkov.indexation.listener.impl;

import org.junit.jupiter.api.Test;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.listener.FSEventTrigger;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
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

class IndexUpdateFSEventTriggerTest {

    // TODO: deal with unchecked assignment
    TermIndex<Path> index = mock(TermIndex.class);
    TermsExtractor extractor = mock(TermsExtractor.class);
    TermsTransformer transformer = mock(TermsTransformer.class);
    FSEventTrigger trigger = new IndexUpdateFSEventTrigger(index, extractor, transformer);

    Path file = getFilePathFromResources("text/empty_file.txt");
    Path dir = getFilePathFromResources("text/directory_with_two_files");
    Path file1InsideDir = getFilePathFromResources("text/directory_with_two_files/file1.txt");
    Path file2InsideDir = getFilePathFromResources("text/directory_with_two_files/file2.txt");

    Term term1 = new WordTerm("term1");
    Term term2 = new WordTerm("term2");

    IndexUpdateFSEventTriggerTest() throws IOException {
    }

    @Test
    public void shouldCorrectlyReactOnCreateFileEvent() throws IOException {
        FileSystemEvent event = new FileSystemEvent(FileSystemEvent.Kind.FILE_CREATE, file);
        when(extractor.extractTerms(any())).thenReturn(createSet(term1));
        when(transformer.transform(term1)).thenReturn(term2);
        trigger.onEvent(event);

        verify(index).index(file, createSet(term2));
    }

    @Test
    public void shouldCorrectlyReactOnUpdateFileEvent() throws IOException {
        FileSystemEvent event = new FileSystemEvent(FileSystemEvent.Kind.FILE_UPDATE, file);
        when(extractor.extractTerms(any())).thenReturn(createSet(term1));
        when(transformer.transform(term1)).thenReturn(term2);
        trigger.onEvent(event);

        verify(index).index(file, createSet(term2));
    }

    @Test
    public void shouldCorrectlyReactOnDeleteFileEvent() throws IOException {
        FileSystemEvent event = new FileSystemEvent(FileSystemEvent.Kind.FILE_DELETE, file);
        trigger.onEvent(event);
        verify(index).delete(file);
    }

    @Test
    public void shouldCorrectlyReactOnCreateDirectoryEvent() throws IOException {
        FileSystemEvent event = new FileSystemEvent(FileSystemEvent.Kind.DIRECTORY_CREATE, dir);
        when(extractor.extractTerms(any())).thenReturn(createSet(term1));
        when(transformer.transform(term1)).thenReturn(term2);
        trigger.onEvent(event);

        verify(index).index(file1InsideDir, createSet(term2));
        verify(index).index(file2InsideDir, createSet(term2));
    }

}
