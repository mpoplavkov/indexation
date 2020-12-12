package ru.mpoplavkov.indexation.text.transformer.impl;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

/**
 * Doesn't transform terms in any way.
 */
public class IdTermsTransformer implements TermsTransformer {

    /**
     * Returns the same term.
     *
     * @param term the term to transform.
     * @return the same term.
     */
    @Override
    public Term transform(Term term) {
        return term;
    }
}
