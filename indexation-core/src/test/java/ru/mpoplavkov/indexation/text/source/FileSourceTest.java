package ru.mpoplavkov.indexation.text.source;

import org.junit.jupiter.api.Test;
import ru.mpoplavkov.indexation.text.source.impl.FileSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.mpoplavkov.indexation.util.TestUtil.getFileContentFromResources;
import static ru.mpoplavkov.indexation.util.TestUtil.getFilePathFromResources;

class FileSourceTest {

    @Test
    public void shouldExtractContentsFromAnEmptyFile() throws IOException {
        Source source = new FileSource(getFilePathFromResources("text/empty_file.txt"));
        assertEquals("", source.stringData());
    }

    @Test
    public void shouldExtractContentsFromNonEmptyFile() throws IOException {
        Path file = getFilePathFromResources("text/some_text_file.txt");
        Source source = new FileSource(file);
        String expected = getFileContentFromResources(file);
        assertEquals(expected, source.stringData());
    }

    @Test
    public void shouldExtractStreamOfLinesFromNonEmptyFile() throws IOException {
        Path file = getFilePathFromResources("text/some_text_file.txt");
        Source source = new FileSource(file);
        List<String> actual = source.lines().collect(Collectors.toList());
        List<String> expected = Arrays.asList(getFileContentFromResources(file).split("\n"));
        assertEquals(expected, actual);
    }

}
