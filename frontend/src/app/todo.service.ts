import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';

import { TodoItem } from './todo-item';

/**
 * Fetches to-do items and the configured household members from
 * `/api/todos` and `/api/todos/members`, and exposes both as signals, per
 * this repo's "prefer signals over RxJS for component state" convention
 * (see `CLAUDE.md`). Consumers only ever read `items()`/`members()` — the
 * underlying `HttpClient` observables never escape this service.
 *
 * An empty array response for either endpoint is a normal, valid state
 * (no items yet / no configured members) rather than an error, mirroring
 * the backend's contract — the same "undefined = not yet resolved,
 * populated-including-empty = resolved" convention `WidgetService` uses.
 *
 * The backend's `PUT /api/todos/{id}` is a full replace of
 * `{ description, done }` — there is no partial-patch endpoint. So
 * `setDone` and `updateDescription`, which each only mean to change one
 * field, look up the item's *other* current field from the local `items`
 * signal and send both fields together. This means these two methods can
 * only act on an item this service already knows about (i.e. one that
 * came back from the initial fetch or a prior mutation) — true for every
 * real caller, since the UI only ever acts on items it rendered from
 * `items()`.
 */
@Injectable({ providedIn: 'root' })
export class TodoService {
  private readonly http = inject(HttpClient);

  private readonly itemsState = signal<readonly TodoItem[] | undefined>(undefined);
  private readonly membersState = signal<readonly string[] | undefined>(undefined);

  /** The current to-do items. `undefined` until the initial fetch resolves. */
  readonly items: Signal<readonly TodoItem[] | undefined> = this.itemsState.asReadonly();

  /** The configured household members. `undefined` until the initial fetch resolves. */
  readonly members: Signal<readonly string[] | undefined> = this.membersState.asReadonly();

  constructor() {
    this.http.get<TodoItem[]>('/api/todos').subscribe((items) => this.itemsState.set(items));
    this.http.get<string[]>('/api/todos/members').subscribe((members) => this.membersState.set(members));
  }

  /** Creates a new item via `POST /api/todos` and appends the result to `items`. */
  create(householdMember: string, description: string): void {
    this.http
      .post<TodoItem>('/api/todos', { householdMember, description })
      .subscribe((created) => this.itemsState.set([...(this.itemsState() ?? []), created]));
  }

  /**
   * Sets an item's `done` flag via `PUT /api/todos/{id}`, sending its
   * current `description` alongside since the backend requires the full
   * body.
   */
  setDone(id: number, done: boolean): void {
    const description = this.requireCurrentItem(id).description;
    this.replace(id, { description, done });
  }

  /**
   * Sets an item's `description` via `PUT /api/todos/{id}`, sending its
   * current `done` flag alongside since the backend requires the full
   * body.
   */
  updateDescription(id: number, description: string): void {
    const done = this.requireCurrentItem(id).done;
    this.replace(id, { description, done });
  }

  /** Deletes an item via `DELETE /api/todos/{id}` and removes it from `items`. */
  remove(id: number): void {
    this.http.delete<void>(`/api/todos/${id}`).subscribe(() => {
      this.itemsState.set((this.itemsState() ?? []).filter((item) => item.id !== id));
    });
  }

  private replace(id: number, body: { description: string; done: boolean }): void {
    this.http.put<TodoItem>(`/api/todos/${id}`, body).subscribe((updated) => {
      this.itemsState.set((this.itemsState() ?? []).map((item) => (item.id === id ? updated : item)));
    });
  }

  private requireCurrentItem(id: number): TodoItem {
    const item = (this.itemsState() ?? []).find((candidate) => candidate.id === id);
    if (!item) {
      throw new Error(`No known todo item with id ${id}`);
    }
    return item;
  }
}
