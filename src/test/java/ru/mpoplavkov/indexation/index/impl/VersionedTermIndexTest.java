package ru.mpoplavkov.indexation.index.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mpoplavkov.indexation.index.TermIndex;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.model.term.WordTerm;

import java.util.Set;
import java.util.function.Predicate;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.mpoplavkov.indexation.TestUtils.createSet;

class VersionedTermIndexTest {

    Predicate<String> valueIsActualPredicate = mock(Predicate.class);
    TermIndex<String> index;

    String value1 = "value1";
    String value2 = "value2";
    String value3 = "value3";

    String term1Word = "term1";
    Term term1 = new WordTerm(term1Word);
    Term term2 = new WordTerm("term2");
    Term term3 = new WordTerm("term3");

    @BeforeEach
    public void init() {
        index = new VersionedTermIndex<String>() {
            @Override
            protected boolean valueIsActual(String value) {
                return valueIsActualPredicate.test(value);
            }
        };
    }

    // TODO: generify tests ?

    @Test
    public void shouldFindIndexedValueWithSimpleQuery() {
        when(valueIsActualPredicate.test(value1)).thenReturn(true);
        index.index(value1, createSet(term1));
        assertEquals(createSet(value1), searchByTerm(term1));
    }

    @Test
    public void shouldFindIndexedValueByAnotherInstanceOfTheSameTerm() {
        when(valueIsActualPredicate.test(value1)).thenReturn(true);
        index.index(value1, createSet(term1));
        Term sameTerm = new WordTerm(term1Word);
        assertEquals(createSet(value1), searchByTerm(sameTerm));
    }

    @Test
    public void shouldFindSeveralIndexedValuesWithSimpleQuery() {
        when(valueIsActualPredicate.test(value1)).thenReturn(true);
        when(valueIsActualPredicate.test(value2)).thenReturn(true);
        index.index(value1, createSet(term1));
        index.index(value2, createSet(term1));
        assertEquals(createSet(value1, value2), searchByTerm(term1));
    }

    @Test
    public void shouldNotFindAnythingInAnEmptyIndex() {
        assertEquals(createSet(), searchByTerm(term1));
    }

    @Test
    public void shouldNotFindDeletedValue() {
        index.index(value1, createSet(term1));
        index.delete(value1);
        assertEquals(createSet(), searchByTerm(term1));
    }

    @Test
    public void shouldFindRecreatedValue() {
        when(valueIsActualPredicate.test(value1)).thenReturn(true);
        index.index(value1, createSet(term1));
        index.delete(value1);
        index.index(value1, createSet(term1));
        assertEquals(createSet(value1), searchByTerm(term1));
    }

    @Test
    public void shouldReindexValue() {
        when(valueIsActualPredicate.test(value1)).thenReturn(true);
        index.index(value1, createSet(term1));
        index.index(value1, createSet(term2));

        Assertions.assertAll(
                () -> assertEquals(createSet(), searchByTerm(term1)),
                () -> assertEquals(createSet(value1), searchByTerm(term2))
        );
    }

    @Test
    public void shouldNotFindNonActualValues() {
        when(valueIsActualPredicate.test(value1)).thenReturn(false);
        index.index(value1, createSet(term1));
        assertEquals(createSet(), searchByTerm(term1));
    }

    @Test
    public void shouldFindOnlyActualValues() {
        when(valueIsActualPredicate.test(value1)).thenReturn(false);
        when(valueIsActualPredicate.test(value2)).thenReturn(true);
        index.index(value1, createSet(term1));
        index.index(value2, createSet(term1));
        assertEquals(createSet(value2), searchByTerm(term1));
    }

    private Set<String> searchByTerm(Term term) {
        Query query = new ExactTerm(term);
        return index.search(query);
    }

}
