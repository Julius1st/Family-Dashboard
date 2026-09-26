package com.familydashboard.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Confirms {@link WeatherWidget} is auto-discovered by {@code WidgetRegistry}
 * (unchanged) purely by being a {@code @Component}-annotated {@code Widget}
 * bean, and surfaces over the existing {@code GET /api/widgets} endpoint
 * with no registry or controller changes needed — same proof as {@code
 * TodoWidgetRegistrationTest} for the todo widget.
 *
 * <p>Uses the full application context ({@code @SpringBootTest}), so both
 * the todo and weather widgets end up registered; this only asserts the
 * weather entry's presence, without assuming an exact response size or the
 * two entries' relative order (not part of {@code WidgetRegistry}'s
 * contract).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WeatherWidgetRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getWidgetsIncludesTheWeatherWidget() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/widgets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        List<Map<String, Object>> widgets = new ObjectMapper()
                .readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
                });

        assertThat(widgets).contains(Map.of("id", "weather", "displayName", "Wetter"));
    }
}
