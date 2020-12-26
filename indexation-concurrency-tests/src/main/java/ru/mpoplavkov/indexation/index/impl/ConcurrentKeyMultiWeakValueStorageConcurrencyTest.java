package ru.mpoplavkov.indexation.index.impl;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.LL_Result;
import org.openjdk.jcstress.infra.results.L_Result;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.util.CollectionsUtil;
import ru.mpoplavkov.indexation.util.ExecutorsUtil;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConcurrentKeyMultiWeakValueStorageConcurrencyTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";

    private static final String VALUE_1 = "value_1";
    private static final String VALUE_2 = "value_2";

    @JCStressTest
    @Description("The thread should read its own write from the storage")
    @Outcome(id = "value_1", expect = Expect.ACCEPTABLE)
    @State
    public static class ReadAfterWrite {

        KeyMultiValueStorage<String, String> storage = createStorage();

        @Actor
        public void actor(L_Result r) {
            storage.put(KEY_1, VALUE_1);
            r.r1 = CollectionsUtil.makeSortedString(storage.get(KEY_1));
        }

    }

    @JCStressTest
    @Description("Two threads should successfully concurrently write to the storage")
    @Outcome(id = "value_1, value_2", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentWriteTest {

        KeyMultiValueStorage<String, String> storage = createStorage(1);

        @Actor
        public void actor1() {
            storage.put(KEY_1, VALUE_1);
        }

        @Actor
        public void actor2() {
            storage.put(KEY_2, VALUE_2);
        }

        @Arbiter
        public void arbiter(LL_Result r) {
            r.r1 = CollectionsUtil.makeSortedString(storage.get(KEY_1));
            r.r2 = CollectionsUtil.makeSortedString(storage.get(KEY_2));
        }

    }

    @JCStressTest
    @Description("Two threads should successfully concurrently write values with the same key to the storage")
    @Outcome(id = "value_1;value_2", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentWriteWithTheSameKeyTest {

        KeyMultiValueStorage<String, String> storage = createStorage(1);

        @Actor
        public void actor1() {
            storage.put(KEY_1, VALUE_1);
        }

        @Actor
        public void actor2() {
            storage.put(KEY_1, VALUE_2);
        }

        @Arbiter
        public void arbiter(L_Result r) {
            r.r1 = CollectionsUtil.makeSortedString(storage.get(KEY_1), ";");
        }
    }

    @JCStressTest
    @Description("Two threads should successfully concurrently delete the same value from the storage")
    @Outcome(id = "", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentDeleteOfTheSameValueTest {

        KeyMultiValueStorage<String, String> storage = createStorage();

        {
            storage.put(KEY_1, VALUE_1);
        }

        @Actor
        public void actor1() {
            storage.delete(KEY_1, VALUE_1);
        }

        @Actor
        public void actor2() {
            storage.delete(KEY_1, VALUE_1);
        }

        @Arbiter
        public void arbiter(L_Result r) {
            r.r1 = CollectionsUtil.makeSortedString(storage.get(KEY_1));
        }
    }

    @JCStressTest
    @Description("Two threads should successfully concurrently delete different values from the storage")
    @Outcome(id = "", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentDeleteOfDifferentValuesTest {

        KeyMultiValueStorage<String, String> storage = createStorage();

        {
            storage.put(KEY_1, VALUE_1);
            storage.put(KEY_1, VALUE_2);
        }

        @Actor
        public void actor1() {
            storage.delete(KEY_1, VALUE_1);
        }

        @Actor
        public void actor2() {
            storage.delete(KEY_1, VALUE_2);
        }

        @Arbiter
        public void arbiter(L_Result r) {
            r.r1 = CollectionsUtil.makeSortedString(storage.get(KEY_1));
        }
    }

    private static <K, V> KeyMultiValueStorage<K, V> createStorage(int initialCapacity) {
        ScheduledExecutorService fakeScheduler =
                new ExecutorsUtil.FakeScheduledExecutorService();
        return new ConcurrentKeyMultiWeakValueStorage<>(fakeScheduler, 1, TimeUnit.DAYS, initialCapacity);
    }

    private static <K, V> KeyMultiValueStorage<K, V> createStorage() {
        return createStorage(16);
    }
}
