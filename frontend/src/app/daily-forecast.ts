/**
 * One future day's whole-day forecast summary, as shown in the weather
 * widget's compact "next 3 days" outlook section — no hourly breakdown.
 *
 * Mirrors the backend's `com.familydashboard.weather.DailyForecast` record
 * exactly: `{ date, conditionText, highTemperature, lowTemperature }`.
 */
export interface DailyForecast {
  /**
   * ISO-8601 calendar date string, e.g. "2026-09-27" (backend `LocalDate`).
   * Typed `string`, not `Date` — same convention as `WeatherSnapshot`'s
   * `sunset`/`fetchedAt`: this service does no client-side date parsing,
   * it just passes the value through for a consumer (`WeatherPanel`) to
   * format.
   */
  readonly date: string;
  /** German-language condition text for that day (e.g. "Bedeckt", "Leichter Regen"). */
  readonly conditionText: string;
  /** That day's forecast high in degrees Celsius. */
  readonly highTemperature: number;
  /** That day's forecast low in degrees Celsius. */
  readonly lowTemperature: number;
}
