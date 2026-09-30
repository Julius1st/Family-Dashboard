package com.familydashboard.departures;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * External-facing (JSON) representation of a {@link Departure}, shaped to
 * exactly what the departures widget needs per {@code
 * docs/design_handoff_family_dashboard/README.md}'s "Screen 2 — Abfahrten ·
 * Wetter" section ("Departures widget" subsection and its {@code
 * departures: { line, dest, time, countdown, track, status, delay? }[]}
 * state shape), following the same "don't expose the internal domain type
 * directly" discipline as {@code TodoItemDto} in the todo package.
 *
 * @param line             the published line name/number, e.g. {@code "S2"}.
 * @param destination      the service's destination, e.g. {@code "Bad
 *                         Herrenalb"}.
 * @param platform         pre-formatted for direct display, e.g. {@code "Gl.
 *                         3"} (the design's exact column format), or
 *                         {@code "Gl. –"} when the upstream response carries
 *                         no bay/platform information at all.
 * @param statusText       German status text for the design's status column:
 *                         {@code "pünktlich"}, {@code "+3 Min"} (the delay in
 *                         minutes), or {@code "fällt aus"}.
 * @param statusTone       one of {@code "ok"}/{@code "late"}/{@code
 *                         "cancelled"} — matches the design's own {@code
 *                         status: 'ok' | 'late' | 'cancelled'} state literal
 *                         exactly, so the frontend can map it straight to the
 *                         design's color tokens ({@code status/late}/{@code
 *                         status/cancelled}) without re-deriving it from
 *                         {@code statusText}.
 * @param time             the time to display in the design's time block —
 *                         the real-time estimate when one is available,
 *                         otherwise the scheduled time. For a cancelled
 *                         departure this is always the scheduled time (real-
 *                         time data isn't meaningful for a service that
 *                         won't run), which the frontend is expected to
 *                         render struck-through/dimmed per the design.
 * @param countdownMinutes minutes from now until {@code time}, floored at 0
 *                         (never negative — a departure due "now" still
 *                         reads as {@code 0}, not a confusing negative
 *                         number), or {@code null} for a cancelled departure,
 *                         matching the design's "{@code —} instead of a
 *                         countdown" spec for that case.
 */
public record DepartureDto(
        String line,
        String destination,
        String platform,
        String statusText,
        String statusTone,
        LocalDateTime time,
        Integer countdownMinutes) {

    static DepartureDto from(Departure departure, LocalDateTime now) {
        String platformText = departure.platform() == null || departure.platform().isBlank()
                ? "Gl. –"
                : "Gl. %s".formatted(departure.platform());

        return switch (departure.status()) {
            case CANCELLED -> new DepartureDto(
                    departure.line(), departure.destination(), platformText,
                    "fällt aus", "cancelled", departure.scheduledTime(), null);
            case DELAYED -> new DepartureDto(
                    departure.line(), departure.destination(), platformText,
                    "+%d Min".formatted(Duration.between(departure.scheduledTime(), departure.expectedTime()).toMinutes()),
                    "late", departure.expectedTime(), countdownMinutes(departure.expectedTime(), now));
            case ON_TIME -> {
                LocalDateTime displayTime = departure.expectedTime() != null ? departure.expectedTime() : departure.scheduledTime();
                yield new DepartureDto(
                        departure.line(), departure.destination(), platformText,
                        "pünktlich", "ok", displayTime, countdownMinutes(displayTime, now));
            }
        };
    }

    private static int countdownMinutes(LocalDateTime time, LocalDateTime now) {
        return (int) Math.max(0, Duration.between(now, time).toMinutes());
    }
}
