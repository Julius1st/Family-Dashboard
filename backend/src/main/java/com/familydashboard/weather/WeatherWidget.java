package com.familydashboard.weather;

import org.springframework.stereotype.Component;

import com.familydashboard.widget.Widget;
import com.familydashboard.widget.WidgetRegistry;

/**
 * Registers the weather feature as a dashboard widget. Picked up
 * automatically by {@link WidgetRegistry}, which collects all {@link Widget}
 * beans from the application context — same pattern as {@code TodoWidget} in
 * the todo package.
 */
@Component
public class WeatherWidget implements Widget {

    @Override
    public String id() {
        return "weather";
    }

    @Override
    public String displayName() {
        return "Wetter";
    }
}
