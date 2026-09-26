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
     * the caller to duplicate this class's {@code
     * weather.cache.refresh-interval-millis} value as a threshold, which is
     * both a magic number leaking across a class boundary and imprecise (it
     * can only ever guess whether a refresh failed, whereas this class already
     * knows for certain). {@code false} before any refresh has ever been
     * attempted — meaningless in that state since {@link #current()} is
     * still empty, but a sane default rather than a false "stale" signal.
     */
    private volatile boolean stale;

    public WeatherCache(WeatherProvider weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    /**
     * Both the repeat rate and the initial delay are externalized as
     * property placeholders — {@code @Scheduled}'s {@code fixedRateString}/
     * {@code initialDelayString} attributes resolve {@code ${...}}
     * placeholders against the {@code Environment} exactly like
     * {@code @Value}, a standard, documented Spring Framework feature (see
     * the "Scheduling Tasks" section of the Spring reference docs) — used
     * here specifically so the two delays can differ between production
     * and tests without duplicating this method or its scheduling logic.
     *
     * <p>{@code weather.cache.refresh-interval-millis} (15 minutes in
     * {@code application.yml}, matching {@code
     * docs/weather-widget-plan.md}'s recommendation) is the same in both.
     * {@code weather.cache.initial-delay-millis} deliberately is not: a
     * short production value (2 seconds, {@code application.yml}) means a
     * real cold boot populates the widget almost immediately instead of
     * leaving it on a loading placeholder for up to a full refresh
     * interval, while {@code src/test/resources/application.yml} overrides
     * it to 30 minutes for every test in this module — long enough that no
     * {@code @SpringBootTest} context (which constructs this bean for
     * real, with scheduling active) ever triggers a live network call to
     * Open-Meteo during a test run, the same safety the old shared
     * 15-minute delay used to provide for both concerns at once.
     */
    @Scheduled(
            fixedRateString = "${weather.cache.refresh-interval-millis}",
            initialDelayString = "${weather.cache.initial-delay-millis}")
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
