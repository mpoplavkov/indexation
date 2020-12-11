package ru.mpoplavkov.indexation.text.source.impl;

import ru.mpoplavkov.indexation.text.source.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class CopyOnReadFileSource implements Source {

    private static Path TMP_DIR;

    static {
        try {
            TMP_DIR = Files.createTempDirectory(".indexer_tmp");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final FileSource fileSource;

    public Path fileForDebug;

    public CopyOnReadFileSource(Path file) throws IOException {
        try {
            System.out.println(file.toAbsolutePath() + " - " + Files.probeContentType(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        Path tempFile = tempCopy(file);
        fileForDebug = tempFile;
        fileSource = new FileSource(tempFile);
    }

    // TODO: atomic
    private Path tempCopy(Path file) throws IOException {
        Path tmp = Files.createTempFile(TMP_DIR, "Indexation-", ".tmp");
        Files.copy(file, tmp, StandardCopyOption.REPLACE_EXISTING);
        return tmp;
    }

    @Override
    public String stringData() throws IOException {
        return fileSource.stringData();
    }

    @Override
    public Stream<String> lines() throws IOException {
        return fileSource.lines();
    }
}
