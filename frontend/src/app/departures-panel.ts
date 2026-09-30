import { Component, computed, inject } from '@angular/core';

import { Departure, DepartureStatusTone } from './departure';
import { DeparturesService } from './departures.service';

/**
 * This dashboard's real target stop is not yet known — `TransitProperties.
 * stopPointRef` (backend) is still an empty placeholder until MobiData BW
 * grants TRIAS access (see `docs/departures-widget-plan.md`'s "Working
 * without credentials yet" section) — unlike `WeatherPanel.LOCATION_LABEL`,
 * which could be confidently swapped from the design handoff's illustrative
 * "Frankfurt" to this project's actual real target city ("Karlsruhe",
 * confirmed elsewhere in the docs). There is no equivalent confirmed real
 * stop name to substitute here, so this keeps the handoff's own illustrative
 * sample stop name verbatim rather than inventing one. Update this once a
 * real stop is chosen/configured.
 */
const STOP_LABEL = 'Ostbahnhof';

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
 * this ticket (see {@link STOP_LABEL}'s own doc comment and
 * `docs/departures-widget-plan.md`'s "Working without credentials yet"
 * section), so `application.yml`'s `transit.*` placeholders are still
 * blank. Every render this component's own tests exercise uses a fake
 * `DeparturesService`, never a real backend response; there has been no
 * end-to-end check against live departure data, unlike the weather widget's
 * equivalent ticket, which could verify against the real Open-Meteo API.
 */
@Component({
  selector: 'app-departures-panel',
  templateUrl: './departures-panel.html',
  styleUrl: './departures-panel.css',
})
export class DeparturesPanel {
  private readonly departuresService = inject(DeparturesService);

  protected readonly departures = this.departuresService.departures;
  protected readonly stopLabel = STOP_LABEL;

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
