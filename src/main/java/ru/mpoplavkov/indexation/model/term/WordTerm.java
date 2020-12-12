package ru.mpoplavkov.indexation.model.term;

import com.google.common.base.Preconditions;
import lombok.Data;

/**
 * One nonempty word.
 */
@Data
public class WordTerm implements Term {

    private final String word;

    public WordTerm(String word) {
        Preconditions.checkArgument(!word.isEmpty(), "Word should not be empty");
        Preconditions.checkArgument(!word.contains("\\s"), "Word should not contain whitespace symbols");

        this.word = word;
    }

}
