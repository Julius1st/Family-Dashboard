package com.familydashboard.widget;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WidgetController.class)
class WidgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WidgetRegistry widgetRegistry;

    @Test
    void getWidgetsReturnsRegisteredWidgetsAsJson() throws Exception {
        Widget todo = new FakeWidget("todo", "Todo");
        Widget weather = new FakeWidget("weather", "Weather");
        when(widgetRegistry.getAll()).thenReturn(List.of(todo, weather));

        mockMvc.perform(get("/api/widgets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("todo"))
                .andExpect(jsonPath("$[0].displayName").value("Todo"))
                .andExpect(jsonPath("$[1].id").value("weather"))
                .andExpect(jsonPath("$[1].displayName").value("Weather"));
    }

    @Test
    void getWidgetsReturnsEmptyArrayWhenNoWidgetsAreRegistered() throws Exception {
        when(widgetRegistry.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/widgets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Minimal {@link Widget} test double; the controller only ever calls
     * {@link WidgetRegistry#getAll()}, which is mocked, so this never needs
     * to be registered with Spring.
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
}
