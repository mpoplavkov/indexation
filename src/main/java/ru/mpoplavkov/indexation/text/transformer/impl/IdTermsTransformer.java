package ru.mpoplavkov.indexation.text.transformer.impl;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

public class IdTermsTransformer implements TermsTransformer {
    @Override
    public Term transform(Term term) {
        return term;
    }
}
