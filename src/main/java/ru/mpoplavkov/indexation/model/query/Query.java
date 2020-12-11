package ru.mpoplavkov.indexation.model.query;

import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

public interface Query {

    Query transform(TermsTransformer termsTransformer);

}
