package com.familydashboard.todo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms {@link TodoWidget} is auto-discovered by {@code WidgetRegistry}
 * (Phase 1, unchanged) purely by being a {@code @Component}-annotated
 * {@code Widget} bean, and surfaces over the existing {@code GET
 * /api/widgets} endpoint with no registry or controller changes needed.
 *
 * <p>{@code WidgetControllerTest} (Phase 1) uses a narrow {@code
 * @WebMvcTest} slice with a mocked {@code WidgetRegistry}, so it's
 * unaffected by this new bean; this full-context test is the one that
 * actually proves the wiring end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TodoWidgetRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getWidgetsIncludesTheTodoWidget() throws Exception {
        mockMvc.perform(get("/api/widgets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("todo"))
                .andExpect(jsonPath("$[0].displayName").value("Todo Lists"));
    }
}
