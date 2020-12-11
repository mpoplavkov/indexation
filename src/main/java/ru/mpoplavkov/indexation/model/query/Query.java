package ru.mpoplavkov.indexation.model.query;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

import java.util.Set;

public interface Query {

    Query transform(TermsTransformer termsTransformer);

    Set<Term> allUnderlyingTerms();

}
