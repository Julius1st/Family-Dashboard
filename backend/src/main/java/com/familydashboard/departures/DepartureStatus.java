package com.familydashboard.departures;

/**
 * A departure's real-time status, matching the design handoff's three states
 * for the departures widget (see {@code docs/design_handoff_family_dashboard/
 * README.md}'s "Screen 2 — Abfahrten · Wetter" section): {@code pünktlich}
 * (on time), {@code +3 Min} (delayed, with the delay shown in minutes) and
 * {@code fällt aus} (cancelled).
 *
 * <p>Ticket 2's {@code DepartureDto} is expected to translate this into the
 * German status text and color-coding tone shown in the UI; this enum itself
 * stays presentation-agnostic, per this project's entity/DTO separation
 * pattern.
 */
public enum DepartureStatus {

    /** Expected time matches the scheduled time (or no real-time data is available yet). */
    ON_TIME,

    /** Expected time is later than the scheduled time. */
    DELAYED,

    /** The service will not run; {@link Departure#expectedTime()} is not meaningful. */
    CANCELLED
}
