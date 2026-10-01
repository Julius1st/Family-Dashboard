import { Component, computed, inject } from '@angular/core';

import { Departure, DepartureStatusTone } from './departure';
import { DeparturesService } from './departures.service';

/**
 * Fallback title shown once a fetch has resolved but the backend still
 * couldn't determine the stop's real name (`DeparturesService.stopName()`
 * resolved to `null` — zero departures in the queried window, or the TRIAS
 * provider call failing entirely; see `DeparturesResponse`'s own doc comment
 * for why the backend passes that `null` through rather than substituting
 * something itself).
 *
 * "Haltestelle" (German for "stop") is chosen over the two other options
 * considered: the raw configured `stop-point-ref` value (e.g. {@code
 * "de:08212:623"}) isn't exposed by the API at all today and is also not
 * remotely passenger-facing text, so showing it would trade one piece of
 * confusing placeholder copy for another, arguably worse one; and leaving
 * the title blank reads as broken/unfinished next to the eyebrow and
 * freshness marker either side of it in the same title row, whereas a
 * generic-but-honest label doesn't claim to know something it doesn't. This
 * is expected to be rare in practice — a real TRIAS response always carries
 * `StopPointName` for every live departure it returns — so simplicity here
 * wins over a more elaborate fallback scheme for a state that should barely
 * ever be seen, same reasoning `DepartureDto`'s own `"Gl. –"` fallback gives
 * for itself.
 */
const UNKNOWN_STOP_LABEL = 'Haltestelle';

/**
 * `time` is an ISO-8601 local date-time string from the backend (see
 * `Departure`'s own doc comment) — formatted with a module-level, reused
 * `Intl.DateTimeFormat`, matching `WeatherPanel`'s established convention
 * (hardcoded German locale, no Angular `DatePipe`/i18n registration).
 */
const TIME_FORMATTER = new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit' });

/** The design handoff's own exact countdown copy, e.g. "in 6 Min" (`mockups.dc.html`'s `inMin` sample field) — a cancelled departure's `null` gets "—" instead. */
function formatCountdown(countdownMinutes: number | null): string {
  return countdownMinutes === null ? '—' : `in ${countdownMinutes} Min`;
}

/** One departure row's fully-derived view model — never stored, only ever produced by `rows()` below. */
interface DepartureRow {
  readonly line: string;
  readonly destination: string;
  /** Already pre-formatted by the backend (e.g. "Gl. 3" or "Gl. –") — displayed as-is, no further formatting here. */
  readonly platform: string;
  readonly statusText: string;
  readonly statusTone: DepartureStatusTone;
  readonly timeLabel: string;
  readonly countdownLabel: string;
  /** Drives the struck-through/dimmed time treatment the design specifies for a cancelled departure. */
  readonly cancelled: boolean;
}

/**
 * Screen 2's departures widget (design handoff, "Departures widget (left,
 * fills remaining width)"): title row (eyebrow/stop name/freshness), then
 * one row per departure (line badge, destination, platform, status with
 * color coding, time + countdown). Replaces `TransitWeatherPage`'s
 * departures placeholder `<article>` entirely — this component owns the
 * full widget-card visual language itself (bg/surface, border/widget,
 * radius/widget), the same two-layer `:host` (outer flex sizing) /
 * `.departures-panel` (card look) split `WeatherPanel`/`TasksPage` use for
 * their own cards.
 *
 * Everything shown is derived from `DeparturesService.departures()` via
 * `computed()` — nothing is duplicated into component state, matching this
 * codebase's established convention (see `WeatherPanel`/`TasksPage`).
 *
 * **Read-only**: no interactive controls, per the design handoff's
 * "Interactions & behavior" section ("Departures / weather — read-only") —
 * same as `WeatherPanel`.
 *
 * **Freshness marker**: `docs/design_handoff_family_dashboard/README.md`'s
 * "State" section lists a `lastUpdated` timestamp for the widget's "Stand
 * hh:mm" marker, but `DepartureDto` (Ticket 2) carries no such field itself
 * — `DeparturesController` calls the TRIAS provider live on every request
 * with no caching layer at all (see `docs/departures-widget-plan.md`'s "No
 * caching/scheduling" design decision), so there is no backend-side
 * "fetched at" bookkeeping to expose. This derives it client-side instead:
 * `freshnessLabel` is a `computed()` that reads `new Date()` — same
 * "impure read inside `computed()`" pattern `WeatherPanel.hourly` already
 * uses for `new Date().getHours()` — which only re-evaluates when {@link
 * DeparturesService#departures} itself changes (i.e. once, when
 * `DeparturesService`'s one-shot constructor-time fetch resolves — see its
 * own doc comment), so the timestamp shown is genuinely "when this
 * component last received data," not a continuously-ticking clock.
 *
 * **Loading vs. empty vs. "not configured/unreachable"**: `departures()`
 * `undefined` renders a loading placeholder. Once resolved, a genuinely
 * empty array (no departures in the queried window) renders a distinct,
 * non-blank empty-state message — same "empty is not an error, and isn't
 * displayed as blank space" discipline as the Todo widget's own empty
 * states (`TasksPage`). This component cannot and does not distinguish
 * "genuinely no departures right now" from "the TRIAS provider isn't
 * configured/reachable yet" — `DeparturesController` (Ticket 2) already
 * collapses both into the same empty-list response, so both render the
 * same empty state here; see this component's own manual-verification note
 * below for what that means for testing against real data.
 *
 * **Manual verification note**: this component cannot be smoke-tested
 * against real TRIAS data yet — MobiData BW has not granted access as of
 * this ticket (see `docs/departures-widget-plan.md`'s "Working without
 * credentials yet" section), so `application.yml`'s `transit.*` placeholders
 * are still blank. Every render this component's own tests exercise uses a
 * fake `DeparturesService`, never a real backend response; there has been no
 * end-to-end check against live departure data, unlike the weather widget's
 * equivalent ticket, which could verify against the real Open-Meteo API.
 *
 * **Title, across loading / populated / name-unknown states**: `titleLabel`
 * reads `DeparturesService.stopName()` and renders blank while it's
 * `undefined` (the initial fetch hasn't resolved — the previous hardcoded
 * sample stop name used to render unconditionally here even during loading,
 * which this replaces: showing a specific-looking but not-yet-real name for
 * up to one network round trip is more misleading than a brief blank title
 * next to the "Abfahrten werden geladen…" message already covering that
 * state below), the real name once resolved, or {@link
 * UNKNOWN_STOP_LABEL}'s fallback if resolved but `null` (see that constant's
 * own doc comment for the reasoning).
 */
@Component({
  selector: 'app-departures-panel',
  templateUrl: './departures-panel.html',
  styleUrl: './departures-panel.css',
})
export class DeparturesPanel {
  private readonly departuresService = inject(DeparturesService);

  protected readonly departures = this.departuresService.departures;

  /** See this class's own doc comment ("Title, across loading / populated / name-unknown states") for what each state renders. */
  protected readonly titleLabel = computed(() => {
    const stopName = this.departuresService.stopName();
    if (stopName === undefined) {
      return '';
    }
    return stopName ?? UNKNOWN_STOP_LABEL;
  });

  protected readonly freshnessLabel = computed(() => {
    return this.departures() !== undefined ? `Stand ${TIME_FORMATTER.format(new Date())}` : '';
  });

  protected readonly rows = computed<readonly DepartureRow[]>(() => {
    const departures = this.departures();
    return departures ? departures.map(toRow) : [];
  });
}

function toRow(departure: Departure): DepartureRow {
  return {
    line: departure.line,
    destination: departure.destination,
    platform: departure.platform,
    statusText: departure.statusText,
    statusTone: departure.statusTone,
    timeLabel: TIME_FORMATTER.format(new Date(departure.time)),
    countdownLabel: formatCountdown(departure.countdownMinutes),
    cancelled: departure.statusTone === 'cancelled',
  };
}
