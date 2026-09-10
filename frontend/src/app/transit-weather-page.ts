import { Component } from '@angular/core';

/**
 * Screen 2 — Abfahrten · Wetter (design handoff). Neither the departures
 * nor the weather backend widget exists yet (Phase 5), so this ticket only
 * builds the two-slot responsive layout with a clear "not yet available"
 * placeholder card in each slot — matching the widget-card visual
 * language (bg/surface, border/widget, radius/widget) rather than leaving
 * blank space.
 */
@Component({
  selector: 'app-transit-weather-page',
  templateUrl: './transit-weather-page.html',
  styleUrl: './transit-weather-page.css',
})
export class TransitWeatherPage {}
