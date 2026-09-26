package com.familydashboard.weather;

/**
 * One hour's worth of forecast data, as shown in the weather widget's hourly
 * strip.
 *
 * @param hour           hour of the day, 0-23 (local to whatever timezone the
 *                        upstream request was made in — see {@code
 *                        OpenMeteoWeatherProvider}).
 * @param temperature     forecast temperature in degrees Celsius.
 * @param rainProbability forecast chance of precipitation, 0-100 (percent).
 * @param rainAmountMm    forecast precipitation depth for that hour, in
 *                         millimeters (equivalently, liters per square
 *                         meter — "l/m²" is the unit label German weather
 *                         reporting conventionally uses for this, per the
 *                         feature request that added this field).
 */
public record HourlyForecast(int hour, double temperature, int rainProbability, double rainAmountMm) {
}
