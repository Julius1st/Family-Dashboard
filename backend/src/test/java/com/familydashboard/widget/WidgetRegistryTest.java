package com.familydashboard.widget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class WidgetRegistryTest {

    @Test
    void getAllReturnsAllRegisteredWidgets() {
        Widget todo = new FakeWidget("todo", "Todo");
        Widget weather = new FakeWidget("weather", "Weather");

        WidgetRegistry registry = new WidgetRegistry(List.of(todo, weather));

        assertThat(registry.getAll()).containsExactlyInAnyOrder(todo, weather);
    }

    @Test
    void findByIdReturnsTheMatchingWidget() {
        Widget todo = new FakeWidget("todo", "Todo");
        Widget weather = new FakeWidget("weather", "Weather");

        WidgetRegistry registry = new WidgetRegistry(List.of(todo, weather));

        assertThat(registry.findById("weather")).contains(weather);
    }

    @Test
    void findByIdReturnsEmptyWhenNoWidgetHasThatId() {
        WidgetRegistry registry = new WidgetRegistry(List.of(new FakeWidget("todo", "Todo")));

        assertThat(registry.findById("does-not-exist")).isEmpty();
    }

    @Test
    void constructorRejectsDuplicateIds() {
        Widget first = new FakeWidget("todo", "Todo");
        Widget second = new AnotherFakeWidget("todo", "Also Todo");

        assertThatThrownBy(() -> new WidgetRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("todo");
    }

    @Test
    void emptyRegistryHasNoWidgets() {
        WidgetRegistry registry = new WidgetRegistry(List.of());

        assertThat(registry.getAll()).isEmpty();
        assertThat(registry.findById("anything")).isEqualTo(Optional.empty());
    }

    /**
     * First fake {@link Widget} test double used across these tests.
     */
    private static final class FakeWidget implements Widget {
        private final String id;
        private final String displayName;

        FakeWidget(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }
    }

    /**
     * Second, distinct fake {@link Widget} test double implementation, used to
     * prove duplicate-id rejection isn't accidentally keyed off the
     * implementation class rather than the id.
     */
    private static final class AnotherFakeWidget implements Widget {
        private final String id;
        private final String displayName;

        AnotherFakeWidget(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }
    }
}
