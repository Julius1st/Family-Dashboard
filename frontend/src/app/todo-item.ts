/**
 * External-facing representation of a to-do item, as returned by the
 * `/api/todos` endpoints.
 *
 * Mirrors the backend's `com.familydashboard.todo.TodoItemDto` record
 * exactly: `{ id, householdMember, description, done }`.
 */
export interface TodoItem {
  readonly id: number;
  readonly householdMember: string;
  readonly description: string;
  readonly done: boolean;
}
