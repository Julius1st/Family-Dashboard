package com.familydashboard.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end proof that the Todo REST API works against the real repository
 * and H2 datasource (not mocks) — {@code @SpringBootTest} loads the full
 * application context, unlike {@link TodoControllerTest}'s
 * {@code @WebMvcTest} slice.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TodoApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListReplaceAndDeleteRoundTripAgainstTheRealDatabase() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdMember": "Alice", "description": "Buy milk"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        TodoItemDto created = readDto(createResult);
        assertThat(created.id()).isNotNull();
        assertThat(created.householdMember()).isEqualTo("Alice");
        assertThat(created.description()).isEqualTo("Buy milk");
        assertThat(created.done()).isFalse();

        MvcResult listResult = mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readDtoList(listResult)).extracting(TodoItemDto::id).contains(created.id());

        MvcResult replaceResult = mockMvc.perform(put("/api/todos/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Buy milk and bread", "done": true}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        TodoItemDto replaced = readDto(replaceResult);
        assertThat(replaced.id()).isEqualTo(created.id());
        assertThat(replaced.householdMember()).isEqualTo("Alice");
        assertThat(replaced.description()).isEqualTo("Buy milk and bread");
        assertThat(replaced.done()).isTrue();

        mockMvc.perform(delete("/api/todos/{id}", created.id()))
                .andExpect(status().isNoContent());

        MvcResult afterDeleteResult = mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readDtoList(afterDeleteResult)).extracting(TodoItemDto::id).doesNotContain(created.id());
    }

    @Test
    void replaceReturns404ForAnUnknownId() throws Exception {
        mockMvc.perform(put("/api/todos/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Buy milk", "done": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns404ForAnUnknownId() throws Exception {
        mockMvc.perform(delete("/api/todos/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private TodoItemDto readDto(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), TodoItemDto.class);
    }

    private List<TodoItemDto> readDtoList(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, TodoItemDto.class));
    }
}
