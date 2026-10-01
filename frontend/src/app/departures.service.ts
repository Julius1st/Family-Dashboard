import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { EMPTY, catchError } from 'rxjs';

import { Departure, DeparturesResponse } from './departure';

/**
 * Fetches the next departures from `GET /api/departures` and exposes them as
 * a signal, per this repo's "prefer signals over RxJS for component state"
 * convention (see `CLAUDE.md`). Consumers only ever read `departures()` —
 * the underlying `HttpClient` observable never escapes this service.
 *
 * Follows `TodoService`'s/`WeatherService`'s established shape (an internal
 * writable signal populated by subscribing in the constructor) rather than
 * `WidgetService`'s `toSignal(...)` shape, same reasoning as `WeatherService`
 * gives for itself: this codebase's more recently established pattern, and
 * one a future manual-refresh action could build on without a shape change.
 *
 * `undefined` means the initial fetch hasn't resolved yet. An empty array is
 * a normal, valid "no departures in the queried window" state, not an
 * error — same "undefined = not yet resolved, populated-including-empty =
 * resolved" convention `TodoService`/`WidgetService` use. This also lines up
 * with `DeparturesController`'s own contract (see `docs/
 * departures-widget-plan.md`'s Ticket 2 section): it responds with an empty
 * list rather than an error status both when there are genuinely no
 * departures and when the TRIAS provider isn't configured/reachable yet, so
 * this service doesn't need to (and can't) tell those two cases apart.
 *
 * Mirrors `WeatherService`'s `catchError` for the same reason given there:
 * an unhandled request failure would otherwise make RxJS report it as an
 * unhandled error. This ticket is a one-shot fetch on construction, matching
 * `TodoService`/`WeatherService`'s own scope — a periodic refresh (unlike
 * weather, departures go stale within minutes, not hours) is a plausible
 * fast-follow but out of scope for `docs/departures-widget-plan.md`'s
 * Ticket 3.
 *
 * One internal signal holds the full `DeparturesResponse` (or `undefined`
 * before the fetch resolves); `departures`/`stopName` are both derived
 * `computed()`s off it rather than two independently-set signals, so a
 * single `subscribe` callback can never leave them momentarily
 * out of sync with each other.
 */
@Injectable({ providedIn: 'root' })
export class DeparturesService {
  private readonly http = inject(HttpClient);

  private readonly responseState = signal<DeparturesResponse | undefined>(undefined);

  /** The next departures at the configured stop. `undefined` until the initial fetch resolves. */
  readonly departures: Signal<readonly Departure[] | undefined> = computed(() => this.responseState()?.departures);

  /**
   * The configured stop's human-readable name. `undefined` until the
   * initial fetch resolves; `null` once resolved if the backend could not
   * determine it (zero departures, or the TRIAS provider failing — see
   * `DeparturesResponse`'s own doc comment for why the backend doesn't
   * substitute a fallback itself).
   */
  readonly stopName: Signal<string | null | undefined> = computed(() => this.responseState()?.stopName);

  constructor() {
    this.http
      .get<DeparturesResponse>('/api/departures')
      .pipe(catchError(() => EMPTY))
      .subscribe((response) => this.responseState.set(response));
  }
}
