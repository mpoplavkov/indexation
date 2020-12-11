package ru.mpoplavkov.indexation.model.query;

import lombok.Data;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

import java.util.Set;

@Data
public class QueryWithUnaryOperator implements Query {
    private final Query query;
    private final UnaryOperator operator;

    @Override
    public Query transform(TermsTransformer termsTransformer) {
        return new QueryWithUnaryOperator(
                query.transform(termsTransformer),
                operator
        );
    }

    @Override
    public Set<Term> allUnderlyingTerms() {
        return query.allUnderlyingTerms();
    }
}
