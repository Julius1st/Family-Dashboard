import { Component, computed, inject } from '@angular/core';

import { memberColor } from './member-color';
import { TodoItem } from './todo-item';
import { TodoService } from './todo.service';

/**
 * One column's fully-derived view model. Never stored on the component —
 * `columns()` (below) recomputes this from `TodoService.items()`/
 * `members()` on every change, so a `setDone` toggle flows straight
 * through without any manual re-fetch or duplicated state.
 */
interface MemberColumn {
  readonly member: string;
  readonly color: string;
  readonly items: readonly TodoItem[];
  readonly doneCount: number;
  readonly totalCount: number;
  /** Fraction of `items` that are done, in `[0, 1]`; `0` when `totalCount` is `0`. */
  readonly progress: number;
}

/**
 * Screen 1 — Aufgaben (design handoff): the real per-household-member
 * task board. Replaces `TodoWidgetTile`'s old generic-tile role entirely
 * (see docs/frontend-redesign-plan.md, Ticket 3) with the bespoke layout
 * the handoff specifies — a 3-column header row (eyebrow / title / done
 * line) followed by one column per configured household member: a color
 * dot + name + counter, a progress bar, the member's task rows, and a
 * per-member "add task" control pinned to the column's bottom.
 *
 * Everything derived (counts, progress ratios, per-member item lists) is
 * computed from `TodoService.items()`/`members()` via `computed()` —
 * nothing is stored separately, so toggling a task's `done` flag updates
 * the row, that member's progress bar, and the page's "x von y erledigt"
 * line immediately, purely through signal reactivity.
 *
 * The backend's `TodoItem` contract is only
 * `{ id, householdMember, description, done }` — it has no due-date/
 * status "meta" field. The design handoff's mock shows a meta line
 * (`überfällig`, `20 Min`, …) under each task's text, but that's sample
 * data for the mock's own hardcoded array, not something the real API
 * returns, so there is nothing to render for it here.
 *
 * The add-task button is a real, styled, ≥44px control per member —
 * wiring its click to actually open the "Neue Aufgabe" dialog is
 * Ticket 4's job, not this one's, so `onAddTask` is intentionally a
 * no-op for now.
 */
@Component({
  selector: 'app-tasks-page',
  templateUrl: './tasks-page.html',
  styleUrl: './tasks-page.css',
})
export class TasksPage {
  private readonly todoService = inject(TodoService);

  protected readonly members = this.todoService.members;
  protected readonly items = this.todoService.items;

  /** "x von y erledigt" — summed across every member's items, computed, never stored. */
  protected readonly doneCount = computed(() => (this.items() ?? []).filter((item) => item.done).length);
  protected readonly totalCount = computed(() => (this.items() ?? []).length);

  /**
   * One view model per configured member, in `members()`'s own order —
   * so `memberColor(index)` stays stable per member as long as the
   * member list's order doesn't change.
   */
  protected readonly columns = computed<readonly MemberColumn[]>(() => {
    const members = this.members() ?? [];
    const items = this.items() ?? [];
    return members.map((member, index) => {
      const memberItems = items.filter((item) => item.householdMember === member);
      const doneCount = memberItems.filter((item) => item.done).length;
      return {
        member,
        color: memberColor(index),
        items: memberItems,
        doneCount,
        totalCount: memberItems.length,
        progress: memberItems.length === 0 ? 0 : doneCount / memberItems.length,
      };
    });
  });

  /** Tapping anywhere on a task row toggles its `done` flag (design handoff, "Task row"). */
  protected onToggle(item: TodoItem): void {
    this.todoService.setDone(item.id, !item.done);
  }

  /**
   * No-op for this ticket. Ticket 4 wires this to open the "Neue
   * Aufgabe" dialog scoped to `member` (no member picker needed, per the
   * handoff — the button already carries the member identity).
   */
  protected onAddTask(member: string): void {
    void member;
  }
}
