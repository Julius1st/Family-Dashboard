import { Injectable, Signal, signal } from '@angular/core';

/**
 * The two pages the kiosk shell switches between, per the design handoff's
 * own "State" section (`docs/design_handoff_family_dashboard/README.md`):
 * `activePage: 'tasks' | 'transit'`.
 */
export type Page = 'tasks' | 'transit';

/**
 * In-memory page-switch state for the app shell — deliberately **not**
 * Angular Router (see `docs/frontend-redesign-plan.md`, "Decisions already
 * made with the user"): there's no distinct URL per page, no deep-linking
 * requirement, and this matches the handoff's own state model exactly.
 *
 * A single signal read by the header (to highlight the active nav pill
 * item) and by the app root (to decide which page component to mount).
 */
@Injectable({ providedIn: 'root' })
export class PageNavigationService {
  private readonly _activePage = signal<Page>('tasks');

  readonly activePage: Signal<Page> = this._activePage.asReadonly();

  /** Switches the active page. Selecting the already-active page is a no-op. */
  select(page: Page): void {
    this._activePage.set(page);
  }
}
