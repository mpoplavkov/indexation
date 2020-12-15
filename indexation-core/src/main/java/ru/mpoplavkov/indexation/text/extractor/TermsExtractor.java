package ru.mpoplavkov.indexation.text.extractor;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.source.Source;

import java.io.IOException;
import java.util.Set;

public interface TermsExtractor {

    /**
     * Extracts terms from the given source.
     *
     * @param s given source.
     * @return extracted terms.
     */
    Set<Term> extractTerms(Source s) throws IOException;
}
