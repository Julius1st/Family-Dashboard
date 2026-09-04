package com.familydashboard.widget;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Collects all {@link Widget} beans present in the application context and
 * makes them discoverable by id.
 *
 * <p>Pure Java + Spring DI: no HTTP/JSON concerns live here. Exposing widgets
 * over HTTP is the responsibility of a separate controller.
 */
@Component
public class WidgetRegistry {

    private final Map<String, Widget> widgetsById;

    public WidgetRegistry(List<Widget> widgets) {
        Map<String, Widget> byId = new LinkedHashMap<>();
        for (Widget widget : widgets) {
            Widget existing = byId.putIfAbsent(widget.id(), widget);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate widget id '%s': registered by both %s and %s"
                                .formatted(widget.id(), existing.getClass().getName(), widget.getClass().getName()));
            }
        }
        this.widgetsById = Map.copyOf(byId);
    }

    /**
     * All registered widgets, in no particular guaranteed order.
     */
    public Collection<Widget> getAll() {
        return widgetsById.values();
    }

    /**
     * Looks up a widget by id.
     *
     * @return the widget, or {@link Optional#empty()} if no widget with that
     *         id is registered.
     */
    public Optional<Widget> findById(String id) {
        return Optional.ofNullable(widgetsById.get(id));
    }
}
