package com.familydashboard.todo;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * A single todo item owned by one household member.
 *
 * <p>Written in a JPA/Hibernate-portable way (no H2-specific SQL or types)
 * per this project's persistence decision, so swapping to PostgreSQL later
 * is a config change, not a rewrite.
 */
@Entity
public class TodoItem {

    @Id
    @GeneratedValue
    private Long id;

    private String householdMember;

    private String description;

    private boolean done = false;

    /**
     * Backend-only bookkeeping used to auto-delete stale, completed items
     * (see {@code TodoController#getTodos}) — never exposed via
     * {@link TodoItemDto} or either request record. Set automatically to
     * today's date when a new item is created; not settable by clients.
     */
    private LocalDate dueDate;

    /**
     * No-args constructor required by JPA; not for application use.
     */
    protected TodoItem() {
    }

    public TodoItem(String householdMember, String description) {
        this.householdMember = householdMember;
        this.description = description;
        this.dueDate = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public String getHouseholdMember() {
        return householdMember;
    }

    public void setHouseholdMember(String householdMember) {
        this.householdMember = householdMember;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    /**
     * Test/administrative use only — production code never sets this after
     * construction; it's a plain setter (matching this entity's other
     * mutable fields) so tests can seed items with an arbitrary due date.
     */
    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
