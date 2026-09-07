import { NgComponentOutlet } from '@angular/common';
import { Component, inject } from '@angular/core';

import { resolveWidgetTileComponent } from './widget-tile-registry';
import { WidgetService } from './widget.service';

/**
 * Renders one tile per widget returned by `WidgetService`, resolving each
 * widget's tile component through `resolveWidgetTileComponent` (currently
 * always the generic fallback tile — see `widget-tile-registry.ts`).
 *
 * Handles the three states `WidgetService.widgets` can be in:
 * - `undefined`: the initial request hasn't resolved yet — show a loading
 *   message.
 * - `[]`: resolved, no widgets registered — show a non-blank empty state.
 * - populated: render one tile per widget.
 */
@Component({
  selector: 'app-dashboard-shell',
  imports: [NgComponentOutlet],
  templateUrl: './dashboard-shell.html',
  styleUrl: './dashboard-shell.css',
})
export class DashboardShell {
  private readonly widgetService = inject(WidgetService);

  protected readonly widgets = this.widgetService.widgets;
  protected readonly resolveWidgetTileComponent = resolveWidgetTileComponent;
}
