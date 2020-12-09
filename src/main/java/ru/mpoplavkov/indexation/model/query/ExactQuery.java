package ru.mpoplavkov.indexation.model.query;

import lombok.Data;
import ru.mpoplavkov.indexation.model.term.Term;

@Data
public class ExactQuery implements Query {

    private final Term term;

}
