package ru.mpoplavkov.indexation.text.extractor;

import ru.mpoplavkov.indexation.model.term.Term;

public interface TermsExtractor {

    /**
     * Extracts terms from the given string.
     *
     * @param s given string.
     * @return extracted terms.
     */
    Iterable<Term> extractTerms(String s);
}
