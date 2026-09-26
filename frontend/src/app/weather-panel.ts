import { Component, computed, inject } from '@angular/core';

import { DailyForecast } from './daily-forecast';
import { HourlyForecast } from './hourly-forecast';
import { WeatherService } from './weather.service';

/**
 * This project's actual target city (`docs/weather-widget-plan.md`'s
 * "Design decisions" section) — the design handoff's own mock uses
 * "Frankfurt" as illustrative sample data, not the real deployment target.
 */
const LOCATION_LABEL = 'Karlsruhe';

/** The handoff's hourly strip is a fixed 6-column grid (Screen 2, "Weather widget"). */
const HOURLY_STRIP_LENGTH = 6;

/** Rain probability at/above which the bar fill and percentage text switch to the "high" accent (handoff, same section). */
const RAIN_HIGH_THRESHOLD_PERCENT = 50;

/** "a fill of `rain% × 0.3` px" (handoff, same section) — the track itself is 30px tall. */
const RAIN_BAR_PX_PER_PERCENT = 0.3;

/**
 * `sunset`/`fetchedAt` are ISO-8601 strings from the backend (see
 * `WeatherSnapshot`'s own doc comment) — formatted with a module-level,
 * reused `Intl.DateTimeFormat`, matching `Header`'s established convention
 * (hardcoded German locale, no Angular `DatePipe`/i18n registration).
 */
const TIME_FORMATTER = new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit' });

/**
 * Short German weekday label for each 3-day outlook row (e.g. "Mo", "Di") —
 * same "hardcoded German locale, no Angular `DatePipe`/i18n registration"
 * convention as `TIME_FORMATTER` above. `outlook[].date` is a date-only ISO
 * string (e.g. "2026-09-27", no time component — see `DailyForecast`'s own
 * doc comment), which `new Date(...)` parses as UTC midnight; since this
 * dashboard only ever targets `Europe/Berlin` (a positive UTC offset), that
 * always still falls on the same calendar day locally, so no timezone-shift
 * bug here.
 */
const WEEKDAY_FORMATTER = new Intl.DateTimeFormat('de-DE', { weekday: 'short' });

/**
 * Rainfall depth formatter for both the hourly strip's per-hour amount and
 * the 3-day outlook's per-day total (both backend fields are `rainAmountMm`,
 * in millimeters). German weather reporting conventionally expresses this
 * as "l/m²" (liters per square meter) — numerically identical to
 * millimeters, but a less ambiguous unit label for a German-language
 * display than a bare "mm" or "L" would be — per the feature request that
 * added these fields. Formatted with a German-locale `Intl.NumberFormat`
 * (comma decimal separator, e.g. "1,3 l/m²") for the same reason
 * `WEEKDAY_FORMATTER`/`TIME_FORMATTER` hardcode `de-DE`: this project's
 * established "hardcoded German locale, no Angular DatePipe/i18n
 * registration" convention, extended here to numbers.
 */
const RAIN_AMOUNT_FORMATTER = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

function formatRainAmount(mm: number): string {
  return `${RAIN_AMOUNT_FORMATTER.format(mm)} l/m²`;
}

/** One hourly-strip cell's fully-derived view model — never stored, only ever produced by `hourly()` below. */
interface HourlyCell {
  /** Zero-padded hour label, e.g. "09". */
  readonly label: string;
  readonly temperatureLabel: string;
  readonly rainProbability: number;
  readonly rainBarHeightPx: number;
  /** `true` at/above `RAIN_HIGH_THRESHOLD_PERCENT` — drives the bar-fill/percentage-text accent color. */
  readonly rainHigh: boolean;
  /** Pre-formatted expected rain amount for that hour, e.g. "0,4 l/m²". */
  readonly rainAmountLabel: string;
}

/**
 * One "next 3 days" outlook row's fully-derived view model — never stored,
 * only ever produced by `outlook()` below. Deliberately has no hourly
 * breakdown fields: "No hourly display, but values for the whole day" per
 * the original feature request this section implements — `rainRiskLabel`/
 * `rainAmountLabel` below are still whole-day summaries (the day's overall
 * risk/total), not an hour-by-hour rundown.
 */
interface OutlookRow {
  /** The day's ISO date string, used as the `@for` track key. */
  readonly date: string;
  /** Short German weekday label, e.g. "Mo". */
  readonly weekdayLabel: string;
  readonly conditionText: string;
  /** Pre-formatted "hi / lo", e.g. "19° / 8°" — same "high / low" shape as `.weather-panel__hi-lo` for today. */
  readonly hiLoLabel: string;
  /** Pre-formatted whole-day rain risk, e.g. "20%". */
  readonly rainRiskLabel: string;
  /** Pre-formatted whole-day expected rain amount, e.g. "1,3 l/m²". */
  readonly rainAmountLabel: string;
  /** `true` at/above `RAIN_HIGH_THRESHOLD_PERCENT` — same threshold/accent-color convention as the hourly strip's `HourlyCell.rainHigh`. */
  readonly rainRiskHigh: boolean;
}

/**
 * Screen 2's weather widget (design handoff, "Weather widget (right, 452px
 * fixed)"): title row (eyebrow/location/freshness), a current-conditions
 * block, an hourly temperature/rain strip, and a humidity/wind/sunset stats
 * row. Replaces `TransitWeatherPage`'s weather placeholder `<article>`
 * entirely — this component owns the full widget-card visual language
 * itself (bg/surface, border/widget, radius/widget), the same two-layer
 * `:host` (outer flex sizing) / `.weather-panel` (card look) split
 * `TasksPage` uses for its own card.
 *
 * Everything shown is derived from `WeatherService.snapshot()` via
 * `computed()` — nothing is duplicated into component state, so a fresh
 * poll (or a stale fallback) flows straight through via signal reactivity,
 * matching this codebase's established convention (see `TasksPage`).
 *
 * **Hourly strip window**: the backend returns a full 24-entry hourly array
 * for today (`OpenMeteoWeatherProvider`'s own doc comment: "an hourly strip
 * for the rest of today"). The handoff's fixed 6-column grid needs a
 * 6-entry slice, so this shows the next 6 hours from the current local
 * hour onward (not the mock's fixed 09/11/13/.../19 sample, which is
 * static illustrative data for a screenshot, not a real "always these
 * clock hours" requirement) — the most useful reading for a live
 * "leaving the house" glance. Late at night this can legitimately be
 * fewer than 6 cells (no next-day data is fetched, per the plan doc's
 * "today only" scope) — the grid simply renders fewer cells rather than
 * wrapping or padding with placeholders. Each cell now also shows the
 * hour's expected rain amount ("l/m²") alongside its rain-probability
 * percentage, per the same feature request that added the 3-day outlook's
 * own rain-risk/rain-amount fields below.
 *
 * **Freshness marker**: normally shows the handoff's static "heute" copy
 * verbatim. When `stale` is true (design handoff, "Interactions &
 * behavior": "on a failed refresh keep the last values and mark them stale
 * rather than blanking the widget"), this swaps to "veraltet · HH:MM"
 * (the last successful `fetchedAt`) instead of silently keeping "heute" on
 * data that may no longer be accurate — a deliberate, more honest
 * deviation from the mock's single static string, in exactly the slot the
 * handoff itself uses for departures' own freshness marker ("Stand
 * 07:42"), so it fits the same visual/informational role the design
 * already establishes for that column.
 *
 * **Cold-start / request-error handling**: `WeatherService` swallows a
 * failed initial fetch (e.g. the backend's documented cold-start 503
 * before its first scheduled refresh has ever succeeded) and leaves
 * `snapshot()` `undefined` — see `WeatherService`'s own doc comment for
 * why. This component doesn't distinguish that from "still loading": both
 * render the same `weather-panel__loading` placeholder. That's a
 * deliberate choice, not an oversight — a 503 is a rare, cold-start-only
 * case, this widget has no retry control per the handoff's "read-only"
 * framing, and a separate "unavailable" message would tell the user
 * nothing they could act on differently from "give it a moment."
 *
 * **3-day outlook**: a section below the stats row showing tomorrow, the
 * day after, and the day after that — one row per day, each split across
 * two lines: weekday/condition/hi-lo, then that day's overall rain risk
 * (%) and expected rain amount ("l/m²" — see `formatRainAmount`).
 * Deliberately no hour-by-hour breakdown for these days (that's what
 * `hourly` above is for, and only for today); the two ADDED fields here are
 * still whole-day summaries, not a step back toward an hourly view. A
 * follow-up feature request explicitly asked for genuine legibility/density
 * over hitting a specific row-height target (an earlier, padding-heavy pass
 * that only matched `.weather-panel__hour`'s height was reverted), so this
 * layout is sized to what the now-5-data-point-per-day content actually
 * needs, not a fixed budget.
 */
@Component({
  selector: 'app-weather-panel',
  templateUrl: './weather-panel.html',
  styleUrl: './weather-panel.css',
})
export class WeatherPanel {
  private readonly weatherService = inject(WeatherService);

  protected readonly snapshot = this.weatherService.snapshot;
  protected readonly locationLabel = LOCATION_LABEL;

  protected readonly freshnessLabel = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return '';
    }
    return snapshot.stale ? `veraltet · ${TIME_FORMATTER.format(new Date(snapshot.fetchedAt))}` : 'heute';
  });

  protected readonly hourly = computed<readonly HourlyCell[]>(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return [];
    }
    const currentHour = new Date().getHours();
    return snapshot.hourly
      .filter((forecast) => forecast.hour >= currentHour)
      .slice(0, HOURLY_STRIP_LENGTH)
      .map(toHourlyCell);
  });

  protected readonly sunsetLabel = computed(() => {
    const snapshot = this.snapshot();
    return snapshot ? TIME_FORMATTER.format(new Date(snapshot.sunset)) : '';
  });

  protected readonly outlook = computed<readonly OutlookRow[]>(() => {
    const snapshot = this.snapshot();
    return snapshot ? snapshot.outlook.map((day) => this.toOutlookRow(day)) : [];
  });

  protected formatTemperature(value: number): string {
    return `${Math.round(value)}°`;
  }

  protected formatWindSpeed(value: number): string {
    return `${Math.round(value)} km/h`;
  }

  private toOutlookRow(day: DailyForecast): OutlookRow {
    return {
      date: day.date,
      weekdayLabel: WEEKDAY_FORMATTER.format(new Date(day.date)),
      conditionText: day.conditionText,
      hiLoLabel: `${this.formatTemperature(day.highTemperature)} / ${this.formatTemperature(day.lowTemperature)}`,
      rainRiskLabel: `${day.rainProbability}%`,
      rainAmountLabel: formatRainAmount(day.rainAmountMm),
      rainRiskHigh: day.rainProbability >= RAIN_HIGH_THRESHOLD_PERCENT,
    };
  }
}

function toHourlyCell(forecast: HourlyForecast): HourlyCell {
  const rainHigh = forecast.rainProbability >= RAIN_HIGH_THRESHOLD_PERCENT;
  return {
    label: forecast.hour.toString().padStart(2, '0'),
    temperatureLabel: `${Math.round(forecast.temperature)}°`,
    rainProbability: forecast.rainProbability,
    rainBarHeightPx: Math.round(forecast.rainProbability * RAIN_BAR_PX_PER_PERCENT),
    rainHigh,
    rainAmountLabel: formatRainAmount(forecast.rainAmountMm),
  };
}
