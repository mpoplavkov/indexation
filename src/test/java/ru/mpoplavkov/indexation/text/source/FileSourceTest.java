package ru.mpoplavkov.indexation.text.source;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.mpoplavkov.indexation.TestUtils.getFileContentFromResources;
import static ru.mpoplavkov.indexation.TestUtils.getFilePathFromResources;

class FileSourceTest {

    @Test
    public void shouldExtractContentsFromAnEmptyFile() throws IOException {
        String filePath = "source/empty_file.txt";
        Source source = new FileSource(getFilePathFromResources(filePath));
        assertEquals("", source.stringData());
    }

    @Test
    public void shouldExtractContentsFromNonEmptyFile() throws IOException {
        String filePath = "source/some_text_file.txt";
        Source source = new FileSource(getFilePathFromResources(filePath));
        String expected = getFileContentFromResources(filePath);
        assertEquals(expected, source.stringData());
    }

    @Test
    public void shouldExtractStreamOfLinesFromNonEmptyFile() throws IOException {
        String filePath = "source/some_text_file.txt";
        Source source = new FileSource(getFilePathFromResources(filePath));
        List<String> actual = source.lines().collect(Collectors.toList());
        List<String> expected = Arrays.asList(getFileContentFromResources(filePath).split("\n"));
        assertEquals(expected, actual);
    }

}
