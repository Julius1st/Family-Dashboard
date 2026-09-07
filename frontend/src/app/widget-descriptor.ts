/**
 * External-facing description of a registered widget, as returned by
 * `GET /api/widgets`.
 *
 * Mirrors the backend's `com.familydashboard.widget.WidgetDescriptor`
 * record exactly: `{ "id": string, "displayName": string }`.
 */
export interface WidgetDescriptor {
  readonly id: string;
  readonly displayName: string;
}
