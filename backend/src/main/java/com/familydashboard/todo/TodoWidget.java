package com.familydashboard.todo;

import org.springframework.stereotype.Component;

import com.familydashboard.widget.Widget;
import com.familydashboard.widget.WidgetRegistry;

/**
 * Registers the Todo feature as a dashboard widget. Picked up automatically
 * by {@link WidgetRegistry}, which collects all {@link Widget} beans from
 * the application context.
 */
@Component
public class TodoWidget implements Widget {

    @Override
    public String id() {
        return "todo";
    }

    @Override
    public String displayName() {
        return "Todo Lists";
    }
}
