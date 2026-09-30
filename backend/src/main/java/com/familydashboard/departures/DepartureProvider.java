package com.familydashboard.departures;

import java.util.List;

/**
 * Wraps whatever external transit data source this dashboard uses behind our
 * own domain type ({@link Departure}), per this project's provider/DTO
 * convention for external API calls (see {@code CLAUDE.md}): an upstream
 * change touches one adapter class. The one real implementation is
 * {@link TriasDepartureProvider}.
 */
public interface DepartureProvider {

    /**
     * Fetches the next departures at the configured stop. A live call every
     * time — unlike {@code WeatherProvider}, there is deliberately no cache
     * layer in front of this (see {@code docs/departures-widget-plan.md}'s
     * "No caching/scheduling" design decision: a cached "6 minutes" becomes
     * wrong within seconds).
     *
     * @throws RuntimeException (or a subtype) if the underlying call fails or
     *                          the response can't be parsed. Ticket 2's
     *                          controller is responsible for turning that
     *                          into a sensible HTTP response rather than an
     *                          unhandled 500.
     */
    List<Departure> nextDepartures();
}
