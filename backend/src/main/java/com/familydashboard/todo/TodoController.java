package com.familydashboard.todo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for {@link TodoItem}s, per the shape decided in
 * {@code docs/phase-3-plan.md}. Validation ({@code description} non-blank,
 * {@code householdMember} one of the configured members) is done with plain
 * code and {@link ResponseStatusException} rather than Bean Validation
 * annotations, since {@code backend/pom.xml} has no
 * {@code spring-boot-starter-validation} dependency (see {@code CLAUDE.md}'s
 * "don't add dependencies without authorization").
 */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoItemRepository todoItemRepository;
    private final HouseholdProperties householdProperties;

    public TodoController(TodoItemRepository todoItemRepository, HouseholdProperties householdProperties) {
        this.todoItemRepository = todoItemRepository;
        this.householdProperties = householdProperties;
    }

    @GetMapping
    public List<TodoItemDto> getTodos() {
        deleteDoneAndOverdueTodos();
        return todoItemRepository.findAllByOrderById().stream()
                .map(TodoItemDto::from)
                .toList();
    }

    @GetMapping("/members")
    public List<String> getMembers() {
        return householdProperties.members();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoItemDto createTodo(@RequestBody CreateTodoItemRequest request) {
        requireNonBlankDescription(request.description());
        requireKnownHouseholdMember(request.householdMember());

        TodoItem saved = todoItemRepository.save(new TodoItem(request.householdMember(), request.description()));
        return TodoItemDto.from(saved);
    }

    @PutMapping("/{id}")
    public TodoItemDto replaceTodo(@PathVariable Long id, @RequestBody UpdateTodoItemRequest request) {
        requireNonBlankDescription(request.description());

        TodoItem item = todoItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No todo item with id " + id));

        item.setDescription(request.description());
        item.setDone(request.done());
        TodoItem saved = todoItemRepository.save(item);
        return TodoItemDto.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTodo(@PathVariable Long id) {
        if (!todoItemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No todo item with id " + id);
        }
        todoItemRepository.deleteById(id);
    }

    /**
     * Deletes every done+overdue item before the list is read, so a just-
     * deleted item never appears in the response returned by
     * {@link #getTodos()}. No scheduled job — this cleanup only ever runs as
     * a side effect of {@code GET /api/todos}, per this feature's scoped-down
     * design.
     */
    private void deleteDoneAndOverdueTodos() {
        todoItemRepository.deleteByDoneTrueAndDueDateBefore(LocalDate.now());
    }

    private void requireNonBlankDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description must not be blank");
        }
    }

    private void requireKnownHouseholdMember(String householdMember) {
        if (householdMember == null || !householdProperties.members().contains(householdMember)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "householdMember must be one of " + householdProperties.members());
        }
    }
}
