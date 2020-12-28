package ru.mpoplavkov.indexation.listener.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.LLLLLL_Result;
import org.openjdk.jcstress.infra.results.LLLLL_Result;
import org.openjdk.jcstress.infra.results.LLLL_Result;
import ru.mpoplavkov.indexation.filter.PathFilter;
import ru.mpoplavkov.indexation.filter.impl.AcceptAllPathFilter;
import ru.mpoplavkov.indexation.model.fs.FileSystemEvent;
import ru.mpoplavkov.indexation.trigger.FSEventTrigger;
import ru.mpoplavkov.indexation.trigger.impl.DoNothingTrigger;
import ru.mpoplavkov.indexation.util.CollectionsUtil;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WatchServiceFsSubscriberConcurrencyTest {

    // WatchService creates an internal executor service.
    // To not overflow the system with threads, reuse the same one among all
    // subscribers.
    private static final WatchService watcher = createWatchService();

    private static final Path ROOT_DIR = createTempDir("root_dir");
    private static final Path DIR_1 = createTempDir(ROOT_DIR, "1d");
    private static final Path DIR_1_FILE_1 = createTempFile(DIR_1, "11f");
    private static final Path DIR_1_FILE_2 = createTempFile(DIR_1, "12f");
    private static final Path DIR_2 = createTempDir(ROOT_DIR, "2d");
    private static final Path DIR_2_FILE_1 = createTempFile(DIR_2, "21f");
    private static final Path DIR_2_FILE_2 = createTempFile(DIR_2, "22f");

    private static final Set<Path> ALL_DIRS =
            CollectionsUtil.createSet(ROOT_DIR, DIR_1, DIR_2);
    private static final Set<Path> ALL_PATHS =
            CollectionsUtil.createSet(ROOT_DIR, DIR_1, DIR_2, DIR_1_FILE_1, DIR_1_FILE_2, DIR_2_FILE_1, DIR_2_FILE_2);

    @JCStressTest
    @Description("Two threads could concurrently subscribe to different directories")
    @Outcome(id = "11f;12f, 21f;22f, 11f;12f;1d;21f;22f;2d, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnDifferentDirsTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

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
        public void arbiter(LLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedPaths(subscriber, DIR_2);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to the same directory")
    @Outcome(id = "11f;12f, 11f;12f;1d, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnTheSameDirTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_1);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to different directories one of which is a subdirectory of another")
    @Outcome(id = "1d;2d, 11f;12f, 21f;22f, 11f;12f;1d;21f;22f;2d;root_dir, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnSubDirsTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(ROOT_DIR);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_1);
        }

        @Arbiter
        public void arbiter(LLLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, ROOT_DIR);
            r.r2 = prettyTrackedPaths(subscriber, DIR_1);
            r.r3 = prettyTrackedPaths(subscriber, DIR_2);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r6 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to the directory and the file")
    @Outcome(id = "11f;12f, 21f, 11f;12f;1d;21f, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnTheDirAndTheFileTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_2_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedPaths(subscriber, DIR_2);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to the directory and one of its files")
    @Outcome(id = "11f;12f, 11f;12f;1d, , ", expect = Expect.ACCEPTABLE)
    @Outcome(id = "11f;12f, 11f;11f;12f;1d, , ", expect = Expect.ACCEPTABLE, desc = "the file was indexed twice")
    @State
    public static class ConcurrentSubscriptionOnTheDirAndItsFileTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_1_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to different files")
    @Outcome(id = "11f, 21f, 11f;21f, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnDifferentFilesTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1_FILE_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_2_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedPaths(subscriber, DIR_2);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to the same file")
    @Outcome(id = "11f, 11f, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnTheSameFileTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1_FILE_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_1_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to different files from the same directory")
    @Outcome(id = "11f;12f, 11f;12f, , ", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionOnDifferentFilesFromTheSameDirectoryTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.subscribe(DIR_1_FILE_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_1_FILE_2);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from the same directory")
    @Outcome(id = ", 11f;12f;1d, , 11f;12f;1d", expect = Expect.ACCEPTABLE)
    @Outcome(id = ", 11f;12f;1d, , 11f;12f;1d;1d", expect = Expect.ACCEPTABLE, desc = "delete directory twice")
    @State
    public static class ConcurrentUnsubscriptionFromTheSameDirTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_1);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from different directories")
    @Outcome(id = ", , 11f;12f;1d;21f;22f;2d, , 11f;12f;1d;21f;22f;2d", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentUnsubscriptionFromDifferentDirsTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1);
                subscriber.subscribe(DIR_2);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_2);
        }

        @Arbiter
        public void arbiter(LLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedPaths(subscriber, DIR_2);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from the directory and its subdirectory")
    @Outcome(id = ", , , 11f;12f;1d;21f;22f;2d;root_dir, , 11f;12f;1d;21f;22f;2d;root_dir", expect = Expect.ACCEPTABLE)
    @Outcome(id = ", , , 11f;12f;1d;21f;22f;2d;root_dir, , 11f;12f;1d;1d;21f;22f;2d;root_dir", expect = Expect.ACCEPTABLE, desc = "delete directory twice")
    @State
    public static class ConcurrentUnsubscriptionFromTheDirAndItsSubdirTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(ROOT_DIR);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(ROOT_DIR);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_1);
        }

        @Arbiter
        public void arbiter(LLLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, ROOT_DIR);
            r.r2 = prettyTrackedPaths(subscriber, DIR_1);
            r.r3 = prettyTrackedPaths(subscriber, DIR_2);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r6 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from the same file")
    @Outcome(id = ", 11f, , 11f", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentUnsubscriptionFromTheSameFileTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1_FILE_1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1_FILE_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_1_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from different files")
    @Outcome(id = ", , 11f;21f, , 11f;21f", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentUnsubscriptionFromDifferentFilesTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1_FILE_1);
                subscriber.subscribe(DIR_2_FILE_1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1_FILE_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_2_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedPaths(subscriber, DIR_2);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from different files from the same directory")
    @Outcome(id = ", 11f;12f;1d, , 11f;12f", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentUnsubscriptionFromDifferentFilesFromTheSameDirTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1_FILE_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_1_FILE_2);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently unsubscribe from the directory and one of its files")
    @Outcome(id = ", 11f;12f;1d, , 11f;12f;1d", expect = Expect.ACCEPTABLE)
    @Outcome(id = ", 11f;12f;1d, , 11f;11f;12f;1d", expect = Expect.ACCEPTABLE, desc = "file was deleted twice")
    @State
    public static class ConcurrentUnsubscriptionFromTheDirectoryAndItsFileTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.unsubscribe(DIR_1_FILE_1);
        }

        @Arbiter
        public void arbiter(LLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently subscribe to and unsubscribe from some paths")
    @Outcome(id = ", 21f;22f, 11f;12f;1d;21f;22f;2d, , 11f;12f;1d", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSubscriptionAndUnsubscriptionTest {

        List<FileSystemEvent> trackedEvents = new CopyOnWriteArrayList<>();
        WatchServiceFSSubscriber subscriber = createSubscriber(trackedEvents);

        {
            try {
                subscriber.subscribe(DIR_1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Actor
        @SneakyThrows
        public void actor1() {
            subscriber.unsubscribe(DIR_1);
        }

        @Actor
        @SneakyThrows
        public void actor2() {
            subscriber.subscribe(DIR_2);
        }

        @Arbiter
        public void arbiter(LLLLL_Result r) {
            Map<FileSystemEvent.Kind, String> prettyTrackedEvents =
                    prettyResultsFromTrackedEvents(trackedEvents);

            r.r1 = prettyTrackedPaths(subscriber, DIR_1);
            r.r2 = prettyTrackedPaths(subscriber, DIR_2);
            r.r3 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_CREATE);
            r.r4 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_MODIFY);
            r.r5 = prettyTrackedEvents.get(FileSystemEvent.Kind.ENTRY_DELETE);
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
    private static WatchServiceFSSubscriber createSubscriber(List<FileSystemEvent> trackedEvents) {
        return new DoNotTouchFilesSubscriber(new AcceptAllPathFilter(), new TrackedTrigger(trackedEvents), watcher);
    }

    @SneakyThrows
    private static WatchServiceFSSubscriber createSubscriber() {
        return new DoNotTouchFilesSubscriber(new AcceptAllPathFilter(), new DoNothingTrigger(), watcher);
    }

    private static String getFileNameFromTmpPath(Path path) {
        return path.toFile().getName().replaceFirst("\\d*$", "");
    }

    private static String prettyTrackedPaths(WatchServiceFSSubscriber subscriber, Path path) {
        Set<String> pathTrackedPaths = subscriber.trackedPaths.getOrDefault(path, Collections.emptySet())
                .stream()
                .map(WatchServiceFsSubscriberConcurrencyTest::getFileNameFromTmpPath)
                .collect(Collectors.toSet());
        return CollectionsUtil.makeSortedString(pathTrackedPaths, ";");
    }

    private static Map<FileSystemEvent.Kind, String> prettyResultsFromTrackedEvents(List<FileSystemEvent> trackedEvents) {
        Map<FileSystemEvent.Kind, String> result = new HashMap<>();
        // defaults
        result.put(FileSystemEvent.Kind.ENTRY_CREATE, "");
        result.put(FileSystemEvent.Kind.ENTRY_MODIFY, "");
        result.put(FileSystemEvent.Kind.ENTRY_DELETE, "");


        Map<FileSystemEvent.Kind, List<FileSystemEvent>> kindsToPaths = trackedEvents
                .stream()
                .collect(Collectors.groupingBy(FileSystemEvent::getKind));

        kindsToPaths.forEach((kind, paths) -> {
            List<String> pathNames = paths.stream()
                    .map(FileSystemEvent::getEntry)
                    .map(WatchServiceFsSubscriberConcurrencyTest::getFileNameFromTmpPath)
                    .collect(Collectors.toList());
            result.put(kind, CollectionsUtil.makeSortedString(pathNames, ";"));
        });

        return result;
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

    @RequiredArgsConstructor
    private static class TrackedTrigger implements FSEventTrigger {
        private final List<FileSystemEvent> trackedEvents;

        @Override
        public void onEvent(FileSystemEvent fileSystemEvent) {
            trackedEvents.add(fileSystemEvent);
        }
    }
}
