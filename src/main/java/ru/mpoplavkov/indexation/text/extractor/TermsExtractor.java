package ru.mpoplavkov.indexation.text.extractor;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.source.Source;

public interface TermsExtractor {

    /**
     * Extracts terms from the given source.
     *
     * @param s given source.
     * @return extracted terms.
     */
    Iterable<Term> extractTerms(Source s);
}
