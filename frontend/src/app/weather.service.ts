import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';
import { EMPTY, catchError } from 'rxjs';

import { WeatherSnapshot } from './weather-snapshot';

/**
 * Fetches the current weather snapshot from `GET /api/weather` and exposes
 * it as a signal, per this repo's "prefer signals over RxJS for component
 * state" convention (see `CLAUDE.md`). Consumers only ever read
 * `snapshot()` — the underlying `HttpClient` observable never escapes this
 * service.
 *
 * Follows `TodoService`'s established shape (an internal writable signal
 * populated by subscribing in the constructor) rather than `WidgetService`'s
 * `toSignal(...)` shape, since `TodoService` is this codebase's more
 * recently established, mutation-capable pattern; a future ticket adding a
 * manual refresh action here (mirroring `TodoService`'s `create`/`setDone`/
 * etc.) would need the same internal-signal shape anyway.
 *
 * `undefined` means the initial fetch hasn't resolved yet. There is no
 * "empty" state distinct from that — unlike `/api/todos`, `/api/weather`
 * always returns a single snapshot object once resolved (see
 * `WeatherController`), except for the narrow cold-start 503 case before
 * the backend's own first scheduled refresh has ever succeeded.
 *
 * **Ticket 4 gap fix**: that 503 (or any other request failure) used to
 * reach `subscribe()` with no error callback, which makes RxJS report it as
 * an unhandled error (rethrown asynchronously) — a real bug, not just a
 * missing UI treatment. The `catchError` below swallows it and completes
 * quietly, leaving `snapshot()` `undefined` exactly as before the request
 * ever ran. That's a deliberate choice, not a placeholder: a 503 here only
 * ever happens once, right after a cold boot, before the backend's own
 * scheduler has completed a single successful refresh; this widget is
 * read-only with no retry control (design handoff, "Interactions &
 * behavior"); and `WeatherPanel` already renders a "loading" placeholder
 * for `undefined` that reads perfectly reasonably for this case too — a
 * separate "unavailable" signal would add a state with nothing distinct
 * for the UI to say or do about it. See `WeatherPanel`'s own doc comment
 * for the UI-side half of this decision.
 */
@Injectable({ providedIn: 'root' })
export class WeatherService {
  private readonly http = inject(HttpClient);

  private readonly snapshotState = signal<WeatherSnapshot | undefined>(undefined);

  /** The current weather snapshot. `undefined` until the initial fetch resolves. */
  readonly snapshot: Signal<WeatherSnapshot | undefined> = this.snapshotState.asReadonly();

  constructor() {
    this.http
      .get<WeatherSnapshot>('/api/weather')
      .pipe(catchError(() => EMPTY))
      .subscribe((snapshot) => this.snapshotState.set(snapshot));
  }
}
