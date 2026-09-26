package com.familydashboard.weather;

/**
 * Wraps whatever external weather data source this dashboard uses behind our
 * own domain type ({@link WeatherSnapshot}), per this project's provider/DTO
 * convention for external API calls (see {@code CLAUDE.md}): an upstream
 * change touches one adapter class. The one real implementation is {@link
 * OpenMeteoWeatherProvider}.
 */
public interface WeatherProvider {

    /**
     * Fetches the current weather snapshot from the underlying data source.
     * A live call every time — callers wanting a cached, always-fast value
     * should go through {@link WeatherCache} instead.
     *
     * @throws RuntimeException (or a subtype) if the underlying call fails;
     *                          {@link WeatherCache} is responsible for
     *                          catching this and keeping the previous
     *                          snapshot in place.
     */
    WeatherSnapshot fetch();
}
