package com.familydashboard.departures;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the departures widget, per {@code
 * docs/departures-widget-plan.md}'s Ticket 2 section: exposes {@link
 * DepartureProvider} over HTTP as a {@link DepartureDto} list.
 *
 * <p>Unlike {@code WeatherController} (which serves an always-fast, cached
 * {@code WeatherSnapshot}), this calls {@link DepartureProvider#nextDepartures()}
 * live on every request — see {@code docs/departures-widget-plan.md}'s "No
 * caching/scheduling" design decision: a cached "6 minutes" becomes wrong
 * within seconds, so there is no cache layer to serve from here.
 */
@RestController
public class DeparturesController {

    private static final Logger log = LoggerFactory.getLogger(DeparturesController.class);

    /**
     * This dashboard only ever targets one German stop (see {@code
     * docs/departures-widget-plan.md}'s "Out of scope" section: no stop
     * picker), so "now" for countdown purposes is anchored to this fixed
     * zone rather than derived from any per-request context — same reasoning
     * as {@code OpenMeteoWeatherProvider}'s {@code TIMEZONE} constant and
     * {@code TriasDepartureProvider}'s {@code ZONE} constant.
     */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final DepartureProvider departureProvider;

    public DeparturesController(DepartureProvider departureProvider) {
        this.departureProvider = departureProvider;
    }

    /**
     * The next departures at the configured stop, as {@link DepartureDto}s.
     *
     * <p>Responds with an empty list — never a {@code 500} — when {@link
     * DepartureProvider#nextDepartures()} fails, per {@code
     * docs/departures-widget-plan.md}'s Ticket 2 "Sensible error handling"
     * requirement. This covers both "not configured yet" (MobiData BW
     * hasn't granted access yet, so {@code transit.*} is still blank —
     * {@link TriasDepartureProvider} will fail to build a usable request or
     * connect to a blank endpoint URL) and "the real endpoint is
     * unreachable" — both are the same "no data available right now" case
     * from this controller's point of view, and an empty list is exactly
     * what the frontend's "no departures" empty state (Ticket 4) already
     * needs to render, so no separate error-status path is needed on top of
     * it.
     */
    @GetMapping("/api/departures")
    public List<DepartureDto> getDepartures() {
        List<Departure> departures;
        try {
            departures = departureProvider.nextDepartures();
        } catch (RuntimeException e) {
            log.warn("Failed to fetch departures; returning an empty list", e);
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        return departures.stream().map(departure -> DepartureDto.from(departure, now)).toList();
    }
}
