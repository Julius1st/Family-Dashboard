import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';
import { EMPTY, catchError } from 'rxjs';

import { Departure } from './departure';

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
 */
@Injectable({ providedIn: 'root' })
export class DeparturesService {
  private readonly http = inject(HttpClient);

  private readonly departuresState = signal<readonly Departure[] | undefined>(undefined);

  /** The next departures at the configured stop. `undefined` until the initial fetch resolves. */
  readonly departures: Signal<readonly Departure[] | undefined> = this.departuresState.asReadonly();

  constructor() {
    this.http
      .get<Departure[]>('/api/departures')
      .pipe(catchError(() => EMPTY))
      .subscribe((departures) => this.departuresState.set(departures));
  }
}
