package ru.mpoplavkov.indexation.model.query;

import com.google.common.collect.ImmutableSet;
import lombok.Data;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

import java.util.Set;

@Data
public class QueryWithBinaryOperator implements Query {
    private final Query left;
    private final Query right;
    private final BinaryOperator operator;

    @Override
    public Query transform(TermsTransformer termsTransformer) {
        return new QueryWithBinaryOperator(
                left.transform(termsTransformer),
                right.transform(termsTransformer),
                operator
        );
    }

    @Override
    public Set<Term> allUnderlyingTerms() {
        return ImmutableSet.<Term>builder()
                .addAll(left.allUnderlyingTerms())
                .addAll(right.allUnderlyingTerms())
                .build();
    }
}
