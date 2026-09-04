package com.familydashboard.widget;

import java.util.Collection;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the registered {@link Widget}s over HTTP for the dashboard shell.
 */
@RestController
public class WidgetController {

    private final WidgetRegistry widgetRegistry;

    public WidgetController(WidgetRegistry widgetRegistry) {
        this.widgetRegistry = widgetRegistry;
    }

    @GetMapping("/api/widgets")
    public List<WidgetDescriptor> getWidgets() {
        return toDescriptors(widgetRegistry.getAll());
    }

    private static List<WidgetDescriptor> toDescriptors(Collection<Widget> widgets) {
        return widgets.stream()
                .map(widget -> new WidgetDescriptor(widget.id(), widget.displayName()))
                .toList();
    }
}
