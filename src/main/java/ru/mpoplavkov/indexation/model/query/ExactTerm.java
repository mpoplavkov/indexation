package ru.mpoplavkov.indexation.model.query;

import lombok.Data;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.text.transformer.TermsTransformer;

@Data
public class ExactTerm implements Query {

    private final Term term;

    @Override
    public Query transform(TermsTransformer termsTransformer) {
        Term newTerm = termsTransformer.transform(term);
        return new ExactTerm(newTerm);
    }
}
