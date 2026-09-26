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

    /**
     * Whether the <em>most recent</em> refresh attempt failed (Ticket 2's
     * {@code GET /api/weather} needs an explicit "is what I'm serving
     * stale?" signal to implement the design handoff's "mark as stale
     * rather than blanking the widget" requirement — see {@code
     * docs/weather-widget-plan.md}'s Ticket 2 section). Tracked explicitly
     * here rather than left for a caller to infer from {@code
     * snapshot.fetchedAt()}'s age: inferring staleness from age would need
     * the caller to duplicate this class's {@link
     * #REFRESH_INTERVAL_MILLIS} as a threshold, which is both a magic
     * number leaking across a class boundary and imprecise (it can only
     * ever guess whether a refresh failed, whereas this class already
     * knows for certain). {@code false} before any refresh has ever been
     * attempted — meaningless in that state since {@link #current()} is
     * still empty, but a sane default rather than a false "stale" signal.
     */
    private volatile boolean stale;

    public WeatherCache(WeatherProvider weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    @Scheduled(fixedRate = REFRESH_INTERVAL_MILLIS, initialDelay = REFRESH_INTERVAL_MILLIS)
    void refresh() {
        try {
            snapshot = weatherProvider.fetch();
            stale = false;
        } catch (RuntimeException e) {
            stale = true;
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

    /**
     * Whether {@link #current()} (when present) is serving a snapshot from a
     * refresh attempt older than the most recent one — i.e. the most recent
     * scheduled refresh failed, so the cache is falling back to the last
     * known good value instead of the newest one.
     */
    public boolean isStale() {
        return stale;
    }
}
