package ru.mpoplavkov.indexation.index.impl;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.LLL_Result;
import org.openjdk.jcstress.infra.results.LL_Result;
import org.openjdk.jcstress.infra.results.L_Result;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.util.CollectionsUtil;
import ru.mpoplavkov.indexation.util.ExecutorsUtil;

public class VersionedTermIndexConcurrencyTest {

    private static final String VALUE_1 = "value_1";
    private static final String VALUE_2 = "value_2";

    private static final Term TERM_1 = new WordTerm("term1");
    private static final Term TERM_2 = new WordTerm("term2");
    private static final Term TERM_3 = new WordTerm("term3");

    @JCStressTest
    @Description("Two threads could concurrently index different values.")
    @Outcome(id = "\\[value_1\\], \\[value_1, value_2\\], \\[value_2\\]", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentIndexOfDifferentValuesTest {

        TermIndex<String> index = createIndex();

        @Actor
        public void actor1() {
            index.index(VALUE_1, CollectionsUtil.createSet(TERM_1, TERM_2));
        }

        @Actor
        public void actor2() {
            index.index(VALUE_2, CollectionsUtil.createSet(TERM_2, TERM_3));
        }

        @Arbiter
        public void arbiter(LLL_Result r) {
            r.r1 = index.search(new ExactTerm(TERM_1));
            r.r2 = index.search(new ExactTerm(TERM_2));
            r.r3 = index.search(new ExactTerm(TERM_3));
        }

    }

    @JCStressTest
    @Description("Two threads could concurrently index the same value. Only one of the results should be visible")
    @Outcome(id = "\\[value_1\\], \\[value_1\\], \\[\\]", expect = Expect.ACCEPTABLE, desc = "If the value indexed by the actor1 is visible")
    @Outcome(id = "\\[\\], \\[value_1\\], \\[value_1\\]", expect = Expect.ACCEPTABLE, desc = "If the value indexed by the actor2 is visible")
    @State
    public static class ConcurrentIndexOfTheSameValueTest {

        TermIndex<String> index = createIndex();

        @Actor
        public void actor1() {
            index.index(VALUE_1, CollectionsUtil.createSet(TERM_1, TERM_2));
        }

        @Actor
        public void actor2() {
            index.index(VALUE_1, CollectionsUtil.createSet(TERM_2, TERM_3));
        }

        @Arbiter
        public void arbiter(LLL_Result r) {
            r.r1 = index.search(new ExactTerm(TERM_1));
            r.r2 = index.search(new ExactTerm(TERM_2));
            r.r3 = index.search(new ExactTerm(TERM_3));
        }

    }

    @JCStressTest
    @Description("Two threads could concurrently delete the same value")
    @Outcome(id = "\\[\\]", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentDeleteValueTest {

        TermIndex<String> index = createIndex();

        {
            index.index(VALUE_1, CollectionsUtil.createSet(TERM_1));
        }

        @Actor
        public void actor1() {
            index.delete(VALUE_1);
        }

        @Actor
        public void actor2() {
            index.delete(VALUE_1);
        }

        @Arbiter
        public void arbiter(L_Result r) {
            r.r1 = index.search(new ExactTerm(TERM_1));
        }
    }

    @JCStressTest
    @Description("Two threads could concurrently delete different values")
    @Outcome(id = "\\[\\]", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentDeleteSeveralValuesTest {

        TermIndex<String> index = createIndex();

        {
            index.index(VALUE_1, CollectionsUtil.createSet(TERM_1));
            index.index(VALUE_2, CollectionsUtil.createSet(TERM_1));
        }

        @Actor
        public void actor1() {
            index.delete(VALUE_1);
        }

        @Actor
        public void actor2() {
            index.delete(VALUE_2);
        }

        @Arbiter
        public void arbiter(L_Result r) {
            r.r1 = index.search(new ExactTerm(TERM_1));
        }
    }

    @JCStressTest
    @Description("Two threads should retrieve the same result during the search")
    @Outcome(id = "\\[value_1\\], \\[value_1\\]", expect = Expect.ACCEPTABLE)
    @State
    public static class ConcurrentSearchTest {

        TermIndex<String> index = createIndex();

        {
            index.index(VALUE_1, CollectionsUtil.createSet(TERM_1));
        }

        @Actor
        public void actor1(LL_Result r) {
            r.r1 = index.search(new ExactTerm(TERM_1));
        }

        @Actor
        public void actor2(LL_Result r) {
            r.r2 = index.search(new ExactTerm(TERM_1));
        }
    }

    private static <T> TermIndex<T> createIndex() {
        return new VersionedTermIndex<>(new ExecutorsUtil.FakeScheduledExecutorService());
    }
}
