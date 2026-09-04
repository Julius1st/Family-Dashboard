package com.familydashboard.widget;

/**
 * Contract implemented by every dashboard widget. Widgets are discovered as
 * Spring beans and collected by {@link WidgetRegistry}.
 */
public interface Widget {

    /**
     * Unique identifier for this widget, stable across restarts. Used as the
     * registry key and in external references (e.g. HTTP routes).
     */
    String id();

    /**
     * Human-readable name shown to users.
     */
    String displayName();
}
