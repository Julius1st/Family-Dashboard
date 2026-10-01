/**
 * `statusTone` values, matching the design handoff's own `status: 'ok' |
 * 'late' | 'cancelled'` state literal exactly (see `docs/
 * design_handoff_family_dashboard/README.md`'s "State" section) — a
 * frontend-only refinement of the backend DTO's plain `String` field into a
 * union of its three actual values, so a typo or an unhandled fourth value
 * is a compile error here rather than a silent styling bug.
 */
export type DepartureStatusTone = 'ok' | 'late' | 'cancelled';

/**
 * External-facing representation of one upcoming departure, as returned by
 * `GET /api/departures`.
 *
 * Mirrors the backend's `com.familydashboard.departures.DepartureDto`
 * record exactly: `{ line, destination, platform, statusText, statusTone,
 * time, countdownMinutes }`.
 *
 * `time` (backend `LocalDateTime`) serializes to an ISO-8601 string over
 * JSON, so it's typed `string` here rather than `Date` — this service does
 * no client-side date parsing, it just passes the value through for a
 * consumer to format, same convention as `WeatherSnapshot#sunset`.
 */
export interface Departure {
  /** The published line name/number, e.g. `"S2"` or `"5"`. */
  readonly line: string;
  /** The service's destination, e.g. `"Bad Herrenalb"`. */
  readonly destination: string;
  /** Pre-formatted for direct display, e.g. `"Gl. 3"`. */
  readonly platform: string;
  /** German status text: `"pünktlich"`, `"+3 Min"`, or `"fällt aus"`. */
  readonly statusText: string;
  /** Maps directly to the design's color coding for the status column. */
  readonly statusTone: DepartureStatusTone;
  /**
   * The time to display, as an ISO-8601 local date-time string — the
   * real-time estimate when one is available, otherwise the scheduled time.
   * For a cancelled departure this is always the scheduled time, which
   * should be rendered struck-through/dimmed per the design.
   */
  readonly time: string;
  /**
   * Minutes from "now" (as of the backend's response) until `time`, floored
   * at 0, or `null` for a cancelled departure — matching the design's `—`
   * (instead of a countdown) for that case.
   */
  readonly countdownMinutes: number | null;
}

/**
 * Full response shape of `GET /api/departures`, mirroring the backend's
 * `com.familydashboard.departures.DeparturesDto` record exactly: `{
 * stopName, departures }`.
 *
 * `stopName` is `null` whenever the backend couldn't determine it — either
 * the resolved response genuinely had zero departures, or the TRIAS
 * provider call failed entirely (`DeparturesController` collapses both into
 * the same empty-board shape; see its own doc comment). The backend
 * deliberately passes that `null` straight through rather than substituting
 * placeholder text itself — see `DeparturesDto`'s doc comment for why: only
 * this frontend additionally has a third state the backend doesn't ("the
 * initial fetch hasn't resolved yet" — see `DeparturesService`), so only the
 * frontend can tell that apart from "resolved, but genuinely no name," and
 * is therefore the right place to decide what each state actually displays
 * (see `DeparturesPanel.titleLabel`).
 */
export interface DeparturesResponse {
  readonly stopName: string | null;
  readonly departures: readonly Departure[];
}
