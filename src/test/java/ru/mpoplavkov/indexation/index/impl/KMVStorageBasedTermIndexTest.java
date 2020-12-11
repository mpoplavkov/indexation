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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.mpoplavkov.indexation.TestUtils.createSet;
import static ru.mpoplavkov.indexation.model.query.util.QueryBuilder.*;

class KMVStorageBasedTermIndexTest {

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
        index = new KMVStorageBasedTermIndex<>();
    }

    // TODO: generify tests ?

    @Test
    public void shouldFindIndexedValueWithSimpleQuery() {
        index.index(value1, createSet(term1));
        assertEquals(createSet(value1), searchByTerm(term1));
    }

    @Test
    public void shouldFindIndexedValueByAnotherInstanceOfTheSameTerm() {
        index.index(value1, createSet(term1));
        Term sameTerm = new WordTerm(term1Word);
        assertEquals(createSet(value1), searchByTerm(sameTerm));
    }

    @Test
    public void shouldFindSeveralIndexedValuesWithSimpleQuery() {
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
        index.index(value1, createSet(term1));
        index.delete(value1);
        index.index(value1, createSet(term1));
        assertEquals(createSet(value1), searchByTerm(term1));
    }

    @Test
    public void shouldReindexValue() {
        index.index(value1, createSet(term1));
        index.index(value1, createSet(term2));

        Assertions.assertAll(
                () -> assertEquals(createSet(), searchByTerm(term1)),
                () -> assertEquals(createSet(value1), searchByTerm(term2))
        );
    }

    @Test
    public void shouldSearchByQueryWithOperatorAND() {
        index.index(value1, createSet(term1, term2));
        index.index(value2, createSet(term1));
        index.index(value3, createSet(term2));

        Query andQuery = and(exact(term1), exact(term2));
        Set<String> actual = index.search(andQuery);
        assertEquals(createSet(value1), actual);
    }

    @Test
    public void shouldNotFindAnythingByANDQueryWithMissedTerm() {
        index.index(value1, createSet(term1, term2));
        index.index(value2, createSet(term1));
        index.index(value3, createSet(term2));

        Query andQuery = and(exact(term1), exact(term3));
        Set<String> actual = index.search(andQuery);
        assertEquals(createSet(), actual);
    }

    @Test
    public void shouldSearchByQueryWithOperatorOR() {
        index.index(value1, createSet(term1, term2));
        index.index(value2, createSet(term1));
        index.index(value3, createSet(term2));

        Query orQuery = or(exact(term1), exact(term2));
        Set<String> actual = index.search(orQuery);
        assertEquals(createSet(value1, value2, value3), actual);
    }

    @Test
    public void shouldFindValuesEvenIfThePartOfORQueryIsMissed() {
        index.index(value1, createSet(term1, term2));
        index.index(value2, createSet(term1));
        index.index(value3, createSet(term2));

        Query orQuery = or(exact(term1), exact(term3));
        Set<String> actual = index.search(orQuery);
        assertEquals(createSet(value1, value2), actual);
    }

    @Test
    public void shouldSearchByQueryWithOperatorNOT() {
        index.index(value1, createSet(term1, term2));
        index.index(value2, createSet(term1));
        index.index(value3, createSet(term2));

        Query notQuery = not(exact(term1));
        Set<String> actual = index.search(notQuery);
        assertEquals(createSet(value3), actual);
    }

    @Test
    public void shouldSearchByComplexQuery() {
        index.index(value1, createSet(term1, term2));
        index.index(value2, createSet(term1, term3));
        index.index(value3, createSet(term2, term3));

        Query complexQuery = or(not(exact(term1)), and(exact(term1), exact(term2)));
        Set<String> actual = index.search(complexQuery);
        assertEquals(createSet(value1, value3), actual);
    }

    private Set<String> searchByTerm(Term term) {
        Query query = new ExactTerm(term);
        return index.search(query);
    }

}
