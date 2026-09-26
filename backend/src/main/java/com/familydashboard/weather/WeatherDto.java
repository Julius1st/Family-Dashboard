package com.familydashboard.weather;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * External-facing (JSON) representation of a {@link WeatherSnapshot}, shaped
 * to exactly what the weather widget needs per {@code docs/
 * design_handoff_family_dashboard/README.md}'s "Screen 2 — Abfahrten ·
 * Wetter" section, following the same "don't expose the internal domain type
 * directly" discipline as {@code TodoItemDto} in the todo package.
 *
 * <p>{@code hourly} reuses {@link HourlyForecast} rather than introducing a
 * parallel {@code HourlyForecastDto}: unlike {@link WeatherSnapshot} itself,
 * {@link HourlyForecast} is already our own plain domain record (not a JPA
 * entity, not an upstream/Open-Meteo shape) whose field names
 * ({@code hour}/{@code temperature}/{@code rainProbability}) are exactly the
 * frontend-facing shape the design calls for — a wrapper type here would be
 * pure duplication.
 *
 * @param stale     {@code true} if the most recent scheduled refresh failed
 *                   and this is a previously cached value being served as a
 *                   fallback (see {@link WeatherCache#isStale()}), per the
 *                   design handoff's "on a failed refresh keep the last
 *                   values and mark them stale rather than blanking the
 *                   widget" requirement.
 * @param fetchedAt when this snapshot was actually retrieved from the
 *                   provider, so the frontend can also show its own
 *                   freshness label (e.g. "heute") independent of
 *                   {@code stale}.
 */
public record WeatherDto(
        double currentTemperature,
        String conditionText,
        double highTemperature,
        double lowTemperature,
        List<HourlyForecast> hourly,
        int humidityPercent,
        double windSpeedKmh,
        LocalDateTime sunset,
        Instant fetchedAt,
        boolean stale) {

    static WeatherDto from(WeatherSnapshot snapshot, boolean stale) {
        return new WeatherDto(
                snapshot.currentTemperature(),
                snapshot.conditionText(),
                snapshot.highTemperature(),
                snapshot.lowTemperature(),
                snapshot.hourly(),
                snapshot.humidityPercent(),
                snapshot.windSpeedKmh(),
                snapshot.sunset(),
                snapshot.fetchedAt(),
                stale);
    }
}
