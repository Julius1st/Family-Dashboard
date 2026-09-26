import { Component } from '@angular/core';

import { WeatherPanel } from './weather-panel';

/**
 * Screen 2 — Abfahrten · Wetter (design handoff). The departures backend
 * widget still doesn't exist, so that slot keeps its "not yet available"
 * placeholder card (matching the widget-card visual language — bg/surface,
 * border/widget, radius/widget — rather than leaving blank space). The
 * weather slot (Ticket 4) is now the real `WeatherPanel` component, which
 * owns its own card styling entirely; this page host contributes nothing
 * beyond the grid column it sits in (see `transit-weather-page.css`'s
 * `grid-template-columns: minmax(0, 1fr) 452px`).
 */
@Component({
  selector: 'app-transit-weather-page',
  templateUrl: './transit-weather-page.html',
  styleUrl: './transit-weather-page.css',
  imports: [WeatherPanel],
})
export class TransitWeatherPage {}
