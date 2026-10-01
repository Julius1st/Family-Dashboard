import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, Signal, computed, inject, signal } from '@angular/core';
import { EMPTY, catchError, interval, startWith, switchMap } from 'rxjs';

import { Departure, DeparturesResponse } from './departure';

/**
 * Re-fetch cadence for `GET /api/departures`. Unlike weather (stays
 * plausible for hours), departures go stale within minutes — a board that
 * never updates after its initial load isn't useful on a wall-mounted
 * kiosk display. `DeparturesController` already calls the live TRIAS
 * endpoint fresh on every request with no server-side caching (see `docs/
 * departures-widget-plan.md`'s "No caching/scheduling" decision), so
 * there's nothing to gain from polling faster than a human would plausibly
 * glance back at the board, and 60s keeps request volume against the
 * (rate-limited, externally-operated) TRIAS provider reasonable.
 */
const REFRESH_INTERVAL_MS = 60_000;

/**
 * Fetches the next departures from `GET /api/departures` on a 60-second
 * timer and exposes them as a signal, per this repo's "prefer signals over
 * RxJS for component state" convention (see `CLAUDE.md`). Consumers only
 * ever read `departures()`/`stopName()` — the underlying `HttpClient`
 * observable never escapes this service.
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
 * **Polling mechanism**: `interval(REFRESH_INTERVAL_MS)` + `switchMap` into
 * the HTTP request, rather than a raw `setInterval` calling `.subscribe()`
 * repeatedly (the only existing interval precedent in this codebase,
 * `Header`'s once-a-minute clock tick, does that — but it only ever sets a
 * signal synchronously, with nothing in flight to overlap). `switchMap`
 * composes an async HTTP call with the ticking source correctly: if a tick
 * ever fired while the previous request were still pending (not expected at
 * a 60s cadence against a backend with no caching of its own, but not
 * guaranteed either), it cancels the stale in-flight request rather than
 * letting two responses race to set `responseState` in an undefined order.
 * `interval(REFRESH_INTERVAL_MS)` on its own would wait a full period before
 * its first tick (it's `timer(period, period)` under the hood), which would
 * delay the very first load by 60s — a regression from the old
 * one-shot-on-construction behavior. Prefixing it with `startWith(0)` emits
 * one extra, synchronous value immediately on subscribe (no scheduler
 * involved, unlike the periodic ticks that follow), so the first HTTP
 * request still fires the moment the service is constructed, exactly as
 * before, and every tick after that is the real 60s-interval one.
 *
 * **Stale-data policy on a failed refresh**: `catchError` sits on the
 * *inner* request observable (inside the `switchMap` callback), not on the
 * outer `interval` chain. This matters: catching there turns one failed
 * request into a quiet "this tick produced nothing" (via `EMPTY`), and
 * `responseState` simply keeps whatever it last held — the widget keeps
 * showing the last-known-good departures/stop name rather than clearing to
 * `undefined` or an error state, the same "keep stale data on a failed
 * refresh" spirit `WeatherCache` applies server-side (even though this is
 * now client-side polling, not a server cache). Catching on the outer chain
 * instead would be a bug: an RxJS error terminates its source permanently,
 * so a single failed tick would silently kill all future polling for the
 * rest of the app's lifetime with no way to recover short of a full reload.
 * The one case this doesn't newly cover is the very first fetch failing —
 * that still surfaces as `departures()`/`stopName()` staying `undefined`
 * forever (nothing "stale" exists yet to fall back to), exactly as the old
 * one-shot behavior did; `DeparturesPanel`'s existing loading placeholder
 * already covers that renders-as-"still loading" case reasonably.
 *
 * **Lifecycle**: `DeparturesService` is `providedIn: 'root'`, so it lives
 * for the whole app's lifetime in this single-page, no-routing kiosk app
 * (`CLAUDE.md`: "runs on a touchscreen... single long-lived session") —
 * the service is never destroyed before the app itself is, so there's no
 * real leak from never calling `unsubscribe()`. The subscription is still
 * torn down via `DestroyRef.onDestroy()` below anyway (mirroring `Header`'s
 * own interval cleanup convention) purely for hygiene/symmetry with that
 * precedent, not because it's load-bearing here.
 *
 * One internal signal holds the full `DeparturesResponse` (or `undefined`
 * before the first fetch resolves); `departures`/`stopName` are both
 * derived `computed()`s off it rather than two independently-set signals,
 * so a single `subscribe` callback can never leave them momentarily out of
 * sync with each other. Each successful poll sets this signal to a freshly
 * deserialized response object (new array/object references every time,
 * even when the underlying data is unchanged), so `departures`/`stopName`
 * (and anything downstream computed off them, e.g. `DeparturesPanel.
 * freshnessLabel`) correctly re-fire on every successful refresh — Angular
 * signals compare by reference (`Object.is`) by default, and JSON
 * deserialization never reuses a prior response's object identity.
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
    const subscription = interval(REFRESH_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.http.get<DeparturesResponse>('/api/departures').pipe(catchError(() => EMPTY))),
      )
      .subscribe((response) => this.responseState.set(response));

    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }
}
