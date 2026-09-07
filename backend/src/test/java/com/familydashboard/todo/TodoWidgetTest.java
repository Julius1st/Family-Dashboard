package com.familydashboard.todo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TodoWidgetTest {

    @Test
    void exposesTheExpectedIdAndDisplayName() {
        TodoWidget widget = new TodoWidget();

        assertThat(widget.id()).isEqualTo("todo");
        assertThat(widget.displayName()).isEqualTo("Todo Lists");
    }
}
