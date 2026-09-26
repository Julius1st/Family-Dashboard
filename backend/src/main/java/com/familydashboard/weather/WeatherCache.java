package com.familydashboard.weather;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Holds the last successfully fetched {@link WeatherSnapshot}, refreshed on
 * a fixed schedule via {@link WeatherProvider}. Implements the "on a failed
 * refresh keep the last values" behaviour the design handoff requires (see
 * {@code docs/weather-widget-plan.md}'s "Caching, not per-request live
 * calls" section): a failed refresh is logged and otherwise swallowed,
 * leaving the previous snapshot (and its now-older {@code fetchedAt}) in
 * place rather than propagating the failure or crashing the scheduler.
 *
 * <p>{@code GET /api/weather} (Ticket 2) always serves {@link #current()}
 * instantly rather than making a live call per request.
 */
@Component
public class WeatherCache {

    private static final Logger log = LoggerFactory.getLogger(WeatherCache.class);

    /**
     * Refresh interval, in milliseconds (15 minutes) — matches {@code
     * docs/weather-widget-plan.md}'s recommendation. Also used as the
     * initial delay: the first refresh only happens 15 minutes after
     * startup rather than immediately, so bringing up the application (in
     * particular, in tests using {@code @SpringBootTest}, which construct
     * this bean as part of a full application context) never makes a live
     * network call as a side effect — deliberate, since this devcontainer's
     * egress firewall can't reach Open-Meteo (see {@code
     * docs/weather-widget-plan.md}'s "Environment constraint" section) and
     * tests must never depend on the live API per {@code CLAUDE.md}. In
     * production this means the widget briefly has no data on a cold start;
     * an acceptable trade-off for a fixed 15-minute cadence with no
     * "refresh now" mechanism in scope (see the plan doc's "Out of scope"
     * section on the scheduler interval).
     */
    private static final long REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L;

    private final WeatherProvider weatherProvider;

    private volatile WeatherSnapshot snapshot;

    public WeatherCache(WeatherProvider weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    @Scheduled(fixedRate = REFRESH_INTERVAL_MILLIS, initialDelay = REFRESH_INTERVAL_MILLIS)
    void refresh() {
        try {
            snapshot = weatherProvider.fetch();
        } catch (RuntimeException e) {
            log.warn("Failed to refresh weather snapshot; keeping previous cached value", e);
        }
    }

    /**
     * The last successfully fetched snapshot, or {@link Optional#empty()} if
     * no refresh has ever succeeded yet (e.g. immediately after startup,
     * before the first scheduled refresh has run).
     */
    public Optional<WeatherSnapshot> current() {
        return Optional.ofNullable(snapshot);
    }
}
