import { Type } from '@angular/core';

import { WidgetFallbackTile } from './widget-fallback-tile';

/**
 * Minimal type-keyed seam for the dashboard shell: maps a widget's `id`
 * (from `WidgetDescriptor`) to the component that should render it.
 *
 * Empty for now — no real widget component exists yet (the Todo widget
 * is Phase 3, which will add an entry here, e.g. `todo: TodoWidgetTile`).
 * Deliberately not a general plugin/dynamic-loading framework: just
 * enough to resolve the one fallback case that exists today.
 */
const WIDGET_TILE_COMPONENTS: Record<string, Type<unknown>> = {};

/**
 * Resolves the component that should render the given widget id, falling
 * back to the generic display tile when no dedicated component is
 * registered.
 */
export function resolveWidgetTileComponent(widgetId: string): Type<unknown> {
  return WIDGET_TILE_COMPONENTS[widgetId] ?? WidgetFallbackTile;
}
