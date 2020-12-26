package ru.mpoplavkov.indexation.text.extractor.impl;

import org.junit.jupiter.api.Test;
import ru.mpoplavkov.indexation.model.term.Term;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.source.Source;
import ru.mpoplavkov.indexation.text.source.impl.StringSource;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.mpoplavkov.indexation.util.CollectionsUtil.createSet;

class SplitBySpaceTermsExtractorTest {

    TermsExtractor extractor = new SplitBySpaceTermsExtractor();

    @Test
    public void shouldNotExtractAnythingForAnEmptySource() throws IOException {
        Source source = sourceFromString("");
        assertEquals(createSet(), extractor.extractTerms(source));
    }

    @Test
    public void shouldNotExtractAnythingForWhitespaceString() throws IOException {
        Source source = sourceFromString(" \t\n\r  ");
        assertEquals(createSet(), extractor.extractTerms(source));
    }

    @Test
    public void shouldExtractWordsDElimitedByWhitespace() throws IOException {
        Source source = sourceFromString(" word1  \t word2 \n   word3 \r  ");
        Set<Term> expected = createSet(new WordTerm("word1"), new WordTerm("word2"), new WordTerm("word3"));
        assertEquals(expected, extractor.extractTerms(source));
    }

    private Source sourceFromString(String s) {
        return new StringSource(s);
    }

}
