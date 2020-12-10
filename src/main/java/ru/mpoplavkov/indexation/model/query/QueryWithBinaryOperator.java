package ru.mpoplavkov.indexation.model.query;

import lombok.Data;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

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
}
