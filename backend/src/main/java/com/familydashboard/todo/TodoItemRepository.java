package com.familydashboard.todo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link TodoItem}.
 */
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    /**
     * All todo items in a stable, insertion-friendly order.
     */
    List<TodoItem> findAllByOrderById();
}
