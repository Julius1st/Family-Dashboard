package com.familydashboard.todo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link TodoItem}.
 */
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    /**
     * All todo items in a stable, insertion-friendly order.
     */
    List<TodoItem> findAllByOrderById();

    /**
     * Deletes every item that is both done and overdue (its {@code dueDate}
     * is strictly before {@code cutoff}), returning the number removed.
     * Spring Data JPA supports {@code deleteBy...} derived queries returning
     * a removed-count, so no custom {@code @Query} is needed. Used by
     * {@code TodoController#getTodos} to sweep up done+overdue items as a
     * side effect of listing, per this feature's scoped-down design (no
     * scheduled job).
     *
     * <p>{@code @Transactional} is required here: unlike the CRUD methods
     * inherited from {@link JpaRepository} (already transactional in
     * {@code SimpleJpaRepository}), a derived delete query needs its own
     * transaction to remove entities — calling it from a plain, non-
     * transactional request like {@code GET /api/todos} would otherwise fail
     * with "No EntityManager with actual transaction available". {@code
     * spring-tx} is already a transitive dependency of {@code
     * spring-boot-starter-data-jpa}, so this adds no new dependency.
     */
    @Transactional
    long deleteByDoneTrueAndDueDateBefore(LocalDate cutoff);
}
