package ru.mpoplavkov.indexation.integration;

import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.filter.impl.TextPathFilter;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.mpoplavkov.indexation.TestUtils.createSet;
import static ru.mpoplavkov.indexation.TestUtils.getFilePathFromResources;

@Disabled("integration tests")
public class IntegrationIndexServiceTest {

    TermsExtractor extractor = new SplitBySpaceTermsExtractor();
    TermsTransformer transformer = new IdTermsTransformer();
    PathFilter filter = new TextPathFilter();
    FileSystemIndexService service;

    Path integrationDir = getFilePathFromResources("integration");
    Path dir = integrationDir.resolve("dir");
    Path subDir = dir.resolve("subdir");
    Path anotherSubDir = dir.resolve("anotherSubdir");
    Path dirFile1 = dir.resolve("directory_file_1.txt");
    Path subDirFile1 = subDir.resolve("subdirectory_file_1.txt");
    Path subDirFile2 = subDir.resolve("subdirectory_file_2.txt");
    Path anotherSubDirFile = anotherSubDir.resolve("another_subdirectory_file.txt");

    String word1 = "word1";
    String word2 = "word2";
    String word3 = "word3";
    String word4 = "word4";
    String word5 = "word5";

    @BeforeEach
    public void init() throws IOException {
        service = new FileSystemIndexServiceImpl(extractor, transformer, filter, 2);
    }

    @Test
    public void shouldFindIndexedFile() throws IOException {
        service.addToIndex(dirFile1);

        Assertions.assertAll(
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word1))),
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word3)))
        );
    }

    @Test
    public void shouldThrowAnExceptionForAddingAMissedFile() {
        Path file = new File("missed.txt").toPath();
        assertThrows(FileNotFoundException.class, () -> service.addToIndex(file));
    }

    @Test
    public void shouldFindSeveralIndexedFiles() throws IOException {
        service.addToIndex(subDirFile1);
        service.addToIndex(subDirFile2);

        Assertions.assertAll(
                () -> assertEquals(createSet(subDirFile2), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(subDirFile1, subDirFile2), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldCorrectlySearchFromIndexedDirectory() throws IOException {
        service.addToIndex(subDir);

        Assertions.assertAll(
                () -> assertEquals(createSet(subDirFile2), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(subDirFile1, subDirFile2), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldCorrectlySearchFromSeveralIndexedDirectories() throws IOException {
        service.addToIndex(subDir);
        service.addToIndex(anotherSubDir);

        Assertions.assertAll(
                () -> assertEquals(createSet(subDirFile2), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(subDirFile1, anotherSubDirFile), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(subDirFile1, subDirFile2), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldCorrectlySearchFromIndexedMultiLevelDirectory() throws IOException {
        service.addToIndex(dir);

        Assertions.assertAll(
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word1))),
                () -> assertEquals(createSet(dirFile1, subDirFile2), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(dirFile1, subDirFile1, anotherSubDirFile), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(subDirFile1, subDirFile2), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(subDirFile1), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldIndexNewFile(@TempDir Path tempDir) throws IOException {
        service.addToIndex(tempDir);
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());

        waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word1)).isEmpty());

        Assertions.assertAll(
                () -> assertEquals(createSet(newFile), service.search(wordQuery(word1))),
                () -> assertEquals(createSet(newFile), service.search(wordQuery(word2)))
        );
    }

    @Test
    public void shouldIndexNewDirectory(@TempDir Path tempDir) throws IOException {
        service.addToIndex(tempDir);
        Path newDir = Files.createDirectory(tempDir.resolve("new_dir"));
        Path newFile = Files.createFile(newDir.resolve("file.txt"));
        Files.write(newFile, word1.getBytes());

        waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(newFile), service.search(wordQuery(word1)));
    }

    @Test
    public void shouldIndexNewMultilevelDirectory(@TempDir Path tempDir) throws IOException {
        service.addToIndex(tempDir);
        Path newDir = Files.createDirectories(tempDir.resolve("a").resolve("b").resolve("c"));
        Path newFile = Files.createFile(newDir.resolve("file.txt"));
        Files.write(newFile, word1.getBytes());

        waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(newFile), service.search(wordQuery(word1)));
    }

    @Test
    public void shouldIndexNewMultilevelDirectoryWithFilesOnEachLevel(@TempDir Path tempDir) throws IOException {
        service.addToIndex(tempDir);
        Path a = Files.createDirectories(tempDir.resolve("a"));
        Path b = Files.createDirectories(a.resolve("b"));
        Path c = Files.createDirectories(b.resolve("c"));

        Path rootFile = Files.createFile(tempDir.resolve("root.txt"));
        Files.write(rootFile, word1.getBytes());

        Path aFile = Files.createFile(a.resolve("a.txt"));
        Files.write(aFile, word1.getBytes());

        Path bFile = Files.createFile(b.resolve("b.txt"));
        Files.write(bFile, word1.getBytes());

        Path cFile = Files.createFile(c.resolve("c.txt"));
        Files.write(cFile, word1.getBytes());

        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).size() == 4);

        assertEquals(createSet(rootFile, aFile, bFile, cFile), service.search(wordQuery(word1)));
    }

    @Test
    public void shouldRemoveDeletedFileFromTheIndex(@TempDir Path tempDir) throws IOException {
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, word1.getBytes());
        service.addToIndex(tempDir);

        Assumptions.assumeFalse(service.search(wordQuery(word1)).isEmpty());
        Files.delete(newFile);

        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(), service.search(wordQuery(word1)));
    }

    @Test
    public void shouldRemoveDeletedDirFromTheIndex(@TempDir Path tempDir) throws IOException {
        Path newDir = Files.createDirectories(tempDir.resolve("a").resolve("b").resolve("c"));
        Path newFile1 = Files.createFile(newDir.resolve("file1.txt"));
        Files.write(newFile1, word1.getBytes());
        Path newFile2 = Files.createFile(newDir.resolve("file2.txt"));
        Files.write(newFile2, word1.getBytes());
        service.addToIndex(tempDir);

        Assumptions.assumeFalse(service.search(wordQuery(word1)).isEmpty());

        // delete dir with its contents
        Files.delete(newFile1);
        Files.delete(newFile2);
        Files.delete(newDir);

        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(), service.search(wordQuery(word1)));
    }

    @Test
    public void shouldNotReactOnSiblingsUpdatesIfOnlyFileWasRegistered(@TempDir Path tempDir) throws IOException {
        Path registeredFile = Files.createFile(tempDir.resolve("file1.txt"));
        Files.write(registeredFile, word1.getBytes());
        Path sibling1 = Files.createFile(tempDir.resolve("file2.txt"));
        Files.write(sibling1, word1.getBytes());
        service.addToIndex(registeredFile);

        Path sibling2 = Files.createFile(tempDir.resolve("file3.txt"));
        Files.write(sibling2, word1.getBytes());

        assertEquals(createSet(registeredFile), service.search(wordQuery(word1)));
        assertThrows(NoServiceReactionException.class,
                () -> waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).size() > 1));
    }

    @Test
    public void shouldIndexTheFullDirectoryEvenIfConcreteChildrenWereAlreadyRegistered(@TempDir Path tempDir) throws IOException {
        Path registeredFile = Files.createFile(tempDir.resolve("file1.txt"));
        Files.write(registeredFile, word1.getBytes());
        Path sibling1 = Files.createFile(tempDir.resolve("file2.txt"));
        Files.write(sibling1, word1.getBytes());
        service.addToIndex(registeredFile);

        Path sibling2 = Files.createFile(tempDir.resolve("file3.txt"));
        Files.write(sibling2, word1.getBytes());

        Assumptions.assumeTrue(createSet(registeredFile).equals(service.search(wordQuery(word1))));

        service.addToIndex(tempDir);

        assertEquals(createSet(registeredFile, sibling1, sibling2), service.search(wordQuery(word1)));
    }

    @Test
    public void shouldReindexChangedFile(@TempDir Path tempDir) throws IOException {
        Path registeredFile = Files.createFile(tempDir.resolve("file1.txt"));
        Files.write(registeredFile, word1.getBytes());
        service.addToIndex(registeredFile);

        Assumptions.assumeTrue(createSet(registeredFile).equals(service.search(wordQuery(word1))));

        Files.write(registeredFile, word2.getBytes());
        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(registeredFile), service.search(wordQuery(word2)));
    }

    @Test
    public void shouldReindexSeveralChangedFiles(@TempDir Path tempDir) throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("file1.txt"));
        Files.write(file1, word1.getBytes());
        Path file2 = Files.createFile(tempDir.resolve("file2.txt"));
        Files.write(file2, word1.getBytes());
        service.addToIndex(file1);
        service.addToIndex(file2);

        Assumptions.assumeTrue(createSet(file1, file2).equals(service.search(wordQuery(word1))));

        Files.write(file1, word2.getBytes());
        Files.write(file2, word2.getBytes());
        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(file1, file2), service.search(wordQuery(word2)));
    }

    @Test
    public void shouldReindexChangedFileFromRegisteredDirectory(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("file1.txt"));
        Files.write(file, word1.getBytes());
        service.addToIndex(tempDir);

        Assumptions.assumeTrue(createSet(file).equals(service.search(wordQuery(word1))));

        Files.write(file, word2.getBytes());
        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(file), service.search(wordQuery(word2)));
    }

    @Test
    public void shouldReindexSeveralChangedFilesFromRegisteredDirectory(@TempDir Path tempDir) throws IOException {
        Path file1 = Files.createFile(tempDir.resolve("file1.txt"));
        Files.write(file1, word1.getBytes());
        Path file2 = Files.createFile(tempDir.resolve("file2.txt"));
        Files.write(file2, word1.getBytes());
        service.addToIndex(tempDir);

        Assumptions.assumeTrue(createSet(file1, file2).equals(service.search(wordQuery(word1))));

        Files.write(file1, word2.getBytes());
        Files.write(file2, word2.getBytes());
        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(file1, file2), service.search(wordQuery(word2)));
    }

    @Test
    public void shouldReindexChangedFilesFromSeveralRegisteredDirectories(@TempDir Path tempDir) throws IOException {
        Path dir1 = Files.createDirectory(tempDir.resolve("a"));
        Path dir2 = Files.createDirectory(tempDir.resolve("b"));
        Path file1 = Files.createFile(dir1.resolve("file1.txt"));
        Files.write(file1, word1.getBytes());
        Path file2 = Files.createFile(dir2.resolve("file2.txt"));
        Files.write(file2, word1.getBytes());
        service.addToIndex(dir1);
        service.addToIndex(dir2);

        Assumptions.assumeTrue(createSet(file1, file2).equals(service.search(wordQuery(word1))));

        Files.write(file1, word2.getBytes());
        Files.write(file2, word2.getBytes());
        waitUntilServiceReactsToChanges(() -> service.search(wordQuery(word1)).isEmpty());

        assertEquals(createSet(file1, file2), service.search(wordQuery(word2)));
    }

    @Test
    public void shouldRemoveFileFromTheIndex() throws IOException {
        service.addToIndex(dirFile1);

        service.removeFromIndex(dirFile1);

        Assertions.assertAll(
                () -> assertEquals(createSet(), service.search(wordQuery(word1))),
                () -> assertEquals(createSet(), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(), service.search(wordQuery(word3)))
        );
    }

    @Test
    public void shouldThrowAnExceptionForRemovingAMissedFile() {
        Path file = new File("missed.txt").toPath();
        assertThrows(FileNotFoundException.class, () -> service.removeFromIndex(file));
    }

    @Test
    public void shouldRemoveSeveralFilesFromTheIndex() throws IOException {
        service.addToIndex(subDirFile1);
        service.addToIndex(subDirFile2);

        service.removeFromIndex(subDirFile1);
        service.removeFromIndex(subDirFile2);

        Assertions.assertAll(
                () -> assertEquals(createSet(), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldRemoveDirectoryFromTheIndex() throws IOException {
        service.addToIndex(subDir);

        service.removeFromIndex(subDir);

        Assertions.assertAll(
                () -> assertEquals(createSet(), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldRemoveSeveralDirectoriesFromTheIndex() throws IOException {
        service.addToIndex(subDir);
        service.addToIndex(anotherSubDir);

        service.removeFromIndex(subDir);
        service.removeFromIndex(anotherSubDir);

        Assertions.assertAll(
                () -> assertEquals(createSet(), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldRemoveMultilevelDirectoryFromTheIndex() throws IOException {
        service.addToIndex(dir);

        service.removeFromIndex(dir);

        Assertions.assertAll(
                () -> assertEquals(createSet(), service.search(wordQuery(word1))),
                () -> assertEquals(createSet(), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(), service.search(wordQuery(word4))),
                () -> assertEquals(createSet(), service.search(wordQuery(word5)))
        );
    }

    @Test
    public void shouldNotReactToChangesInARemovedFile(@TempDir Path tempDir) throws IOException {
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());
        service.addToIndex(tempDir);

        service.removeFromIndex(newFile);
        Files.write(newFile, "word3 word4".getBytes());

        assertThrows(NoServiceReactionException.class,
                () -> waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word3)).isEmpty()));
    }

    @Test
    public void shouldNotReactToChangesInAFileFromARemovedDirectory(@TempDir Path tempDir) throws IOException {
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());

        service.addToIndex(tempDir);

        service.removeFromIndex(tempDir);
        Files.write(newFile, "word3 word4".getBytes());

        assertThrows(NoServiceReactionException.class,
                () -> waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word3)).isEmpty()));
    }

    @Test
    public void shouldNotReactToChangesInAFileFromARemovedMultilevelDirectory(@TempDir Path tempDir) throws IOException {
        Path multilevelDir = Files.createDirectories(tempDir.resolve("a").resolve("b").resolve("c"));
        Path newFile = Files.createFile(multilevelDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());

        service.addToIndex(tempDir);

        service.removeFromIndex(tempDir);
        Files.write(newFile, "word3 word4".getBytes());

        assertThrows(NoServiceReactionException.class,
                () -> waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word3)).isEmpty()));

    }

    @Test
    public void shouldIndexRemovedFileThatWasRecreated(@TempDir Path tempDir) throws IOException {
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());
        service.addToIndex(tempDir);

        service.removeFromIndex(newFile);

        Files.delete(newFile);
        Files.createFile(newFile);
        Files.write(newFile, "word3 word4".getBytes());

        waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word3)).isEmpty());

        Assertions.assertAll(
                () -> assertEquals(createSet(newFile), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(newFile), service.search(wordQuery(word4)))
        );
    }

    @Test
    public void shouldIndexChangedFileIfItsSiblingWasRemoved(@TempDir Path tempDir) throws IOException {
        Path sibling1 = Files.createFile(tempDir.resolve("sibling1.txt"));
        Path sibling2 = Files.createFile(tempDir.resolve("sibling2.txt"));
        Files.write(sibling1, "word1 word2".getBytes());
        Files.write(sibling2, "word2 word3".getBytes());
        service.addToIndex(tempDir);

        service.removeFromIndex(sibling1);

        Files.write(sibling2, "word4 word5".getBytes());

        waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word4)).isEmpty());

        Assertions.assertAll(
                () -> assertEquals(createSet(sibling2), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(sibling2), service.search(wordQuery(word4)))
        );
    }

    @Test
    public void shouldAddAFileToTheIndexAfterRemovingIt() throws IOException {
        service.addToIndex(dirFile1);
        service.removeFromIndex(dirFile1);
        service.addToIndex(dirFile1);

        Assertions.assertAll(
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word1))),
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word2))),
                () -> assertEquals(createSet(dirFile1), service.search(wordQuery(word3)))
        );
    }

    @Test
    public void shouldReactToChangesInAFileAddedToTheIndexAfterRemovingIt(@TempDir Path tempDir) throws IOException {
        Path newFile = Files.createFile(tempDir.resolve("file.txt"));
        Files.write(newFile, "word1 word2".getBytes());
        service.addToIndex(tempDir);

        service.removeFromIndex(newFile);
        service.addToIndex(newFile);
        Files.write(newFile, "word3 word4".getBytes());

        waitUntilServiceReactsToChanges(() -> !service.search(wordQuery(word3)).isEmpty());

        Assertions.assertAll(
                () -> assertEquals(createSet(newFile), service.search(wordQuery(word3))),
                () -> assertEquals(createSet(newFile), service.search(wordQuery(word4)))
        );
    }

    private Query wordQuery(String word) {
        return new ExactTerm(new WordTerm(word));
    }

    @SneakyThrows
    private void waitUntilServiceReactsToChanges(Supplier<Boolean> waitCondition, int limitSeconds) {
        if (waitCondition.get()) {
            return;
        }
        if (limitSeconds == 0) {
            throw new NoServiceReactionException();
        }
        Thread.sleep(1000);
        waitUntilServiceReactsToChanges(waitCondition, limitSeconds - 1);
    }

    private void waitUntilServiceReactsToChanges(Supplier<Boolean> checkReacted) {
        waitUntilServiceReactsToChanges(checkReacted, 30);
    }

    static class NoServiceReactionException extends RuntimeException {
    }

}
