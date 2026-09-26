package com.familydashboard.weather;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST API for the weather widget, per {@code
 * docs/weather-widget-plan.md}'s Ticket 2 section: exposes {@link
 * WeatherCache}'s always-fast, in-memory snapshot over HTTP as a {@link
 * WeatherDto} — never a live upstream call per request.
 */
@RestController
public class WeatherController {

    private final WeatherCache weatherCache;

    public WeatherController(WeatherCache weatherCache) {
        this.weatherCache = weatherCache;
    }

    /**
     * The current weather snapshot. Responds {@code 503 Service Unavailable}
     * only in the narrow cold-start window before the very first scheduled
     * refresh has ever succeeded (see {@link WeatherCache}'s Javadoc) — at
     * that point there is no "last known good value" to fall back to yet,
     * which is a different situation from the normal "serving a stale but
     * real value" case ({@link WeatherDto#stale()}), which responds
     * {@code 200} as usual.
     */
    @GetMapping("/api/weather")
    public WeatherDto getWeather() {
        WeatherSnapshot snapshot = weatherCache.current()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No weather data available yet"));
        return WeatherDto.from(snapshot, weatherCache.isStale());
    }
}
