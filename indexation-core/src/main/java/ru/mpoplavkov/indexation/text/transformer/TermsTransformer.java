package ru.mpoplavkov.indexation.text.transformer;

import ru.mpoplavkov.indexation.model.term.Term;

public interface TermsTransformer {

    /**
     * Specifies how to transform the term.
     *
     * @param term the term to transform.
     * @return the transformed term.
     */
    Term transform(Term term);

}
