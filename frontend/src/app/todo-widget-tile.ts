import { Component, inject, input } from '@angular/core';

import { TodoItem } from './todo-item';
import { TodoService } from './todo.service';
import { WidgetDescriptor } from './widget-descriptor';

/**
 * Dedicated tile for the `"todo"` widget, registered in
 * `widget-tile-registry.ts`. Renders one section per configured household
 * member (from `TodoService.members()`), each showing that member's items
 * (filtered from `TodoService.items()`), a toggle-done and delete control
 * per item, and an add-item input.
 *
 * Accepts `descriptor` to satisfy `NgComponentOutlet`'s input contract
 * (every resolved tile component gets `{ descriptor: widget }`), but never
 * reads it — this tile's real data comes from `TodoService`.
 *
 * `members()`/`items()` are `undefined` until their initial fetches
 * resolve; a loading message is shown until both have.
 */
@Component({
  selector: 'app-todo-widget-tile',
  templateUrl: './todo-widget-tile.html',
  styleUrl: './todo-widget-tile.css',
})
export class TodoWidgetTile {
  readonly descriptor = input.required<WidgetDescriptor>();

  private readonly todoService = inject(TodoService);

  protected readonly members = this.todoService.members;
  protected readonly items = this.todoService.items;

  /** Items belonging to `member`, from the already-fetched `items` list. */
  protected itemsFor(member: string, items: readonly TodoItem[]): readonly TodoItem[] {
    return items.filter((item) => item.householdMember === member);
  }

  protected onToggle(item: TodoItem): void {
    this.todoService.setDone(item.id, !item.done);
  }

  protected onDelete(id: number): void {
    this.todoService.remove(id);
  }

  /**
   * Handles the per-member add-item form submit. Blank/whitespace-only
   * descriptions are silently ignored (matching the backend's own
   * "blank description is invalid" rule) rather than sent to the server.
   * On a non-blank submit, creates the item and clears the input.
   */
  protected onAdd(member: string, descriptionInput: HTMLInputElement, event: Event): void {
    event.preventDefault();
    const description = descriptionInput.value.trim();
    if (description === '') {
      return;
    }
    this.todoService.create(member, description);
    descriptionInput.value = '';
  }
}
