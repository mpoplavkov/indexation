package ru.mpoplavkov.indexation.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mpoplavkov.indexation.model.query.ExactTerm;
import ru.mpoplavkov.indexation.model.query.Query;
import ru.mpoplavkov.indexation.model.term.WordTerm;
import ru.mpoplavkov.indexation.service.FileSystemIndexService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
public class SimpleIndexationController {

    @ExceptionHandler({FileNotFoundException.class})
    public ResponseEntity<String> handleFileNotFoundException(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler({IOException.class})
    public ResponseEntity<String> handleIOException(Exception e) {
        return new ResponseEntity<>(INTERNAL_SERVER_ERROR);
    }

    @Autowired
    private FileSystemIndexService fileSystemIndexService;

    @GetMapping("/search")
    public Set<Path> search(@RequestParam(value = "word") String word) {
        Query query = new ExactTerm(new WordTerm(word));
        return fileSystemIndexService.search(query);
    }

    @PostMapping("/subscribe")
    public void subscribe(@RequestParam(value = "path") String path) throws IOException {
        File file = new File(path);
        fileSystemIndexService.addToIndex(file.toPath());
    }
}
