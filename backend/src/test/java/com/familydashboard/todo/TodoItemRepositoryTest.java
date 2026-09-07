package com.familydashboard.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TodoItemRepositoryTest {

    @Autowired
    private TodoItemRepository todoItemRepository;

    @Test
    void savesAndFindsATodoItemWithDoneDefaultingToFalse() {
        TodoItem saved = todoItemRepository.save(new TodoItem("Alice", "Buy milk"));

        List<TodoItem> all = todoItemRepository.findAllByOrderById();

        assertThat(all).hasSize(1);
        TodoItem found = all.get(0);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getHouseholdMember()).isEqualTo("Alice");
        assertThat(found.getDescription()).isEqualTo("Buy milk");
        assertThat(found.isDone()).isFalse();
    }

    @Test
    void findAllByOrderByIdReturnsItemsInInsertionOrder() {
        TodoItem first = todoItemRepository.save(new TodoItem("Alice", "Buy milk"));
        TodoItem second = todoItemRepository.save(new TodoItem("Bob", "Walk the dog"));

        List<TodoItem> all = todoItemRepository.findAllByOrderById();

        assertThat(all).extracting(TodoItem::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void updatingAnExistingTodoItemPersistsTheChanges() {
        TodoItem saved = todoItemRepository.save(new TodoItem("Bob", "Walk the dog"));

        saved.setDescription("Walk the dog twice");
        saved.setDone(true);
        todoItemRepository.save(saved);

        TodoItem updated = todoItemRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getHouseholdMember()).isEqualTo("Bob");
        assertThat(updated.getDescription()).isEqualTo("Walk the dog twice");
        assertThat(updated.isDone()).isTrue();
    }
}
