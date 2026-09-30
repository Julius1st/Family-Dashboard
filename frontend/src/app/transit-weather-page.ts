import { Component } from '@angular/core';

import { DeparturesPanel } from './departures-panel';
import { WeatherPanel } from './weather-panel';

/**
 * Screen 2 — Abfahrten · Wetter (design handoff). Both slots are now real,
 * fully-wired components: the departures slot (Ticket 4) is `DeparturesPanel`,
 * and the weather slot is `WeatherPanel` — each owns its own card styling
 * entirely (bg/surface, border/widget, radius/widget); this page host
 * contributes nothing beyond the grid columns they sit in (see
 * `transit-weather-page.css`'s `grid-template-columns: minmax(0, 2fr)
 * minmax(0, 1fr)`).
 */
@Component({
  selector: 'app-transit-weather-page',
  templateUrl: './transit-weather-page.html',
  styleUrl: './transit-weather-page.css',
  imports: [DeparturesPanel, WeatherPanel],
})
export class TransitWeatherPage {}
