package ru.mpoplavkov.indexation.model.query;

import com.google.common.collect.ImmutableSet;
import lombok.Data;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

import java.util.HashSet;
import java.util.Set;

@Data
public class ExactTerm implements Query {

    private final Term term;

    @Override
    public Query transform(TermsTransformer termsTransformer) {
        Term newTerm = termsTransformer.transform(term);
        return new ExactTerm(newTerm);
    }

    @Override
    public ImmutableSet<Term> allUnderlyingTerms() {
        return ImmutableSet.of(term);
    }
}
