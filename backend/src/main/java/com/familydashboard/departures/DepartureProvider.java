package com.familydashboard.departures;

/**
 * Wraps whatever external transit data source this dashboard uses behind our
 * own domain type ({@link Departure}/{@link DepartureBoard}), per this
 * project's provider/DTO convention for external API calls (see {@code
 * CLAUDE.md}): an upstream change touches one adapter class. The one real
 * implementation is {@link TriasDepartureProvider}.
 */
public interface DepartureProvider {

    /**
     * Fetches the next departures at the configured stop, plus that stop's
     * own name (see {@link DepartureBoard}'s javadoc for why both are
     * bundled into one return type rather than the name living on each
     * {@link Departure} row). A live call every time — unlike {@code
     * WeatherProvider}, there is deliberately no cache layer in front of this
     * (see {@code docs/departures-widget-plan.md}'s "No caching/scheduling"
     * design decision: a cached "6 minutes" becomes wrong within seconds).
     *
     * <p>Named {@code nextDepartureBoard} rather than keeping the original
     * {@code nextDepartures} now that the return type is no longer a bare
     * departures list — the method name should say what it actually hands
     * back.
     *
     * @throws RuntimeException (or a subtype) if the underlying call fails or
     *                          the response can't be parsed. {@link
     *                          DeparturesController} is responsible for
     *                          turning that into a sensible HTTP response
     *                          rather than an unhandled 500.
     */
    DepartureBoard nextDepartureBoard();
}
