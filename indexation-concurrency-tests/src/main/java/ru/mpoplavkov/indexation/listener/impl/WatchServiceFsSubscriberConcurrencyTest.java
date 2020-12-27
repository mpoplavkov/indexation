package ru.mpoplavkov.indexation.listener.impl;

import lombok.SneakyThrows;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.LLLLLL_Result;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.filter.impl.AcceptAllPathFilter;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;
import ru.mpoplavkov.indexation.trigger.impl.DoNothingTrigger;
import ru.mpoplavkov.indexation.util.CollectionsUtil;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WatchServiceFsSubscriberConcurrencyTest {

    private static final WatchService watcher = createWatchService();

    private static final Path ROOT_DIR = createTempDir("root_dir");
    private static final Path DIR_1 = createTempDir(ROOT_DIR, "dir1");
    private static final Path DIR_1_FILE_1 = createTempFile(DIR_1, "dir1_file1.txt");
    private static final Path DIR_1_FILE_2 = createTempFile(DIR_1, "dir1_file2.txt");
    private static final Path DIR_2 = createTempDir(ROOT_DIR, "dir2");
    private static final Path DIR_2_FILE_1 = createTempFile(DIR_2, "dir2_file1.txt");
    private static final Path DIR_2_FILE_2 = createTempFile(DIR_2, "dir2_file2.txt");

    private static final Set<Path> ALL_DIRS =
            CollectionsUtil.createSet(ROOT_DIR, DIR_1, DIR_2);
    private static final Set<Path> ALL_PATHS =
            CollectionsUtil.createSet(ROOT_DIR, DIR_1, DIR_2, DIR_1_FILE_1, DIR_1_FILE_2, DIR_2_FILE_1, DIR_2_FILE_2);

    @JCStressTest
    @Description("Two threads could concurrently subscribe to different directories. Checks internal state.")
    @Outcome(id = "2, 2, 2, 2, dir1_file1.txt\\d*;dir1_file2.txt\\d*, dir2_file1.txt\\d*;dir2_file2.txt\\d*", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentIndexOfDifferentValuesTest {

        WatchServiceFSSubscriber subscriber = createSubscriber();

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_2);
        }

        @Arbiter
        public void arbiter(LLLLLL_Result r) {
            r.r1 = subscriber.dirsToWatchKeys.size();
            r.r2 = subscriber.watchKeysToDirs.size();
            r.r3 = subscriber.dirsResponsibleForNewFiles.size();
            r.r4 = subscriber.locksMap.size();

            Set<Path> dir1TrackedPaths = subscriber.trackedPaths.get(DIR_1).stream()
                    .map(Path::getFileName)
                    .collect(Collectors.toSet());
            Set<Path> dir2TrackedPaths = subscriber.trackedPaths.get(DIR_2).stream()
                    .map(Path::getFileName)
                    .collect(Collectors.toSet());

            r.r5 = CollectionsUtil.makeSortedString(dir1TrackedPaths, ";");
            r.r6 = CollectionsUtil.makeSortedString(dir2TrackedPaths, ";");
        }

    }

    @SneakyThrows
    private static Path createTempDir(String name) {
        return deleteOnExit(Files.createTempDirectory(name));
    }

    @SneakyThrows
    private static Path createTempDir(Path parent, String name) {
        return deleteOnExit(Files.createTempDirectory(parent, name));
    }

    @SneakyThrows
    private static Path createTempFile(Path parent, String name) {
        return deleteOnExit(Files.createTempFile(parent, name, ""));
    }

    private static Path deleteOnExit(Path path) {
        path.toFile().deleteOnExit();
        return path;
    }

    @SneakyThrows
    private static WatchService createWatchService() {
        return FileSystems.getDefault().newWatchService();
    }

    @SneakyThrows
    private static WatchServiceFSSubscriber createSubscriber() {
        return new DoNotTouchFilesSubscriber(new AcceptAllPathFilter(), new DoNothingTrigger(), watcher);
    }

    /**
     * Since jcstress runs many tests in parallel, the system cannot handle such a huge
     * number of file openings, that happen in the {@link WatchServiceFSSubscriber}.
     * This implementation replaces all calls to the file system with function calls
     * with known results. This should not affect the behaviour of the program.
     */
    private static class DoNotTouchFilesSubscriber extends WatchServiceFSSubscriber {

        public DoNotTouchFilesSubscriber(PathFilter pathFilter,
                                         FSEventTrigger trigger,
                                         WatchService watcher) throws IOException {
            super(pathFilter, trigger, watcher);
        }

        @Override
        protected boolean isDirectory(Path path) {
            return ALL_DIRS.contains(path);
        }

        @Override
        protected boolean pathExists(Path path) {
            return ALL_PATHS.contains(path);
        }

        @Override
        protected Stream<Path> listChildren(Path path) {
            if (path.equals(ROOT_DIR)) {
                return CollectionsUtil.createSet(DIR_1, DIR_2).stream();
            }
            if (path.equals(DIR_1)) {
                return CollectionsUtil.createSet(DIR_1_FILE_1, DIR_1_FILE_2).stream();
            }
            if (path.equals(DIR_2)) {
                return CollectionsUtil.createSet(DIR_2_FILE_1, DIR_2_FILE_2).stream();
            }
            return Stream.empty();
        }
    }
}
