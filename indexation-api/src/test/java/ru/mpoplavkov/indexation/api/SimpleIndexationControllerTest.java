package ru.mpoplavkov.indexation.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Path;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimpleIndexationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRespondOnSearch() throws Exception {
        mockMvc.perform(get("/search?word=a"))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo("[]")));

    }

    @Test
    void shouldRespondOnSubscribe(@TempDir Path dir) throws Exception {
        mockMvc.perform(post(String.format("/subscribe?path=%s", dir.toAbsolutePath())))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRespondWithBadRequestOnSubscribeToANonExistentPath() throws Exception {
        Path path = new File("non existent").toPath();
        mockMvc.perform(post(String.format("/subscribe?path=%s", path.toAbsolutePath())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRespondOnUnsubscribe(@TempDir Path dir) throws Exception {
        mockMvc.perform(post(String.format("/unsubscribe?path=%s", dir.toAbsolutePath())))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRespondWithBadRequestOnUnsubscribeFromANonExistentPath() throws Exception {
        Path path = new File("non existent").toPath();
        mockMvc.perform(post(String.format("/unsubscribe?path=%s", path.toAbsolutePath())))
                .andExpect(status().isBadRequest());
    }

}
