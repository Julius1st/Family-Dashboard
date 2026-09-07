package com.familydashboard.todo;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code HouseholdProperties} is a constructor-bound {@code
 * @ConfigurationProperties} record, so it only binds via {@code
 * @EnableConfigurationProperties} (it isn't component-scanned). This slice
 * doesn't load {@code FamilyDashboardApplication}'s
 * {@code @EnableConfigurationProperties}, so it's re-declared here, with an
 * explicit, test-local member list via {@code @TestPropertySource} (rather
 * than relying on {@code application.yml}'s real values, which this test
 * shouldn't be coupled to).
 */
@WebMvcTest(TodoController.class)
@EnableConfigurationProperties(HouseholdProperties.class)
@TestPropertySource(properties = "household.members=Alice,Bob")
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TodoItemRepository todoItemRepository;

    @Test
    void getTodosReturnsAllItemsAsJson() throws Exception {
        TodoItem item = new TodoItem("Alice", "Buy milk");
        ReflectionTestUtils.setField(item, "id", 1L);
        when(todoItemRepository.findAllByOrderById()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].householdMember").value("Alice"))
                .andExpect(jsonPath("$[0].description").value("Buy milk"))
                .andExpect(jsonPath("$[0].done").value(false));
    }

    @Test
    void getTodosReturnsEmptyArrayWhenNoItemsExist() throws Exception {
        when(todoItemRepository.findAllByOrderById()).thenReturn(List.of());

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getMembersReturnsTheConfiguredHouseholdMembers() throws Exception {
        mockMvc.perform(get("/api/todos/members"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("Alice"))
                .andExpect(jsonPath("$[1]").value("Bob"));
    }

    @Test
    void createTodoReturns201WithTheCreatedItem() throws Exception {
        when(todoItemRepository.save(any(TodoItem.class))).thenAnswer(invocation -> {
            TodoItem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdMember": "Alice", "description": "Buy milk"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.householdMember").value("Alice"))
                .andExpect(jsonPath("$.description").value("Buy milk"))
                .andExpect(jsonPath("$.done").value(false));
    }

    @Test
    void createTodoRejectsABlankDescription() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdMember": "Alice", "description": "   "}
                                """))
                .andExpect(status().isBadRequest());

        verify(todoItemRepository, never()).save(any());
    }

    @Test
    void createTodoRejectsAnUnknownHouseholdMember() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdMember": "Charlie", "description": "Buy milk"}
                                """))
                .andExpect(status().isBadRequest());

        verify(todoItemRepository, never()).save(any());
    }

    @Test
    void replaceTodoUpdatesDescriptionAndDoneAndReturnsTheUpdatedItem() throws Exception {
        TodoItem existing = new TodoItem("Alice", "Buy milk");
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(todoItemRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(todoItemRepository.save(any(TodoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/todos/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Buy milk and bread", "done": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.householdMember").value("Alice"))
                .andExpect(jsonPath("$.description").value("Buy milk and bread"))
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void replaceTodoRejectsABlankDescriptionWithoutLookingUpTheItem() throws Exception {
        mockMvc.perform(put("/api/todos/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "", "done": true}
                                """))
                .andExpect(status().isBadRequest());

        verify(todoItemRepository, never()).findById(anyLong());
    }

    @Test
    void replaceTodoReturns404WhenTheIdDoesNotExist() throws Exception {
        when(todoItemRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/todos/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Buy milk", "done": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTodoReturns204WhenTheItemExists() throws Exception {
        when(todoItemRepository.existsById(7L)).thenReturn(true);

        mockMvc.perform(delete("/api/todos/7"))
                .andExpect(status().isNoContent());

        verify(todoItemRepository, times(1)).deleteById(7L);
    }

    @Test
    void deleteTodoReturns404WhenTheIdDoesNotExist() throws Exception {
        when(todoItemRepository.existsById(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/todos/999"))
                .andExpect(status().isNotFound());

        verify(todoItemRepository, never()).deleteById(anyLong());
    }
}
