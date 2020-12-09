package ru.mpoplavkov.indexation.model.query;

import lombok.Data;

@Data
public class QueryWithUnaryOperator {
    private final Query query;
    private final UnaryOperator operator;
}
