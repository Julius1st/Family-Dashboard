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
 *                         3"} for a tram/train track or {@code "Bstg. 3"} for
 *                         a bus bay (both the design's exact column format,
 *                         already produced as-is by {@link
 *                         Departure#platform()} — see its javadoc), or
 *                         {@code "Gl. –"} when the upstream response carries
 *                         no bay/platform information at all (see {@link
 *                         #from}'s comment on why this generic fallback was
 *                         kept even though it technically implies a tram
 *                         track).
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
        // Departure.platform() is now already the fully-formatted display
        // label ("Gl. 3", "Bstg. 3", ...) - TriasDepartureProvider's
        // normalizePlatform() decides which short label applies, since that
        // depends on TRIAS-specific raw-text quirks this DTO layer shouldn't
        // reinterpret (see its javadoc). This method's only job left is the
        // genuinely-no-platform-info-at-all fallback.
        //
        // Edge case call: "Gl. –" (rather than something more neutral like
        // just "–") is kept as that fallback even now that some departures
        // are buses, where "Gl." specifically implies a tram/train track.
        // Reasoning: a departure with NO bay/platform information at all is
        // presumably rare (every real KVV response seen so far - tram or
        // bus - has carried an explicit label), and this fallback never
        // claims a wrong label since it's wording for "no information", not
        // "here is an incorrect label" - matching the generic "–" dash the
        // rest of the design already uses for "nothing to show" (e.g. the
        // cancelled countdown). Simplicity wins over a speculative fix for
        // an unobserved case.
        String platformText = departure.platform() == null || departure.platform().isBlank()
                ? "Gl. –"
                : departure.platform();

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
