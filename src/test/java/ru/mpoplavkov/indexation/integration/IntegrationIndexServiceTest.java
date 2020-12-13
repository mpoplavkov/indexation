package ru.mpoplavkov.indexation.integration;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.filter.impl.AcceptAllPathFilter;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;
import ru.mpoplavkov.indexation.service.impl.FileSystemIndexServiceImpl;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.extractor.impl.SplitBySpaceTermsExtractor;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;
import ru.mpoplavkov.indexation.text.transformer.impl.IdTermsTransformer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.mpoplavkov.indexation.TestUtils.createSet;
import static ru.mpoplavkov.indexation.TestUtils.getFilePathFromResources;

public class IntegrationIndexServiceTest {

    TermsExtractor extractor = new SplitBySpaceTermsExtractor();
    TermsTransformer transformer = new IdTermsTransformer();
    PathFilter filter = new AcceptAllPathFilter();
    FileSystemIndexService service;

    Path integrationDir = getFilePathFromResources("integration");
    Path dir = integrationDir.resolve("dir");
    Path subDir = dir.resolve("subdir");
    Path dirFile1 = dir.resolve("directory_file_1.txt");
    Path subDirFile1 = subDir.resolve("subdirectory_file_1.txt");
    Path subDirFile2 = subDir.resolve("subdirectory_file_2.txt");

    @BeforeEach
    public void init() throws IOException {
        service = new FileSystemIndexServiceImpl(extractor, transformer, filter, 2);
    }

    @Test
    public void shouldFindIndexedFile() throws IOException {
        service.addToIndex(dirFile1);

        Assertions.assertAll(
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery("word1"))),
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery("word2"))),
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery("word3")))
        );
    }

    @Test
    public void shouldThrowAnExceptionForMissedFile() throws IOException {
        Path file = new File("missed.txt").toPath();
        assertThrows(FileNotFoundException.class, () -> service.addToIndex(file));
    }

    @Test
    public void shouldCorrectlySearchFromIndexedDirectory() throws IOException {
        service.addToIndex(subDir);

        Assertions.assertAll(
                () -> assertEquals(createSet(subDirFile2), service.search(wordQuery("word2"))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery("word3"))),
                () -> assertEquals(createSet(subDirFile1, subDirFile2), service.search(wordQuery("word4"))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery("word5")))
        );
    }

    @Test
    public void shouldCorrectlySearchFromIndexedMultiLevelDirectory() throws IOException {
        service.addToIndex(dir);

        Assertions.assertAll(
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery("word1"))),
                () -> assertEquals(createSet(dirFile1, subDirFile2), service.search(wordQuery("word2"))),
                () -> assertEquals(createSet(dirFile1, subDirFile1), service.search(wordQuery("word3"))),
                () -> assertEquals(createSet(subDirFile1, subDirFile2), service.search(wordQuery("word4"))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery("word5")))
        );
    }

    @Test
    public void shouldIndexNewFile(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(directoryIsEmpty(tempDir));
        service.addToIndex(tempDir);
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());

        waitUntilServiceReactsToChanges();

        Assertions.assertAll(
                () -> assertEquals(createSet(newFile), service.search(wordQuery("word1"))),
                () -> assertEquals(createSet(newFile), service.search(wordQuery("word2")))
        );

    }

    private Query wordQuery(String word) {
        return new ExactTerm(new WordTerm(word));
    }

    private boolean directoryIsEmpty(Path path) throws IOException {
        Preconditions.checkArgument(Files.isDirectory(path));
        return Files.list(path).count() == 0;
    }

    @SneakyThrows
    private void waitUntilServiceReactsToChanges() {
        Thread.sleep(10000);
    }

}
