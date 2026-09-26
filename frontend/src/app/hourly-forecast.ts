/**
 * One hour's worth of forecast data, as shown in the weather widget's
 * hourly strip.
 *
 * Mirrors the backend's `com.familydashboard.weather.HourlyForecast`
 * record exactly: `{ hour, temperature, rainProbability, rainAmountMm }`.
 */
export interface HourlyForecast {
  /** Hour of the day, 0-23. */
  readonly hour: number;
  /** Forecast temperature in degrees Celsius. */
  readonly temperature: number;
  /** Forecast chance of precipitation, 0-100 (percent). */
  readonly rainProbability: number;
  /**
   * Forecast precipitation depth for that hour, in millimeters
   * (equivalently, liters per square meter — displayed as "l/m²", the unit
   * label German weather reporting conventionally uses for this).
   */
  readonly rainAmountMm: number;
}
