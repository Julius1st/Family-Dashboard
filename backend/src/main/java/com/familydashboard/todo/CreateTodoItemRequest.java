package com.familydashboard.todo;

/**
 * Request body for {@code POST /api/todos}.
 */
public record CreateTodoItemRequest(String householdMember, String description) {
}
