package ru.mpoplavkov.indexation.text.transformer.impl;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

/**
 * Converts terms to lowercase.
 */
public class LowercaseTransformer implements TermsTransformer {
    @Override
    public Term transform(Term term) {
        if (term instanceof WordTerm) {
            WordTerm word = (WordTerm) term;
            return new WordTerm(word.getWord().toLowerCase());
        }
        return term;
    }
}
