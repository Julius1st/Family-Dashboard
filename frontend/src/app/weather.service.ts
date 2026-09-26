import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';

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
 * the backend's own first scheduled refresh has ever succeeded, which
 * surfaces as an `HttpClient` error and simply leaves `snapshot()`
 * `undefined`.
 */
@Injectable({ providedIn: 'root' })
export class WeatherService {
  private readonly http = inject(HttpClient);

  private readonly snapshotState = signal<WeatherSnapshot | undefined>(undefined);

  /** The current weather snapshot. `undefined` until the initial fetch resolves. */
  readonly snapshot: Signal<WeatherSnapshot | undefined> = this.snapshotState.asReadonly();

  constructor() {
    this.http.get<WeatherSnapshot>('/api/weather').subscribe((snapshot) => this.snapshotState.set(snapshot));
  }
}
