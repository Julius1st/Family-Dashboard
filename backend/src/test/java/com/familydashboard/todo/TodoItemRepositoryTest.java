package com.familydashboard.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

    @Test
    void deleteByDoneTrueAndDueDateBeforeOnlyRemovesItemsThatAreBothDoneAndOverdue() {
        LocalDate cutoff = LocalDate.of(2020, 6, 15);

        TodoItem doneAndOverdue = todoItemRepository.save(new TodoItem("Alice", "Done and overdue"));
        doneAndOverdue.setDone(true);
        doneAndOverdue.setDueDate(LocalDate.of(2020, 6, 14));
        todoItemRepository.save(doneAndOverdue);

        TodoItem doneNotOverdue = todoItemRepository.save(new TodoItem("Alice", "Done, due today (not overdue)"));
        doneNotOverdue.setDone(true);
        doneNotOverdue.setDueDate(cutoff);
        todoItemRepository.save(doneNotOverdue);

        TodoItem overdueNotDone = todoItemRepository.save(new TodoItem("Bob", "Overdue but not done"));
        overdueNotDone.setDone(false);
        overdueNotDone.setDueDate(LocalDate.of(2020, 6, 1));
        todoItemRepository.save(overdueNotDone);

        TodoItem notDoneNotOverdue = todoItemRepository.save(new TodoItem("Bob", "Not done, not overdue"));
        notDoneNotOverdue.setDone(false);
        notDoneNotOverdue.setDueDate(LocalDate.of(2020, 6, 20));
        todoItemRepository.save(notDoneNotOverdue);

        long deletedCount = todoItemRepository.deleteByDoneTrueAndDueDateBefore(cutoff);

        assertThat(deletedCount).isEqualTo(1);
        List<Long> remainingIds = todoItemRepository.findAllByOrderById().stream().map(TodoItem::getId).toList();
        assertThat(remainingIds)
                .doesNotContain(doneAndOverdue.getId())
                .containsExactlyInAnyOrder(doneNotOverdue.getId(), overdueNotDone.getId(), notDoneNotOverdue.getId());
    }
}
