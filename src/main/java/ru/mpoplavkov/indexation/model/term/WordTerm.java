package ru.mpoplavkov.indexation.model.term;

import lombok.Data;

@Data
public class WordTerm implements Term {

    private final String word;

}
