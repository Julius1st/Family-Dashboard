import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';

import { TodoService } from './todo.service';

/**
 * "Neue Aufgabe" (design handoff, Screen 1 "Interactions & behavior") —
 * opened by a member column's add-task button (see `TasksPage.onAddTask`),
 * already scoped to that member, so this dialog never shows a member
 * picker: "the button carries the member identity, so the add flow needs
 * no member picker" (handoff, Screen 1 item 5).
 *
 * Built on the native `<dialog>` element, per
 * `docs/frontend-redesign-plan.md` Ticket 4 ("a reasonable
 * zero-dependency choice — no new package"). `showModal()` gets
 * focus-trapping, `aria-modal`, top-layer rendering and Escape-to-cancel
 * for free, with zero added dependencies — which is exactly why the plan
 * suggests it over a hand-rolled overlay.
 *
 * One concrete gap found while implementing this ticket: this project's
 * test runner (`@angular/build`'s unit-test builder, which runs specs
 * against jsdom) uses a jsdom version (28.x) that does not implement
 * `HTMLDialogElement.prototype.showModal`/`.close()` at all — only the
 * plain `open` IDL attribute is implemented (confirmed directly against
 * jsdom before writing this). `showDialog`/`closeDialog` below
 * feature-detect those two methods and fall back to toggling `open`
 * directly (dispatching a synthetic `close` event so the same cleanup
 * runs either way). A real browser — this is a kiosk touchscreen app —
 * always takes the `showModal`/`close` branch and gets full native modal
 * behavior; only the test environment exercises the fallback, which
 * performs the same open/close bookkeeping without those two methods.
 */
@Component({
  selector: 'app-add-task-dialog',
  templateUrl: './add-task-dialog.html',
  styleUrl: './add-task-dialog.css',
})
export class AddTaskDialog {
  private readonly todoService = inject(TodoService);

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly inputRef = viewChild.required<ElementRef<HTMLInputElement>>('descriptionInput');

  /** The member this dialog instance is currently scoped to — see class doc. */
  protected readonly member = signal('');

  /** Opens the dialog scoped to `member`. Called by `TasksPage.onAddTask`. */
  open(member: string): void {
    this.member.set(member);
    this.showDialog();
  }

  /** Cancel button: closes without creating anything. */
  protected cancel(): void {
    this.closeDialog();
  }

  /**
   * Tapping the dimmed area outside the dialog's panel dismisses it, same
   * as cancel — a click that lands directly on the `<dialog>` element
   * itself (rather than a descendant) only happens on that outside area,
   * since the panel fills the dialog's own box otherwise.
   */
  protected onDialogClick(event: MouseEvent): void {
    if (event.target === this.dialogRef().nativeElement) {
      this.closeDialog();
    }
  }

  /**
   * Resets the input whenever the dialog closes, however it closed
   * (submit, cancel, backdrop click, or Escape) — one place, since every
   * closing path ends in a `close` event, native or synthetic (see class
   * doc).
   */
  protected onClose(): void {
    this.inputRef().nativeElement.value = '';
  }

  /**
   * Blank/whitespace-only input never reaches `TodoService.create` —
   * mirrors the backend's own validation so a blank submission doesn't
   * even cost a round trip just to get rejected (Ticket 4 AC).
   */
  protected submit(event: SubmitEvent): void {
    event.preventDefault();
    const description = this.inputRef().nativeElement.value.trim();
    if (!description) {
      return;
    }
    this.todoService.create(this.member(), description);
    this.closeDialog();
  }

  private showDialog(): void {
    const dialog = this.dialogRef().nativeElement;
    if (typeof dialog.showModal === 'function') {
      dialog.showModal();
    } else {
      dialog.open = true;
    }
  }

  private closeDialog(): void {
    const dialog = this.dialogRef().nativeElement;
    if (typeof dialog.close === 'function') {
      dialog.close();
    } else {
      dialog.open = false;
      dialog.dispatchEvent(new Event('close'));
    }
  }
}
