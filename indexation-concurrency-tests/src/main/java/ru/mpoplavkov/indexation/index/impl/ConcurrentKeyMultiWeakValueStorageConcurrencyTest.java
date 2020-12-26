package ru.mpoplavkov.indexation.index.impl;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.IIII_Result;
import org.openjdk.jcstress.infra.results.I_Result;
import ru.mpoplavkov.indexation.index.KeyMultiValueStorage;
import ru.mpoplavkov.indexation.util.ExecutorsUtil;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConcurrentKeyMultiWeakValueStorageConcurrencyTest {

    private static final ScheduledExecutorService scheduler =
            new ExecutorsUtil.FakeScheduledExecutorService();

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";

    private static final Integer VALUE_1 = 55;
    private static final Integer VALUE_2 = 66;

    @JCStressTest
    @Description("The thread should read its own write from the storage")
    @Outcome(id = "55", expect = Expect.ACCEPTABLE, desc = "The only acceptable outcome.")
    @State
    public static class ReadAfterWrite {

        KeyMultiValueStorage<String, Integer> storage =
                new ConcurrentKeyMultiWeakValueStorage<>(scheduler, 1, TimeUnit.DAYS, 16);

        @Actor
        public void actor(I_Result r) {
            storage.put(KEY_1, VALUE_1);
            r.r1 = storage.get(KEY_1).iterator().next();
        }

    }

    @JCStressTest
    @Description("Two threads should successfully concurrently write to the storage")
    @Outcome(id = "1, 1, 55, 66", expect = Expect.ACCEPTABLE, desc = "The only acceptable outcome.")
    @State
    public static class ConcurrentWriteTest {

        KeyMultiValueStorage<String, Integer> storage =
                new ConcurrentKeyMultiWeakValueStorage<>(scheduler, 1, TimeUnit.DAYS, 1);

        @Actor
        public void actor1() {
            storage.put(KEY_1, VALUE_1);
        }

        @Actor
        public void actor2() {
            storage.put(KEY_2, VALUE_2);
        }

        @Arbiter
        public void arbiter(IIII_Result r) {
            Set<Integer> set1 = storage.get(KEY_1);
            Set<Integer> set2 = storage.get(KEY_2);

            r.r1 = set1.size();
            r.r2 = set2.size();
            r.r3 = set1.iterator().next();
            r.r4 = set2.iterator().next();
        }

    }
}
