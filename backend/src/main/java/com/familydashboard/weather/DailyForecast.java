package com.familydashboard.weather;

import java.time.LocalDate;

/**
 * One future day's whole-day forecast summary, as shown in the weather
 * widget's compact "next 3 days" outlook section — no hourly breakdown,
 * per the design request that this stay a dense summary rather than another
 * big, hour-by-hour block like today's hourly strip.
 *
 * @param date            the calendar date this forecast is for.
 * @param conditionText   German-language condition text for that day's WMO
 *                         weather code, resolved via {@link
 *                         WeatherConditionTexts} (same mapping used for
 *                         today's {@code conditionText}).
 * @param highTemperature that day's forecast high, in degrees Celsius.
 * @param lowTemperature  that day's forecast low, in degrees Celsius.
 * @param rainProbability that day's overall forecast chance of
 *                         precipitation, 0-100 (percent) — Open-Meteo's
 *                         daily {@code precipitation_probability_max}, the
 *                         day-level equivalent of {@link
 *                         HourlyForecast#rainProbability()}.
 * @param rainAmountMm    that day's total forecast precipitation depth, in
 *                         millimeters (equivalently, liters per square
 *                         meter — "l/m²", the unit label German weather
 *                         reporting conventionally uses for this).
 */
public record DailyForecast(
        LocalDate date,
        String conditionText,
        double highTemperature,
        double lowTemperature,
        int rainProbability,
        double rainAmountMm) {
}
