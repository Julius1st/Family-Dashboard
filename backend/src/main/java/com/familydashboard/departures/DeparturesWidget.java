package com.familydashboard.departures;

import org.springframework.stereotype.Component;

import com.familydashboard.widget.Widget;
import com.familydashboard.widget.WidgetRegistry;

/**
 * Registers the departures feature as a dashboard widget. Picked up
 * automatically by {@link WidgetRegistry}, which collects all {@link Widget}
 * beans from the application context — same pattern as {@code TodoWidget}/
 * {@code WeatherWidget}.
 */
@Component
public class DeparturesWidget implements Widget {

    @Override
    public String id() {
        return "departures";
    }

    @Override
    public String displayName() {
        return "Abfahrten";
    }
}
