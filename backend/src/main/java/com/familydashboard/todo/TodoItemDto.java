package com.familydashboard.todo;

/**
 * External-facing (JSON) representation of a {@link TodoItem}, following the
 * same "don't expose the JPA entity directly" discipline as
 * {@code WidgetDescriptor} in the widget package.
 */
public record TodoItemDto(Long id, String householdMember, String description, boolean done) {

    static TodoItemDto from(TodoItem item) {
        return new TodoItemDto(item.getId(), item.getHouseholdMember(), item.getDescription(), item.isDone());
    }
}
