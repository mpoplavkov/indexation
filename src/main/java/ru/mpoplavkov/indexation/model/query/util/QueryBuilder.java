package ru.mpoplavkov.indexation.model.query.util;

import ru.mpoplavkov.indexation.model.query.*;
import ru.mpoplavkov.indexation.model.term.Term;

/**
 * Helper class to add handy methods for query construction.
 */
public final class QueryBuilder {

    private QueryBuilder() {
    }

    public static Query exact(Term term) {
        return new ExactTerm(term);
    }

    public static Query and(Query left, Query right) {
        return new QueryWithBinaryOperator(left, right, BinaryOperator.AND);
    }

    public static Query or(Query left, Query right) {
        return new QueryWithBinaryOperator(left, right, BinaryOperator.OR);
    }

    public static Query not(Query query) {
        return new QueryWithUnaryOperator(query, UnaryOperator.NOT);
    }
}
