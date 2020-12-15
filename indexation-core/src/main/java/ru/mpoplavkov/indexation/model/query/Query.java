package ru.mpoplavkov.indexation.model.query;

import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

/**
 * Query to retrieve data from the index.
 */
public interface Query {

    /**
     * Transforms all underlying terms of the query.
     *
     * @param termsTransformer specifies the transformation logic.
     * @return new query with transformations applied.
     */
    Query transform(TermsTransformer termsTransformer);

}
