package ru.mpoplavkov.indexation.text.transformer;

import ru.mpoplavkov.indexation.model.term.Term;

public interface TermsTransformer {

    Term transform(Term term);

}
