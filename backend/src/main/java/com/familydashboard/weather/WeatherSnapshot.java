package com.familydashboard.weather;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Our own domain representation of "the weather right now", as returned by a
 * {@link WeatherProvider} — the upstream (Open-Meteo) JSON shape never leaks
 * past {@link OpenMeteoWeatherProvider}, per this project's provider/DTO
 * convention (see {@code CLAUDE.md}). Plain Java, not a JPA entity: nothing
 * here is persisted to H2, it's a live/cached value held by {@link
 * WeatherCache}.
 *
 * @param currentTemperature current temperature in degrees Celsius.
 * @param weatherCode        the raw WMO weather code (0-99) this snapshot was
 *                            derived from, kept alongside {@code
 *                            conditionText} so callers needing the numeric
 *                            code (e.g. for an icon) don't have to
 *                            re-derive it.
 * @param conditionText      German-language condition text for {@code
 *                            weatherCode}, resolved via {@link
 *                            WeatherConditionTexts}.
 * @param highTemperature    today's forecast high, in degrees Celsius.
 * @param lowTemperature     today's forecast low, in degrees Celsius.
 * @param hourly             hour-by-hour forecast for the rest of the day.
 * @param humidityPercent    relative humidity, 0-100 (percent).
 * @param windSpeedKmh       wind speed in km/h.
 * @param sunset             today's sunset time.
 * @param fetchedAt          when this snapshot was retrieved from the
 *                            provider — not an upstream field, our own
 *                            bookkeeping so callers can tell how stale a
 *                            cached snapshot is.
 */
public record WeatherSnapshot(
        double currentTemperature,
        int weatherCode,
        String conditionText,
        double highTemperature,
        double lowTemperature,
        List<HourlyForecast> hourly,
        int humidityPercent,
        double windSpeedKmh,
        LocalDateTime sunset,
        Instant fetchedAt) {
}
