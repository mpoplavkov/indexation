package ru.mpoplavkov.indexation.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

@RestController
public class SimpleIndexationController {

    @Autowired
    private FileSystemIndexService fileSystemIndexService;

    @GetMapping("/search")
    public Set<Path> search(@RequestParam(value = "word") String word) {
        Query query = new ExactTerm(new WordTerm(word));
        return fileSystemIndexService.search(query);
    }

    @PostMapping("/register")
    public void register(@RequestParam(value = "path") String path) throws IOException {
        File file = new File(path);
        fileSystemIndexService.addToIndex(file.toPath());
    }
}
