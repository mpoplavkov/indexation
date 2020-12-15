package ru.mpoplavkov.indexation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;
import ru.mpoplavkov.indexation.service.impl.FileSystemIndexServiceImpl;
import ru.mpoplavkov.indexation.text.extractor.TermsExtractor;
import ru.mpoplavkov.indexation.text.extractor.impl.SplitBySpaceTermsExtractor;

import java.io.IOException;

@Configuration
public class AppConfiguration {

    @Bean(destroyMethod = "close")
    public FileSystemIndexService fsIndexService() throws IOException {
        TermsExtractor extractor = new SplitBySpaceTermsExtractor();
        return new FileSystemIndexServiceImpl(extractor, 2);
    }

}
