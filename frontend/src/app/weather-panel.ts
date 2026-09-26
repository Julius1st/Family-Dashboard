import { Component, computed, inject } from '@angular/core';

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

/** One hourly-strip cell's fully-derived view model — never stored, only ever produced by `hourly()` below. */
interface HourlyCell {
  /** Zero-padded hour label, e.g. "09". */
  readonly label: string;
  readonly temperatureLabel: string;
  readonly rainProbability: number;
  readonly rainBarHeightPx: number;
  /** `true` at/above `RAIN_HIGH_THRESHOLD_PERCENT` — drives the bar-fill/percentage-text accent color. */
  readonly rainHigh: boolean;
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
 * wrapping or padding with placeholders.
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

  protected formatTemperature(value: number): string {
    return `${Math.round(value)}°`;
  }

  protected formatWindSpeed(value: number): string {
    return `${Math.round(value)} km/h`;
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
  };
}
