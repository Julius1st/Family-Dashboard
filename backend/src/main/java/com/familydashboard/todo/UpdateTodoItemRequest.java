package com.familydashboard.todo;

/**
 * Request body for {@code PUT /api/todos/{id}}: a full replace of the
 * mutable fields. {@code householdMember} is intentionally not included —
 * per {@code docs/phase-3-plan.md}, the household member list is
 * config-only for v1, and an item's owner isn't reassigned by this
 * endpoint.
 */
public record UpdateTodoItemRequest(String description, boolean done) {
}
