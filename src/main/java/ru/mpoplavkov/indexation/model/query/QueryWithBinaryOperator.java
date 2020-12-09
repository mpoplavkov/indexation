package ru.mpoplavkov.indexation.model.query;

import lombok.Data;

@Data
public class QueryWithBinaryOperator {
    private final Query left;
    private final Query right;
    private final BinaryOperator operator;
}
