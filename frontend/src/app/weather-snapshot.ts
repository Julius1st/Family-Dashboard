import { HourlyForecast } from './hourly-forecast';

/**
 * External-facing representation of the current weather, as returned by
 * `GET /api/weather`.
 *
 * Mirrors the backend's `com.familydashboard.weather.WeatherDto` record
 * exactly: `{ currentTemperature, conditionText, highTemperature,
 * lowTemperature, hourly, humidityPercent, windSpeedKmh, sunset,
 * fetchedAt, stale }`.
 *
 * `sunset` (backend `LocalDateTime`) and `fetchedAt` (backend `Instant`)
 * both serialize to ISO-8601 strings over JSON, so both are typed `string`
 * here rather than `Date` — this service does no client-side date parsing,
 * it just passes the values through for a consumer to format.
 */
export interface WeatherSnapshot {
  /** Current temperature in degrees Celsius. */
  readonly currentTemperature: number;
  /** German-language condition text (e.g. "Wolkig", "Regen"). */
  readonly conditionText: string;
  /** Today's forecast high in degrees Celsius. */
  readonly highTemperature: number;
  /** Today's forecast low in degrees Celsius. */
  readonly lowTemperature: number;
  /** Hourly forecast strip. */
  readonly hourly: readonly HourlyForecast[];
  /** Current relative humidity, 0-100 (percent). */
  readonly humidityPercent: number;
  /** Current wind speed in km/h. */
  readonly windSpeedKmh: number;
  /** Today's sunset time, as an ISO-8601 local date-time string. */
  readonly sunset: string;
  /** When this snapshot was actually retrieved from the provider, as an ISO-8601 instant string. */
  readonly fetchedAt: string;
  /**
   * `true` if the most recent scheduled refresh failed and this is a
   * previously cached value being served as a fallback, per the design
   * handoff's "on a failed refresh keep the last values and mark them
   * stale rather than blanking the widget" requirement.
   */
  readonly stale: boolean;
}
