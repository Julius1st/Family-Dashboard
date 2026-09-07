package com.familydashboard.todo;

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
     * No-args constructor required by JPA; not for application use.
     */
    protected TodoItem() {
    }

    public TodoItem(String householdMember, String description) {
        this.householdMember = householdMember;
        this.description = description;
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
}
