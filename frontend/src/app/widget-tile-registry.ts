import { Type } from '@angular/core';

import { TodoWidgetTile } from './todo-widget-tile';
import { WidgetFallbackTile } from './widget-fallback-tile';

/**
 * Minimal type-keyed seam for the dashboard shell: maps a widget's `id`
 * (from `WidgetDescriptor`) to the component that should render it.
 *
 * `"todo"` maps to `TodoWidgetTile` (Phase 3); every other widget id falls
 * back to the generic `WidgetFallbackTile`. Deliberately not a general
 * plugin/dynamic-loading framework: just enough to resolve the widgets
 * that actually exist today.
 */
const WIDGET_TILE_COMPONENTS: Record<string, Type<unknown>> = {
  todo: TodoWidgetTile,
};

/**
 * Resolves the component that should render the given widget id, falling
 * back to the generic display tile when no dedicated component is
 * registered.
 */
export function resolveWidgetTileComponent(widgetId: string): Type<unknown> {
  return WIDGET_TILE_COMPONENTS[widgetId] ?? WidgetFallbackTile;
}
