package ru.mpoplavkov.indexation.text.extractor.impl;

import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.Source;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts single words by splitting incoming strings by whitespaces.
 */
public class SplitBySpaceTermsExtractor implements TermsExtractor {
    @Override
    public Set<Term> extractTerms(Source s) throws IOException {
        try (Stream<String> lines = s.lines()) {
            return lines
                    .flatMap(str -> Arrays.stream(str.split("\\s")))
                    .filter(str -> !str.isEmpty())
                    .map(WordTerm::new)
                    .collect(Collectors.toSet());
        }
    }
}
