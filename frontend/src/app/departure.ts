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
