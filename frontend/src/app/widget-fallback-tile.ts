import { Component, input } from '@angular/core';

import { WidgetDescriptor } from './widget-descriptor';

/**
 * Generic display tile used for any widget id that doesn't have a
 * dedicated component registered in `widget-tile-registry.ts`.
 *
 * Today that's every widget id — no real widget component exists yet
 * (the Todo widget is Phase 3). This just shows the widget's
 * `displayName` so the dashboard shell has something sensible to render.
 */
@Component({
  selector: 'app-widget-fallback-tile',
  templateUrl: './widget-fallback-tile.html',
  styleUrl: './widget-fallback-tile.css',
})
export class WidgetFallbackTile {
  readonly descriptor = input.required<WidgetDescriptor>();
}
