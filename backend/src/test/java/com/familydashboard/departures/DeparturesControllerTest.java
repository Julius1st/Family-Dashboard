package com.familydashboard.departures;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link DeparturesController}, with {@link
 * DepartureProvider} mocked via {@code @MockitoBean} — same pattern as
 * {@code WeatherControllerTest}.
 */
@WebMvcTest(DeparturesController.class)
class DeparturesControllerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartureProvider departureProvider;

    @Test
    void getDeparturesMapsOnTimeDelayedAndCancelledDeparturesToTheDesignsDtoShape() throws Exception {
        // Anchored to "now" (rather than fixed literal timestamps) so this
        // test's countdown-minutes assertions hold regardless of when it
        // runs. Countdown truncates down (Duration#toMinutes()), so a
        // departure scheduled "10 minutes from now" reads as 9 by the time
        // the controller computes its own now() a few milliseconds later -
        // this is the intended "9 min 59 sec away still shows 9, not a
        // rounded-up 10" behaviour, not a test tolerance workaround.
        LocalDateTime now = LocalDateTime.now(ZONE);

        // Departure.platform() now holds the already fully-formatted display
        // label (see its javadoc) - TriasDepartureProvider.normalizePlatform()
        // is what decides "Gl. " vs "Bstg. ", so these test doubles supply
        // that final string directly rather than a bare value.
        Departure onTime = new Departure(
                "S2", "Bad Herrenalb", "Gl. 3", now.plusMinutes(10), null, DepartureStatus.ON_TIME);
        Departure delayed = new Departure(
                "5", "Rheinstetten Rathaus", "Gl. 2", now.plusMinutes(5), now.plusMinutes(8), DepartureStatus.DELAYED);
        Departure cancelled = new Departure(
                "2", "Knielingen", null, now.plusMinutes(12), null, DepartureStatus.CANCELLED);
        when(departureProvider.nextDepartureBoard())
                .thenReturn(new DepartureBoard("Wolfartsweierer Straße", List.of(onTime, delayed, cancelled)));

        mockMvc.perform(get("/api/departures"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stopName").value("Wolfartsweierer Straße"))
                .andExpect(jsonPath("$.departures.length()").value(3))
                // On time: real time shown (falls back to scheduled since no
                // real-time data), countdown floored to 9 (see comment above).
                .andExpect(jsonPath("$.departures[0].line").value("S2"))
                .andExpect(jsonPath("$.departures[0].destination").value("Bad Herrenalb"))
                .andExpect(jsonPath("$.departures[0].platform").value("Gl. 3"))
                .andExpect(jsonPath("$.departures[0].statusText").value("pünktlich"))
                .andExpect(jsonPath("$.departures[0].statusTone").value("ok"))
                .andExpect(jsonPath("$.departures[0].time").value(now.plusMinutes(10).toString()))
                .andExpect(jsonPath("$.departures[0].countdownMinutes").value(9))
                // Delayed: statusText shows the exact scheduled-to-expected
                // delta (3 min), independent of "now"; time shown is the
                // real-time estimate, not the scheduled time.
                .andExpect(jsonPath("$.departures[1].line").value("5"))
                .andExpect(jsonPath("$.departures[1].destination").value("Rheinstetten Rathaus"))
                .andExpect(jsonPath("$.departures[1].platform").value("Gl. 2"))
                .andExpect(jsonPath("$.departures[1].statusText").value("+3 Min"))
                .andExpect(jsonPath("$.departures[1].statusTone").value("late"))
                .andExpect(jsonPath("$.departures[1].time").value(now.plusMinutes(8).toString()))
                .andExpect(jsonPath("$.departures[1].countdownMinutes").value(7))
                // Cancelled: no platform data upstream falls back to "Gl. –",
                // time is still the scheduled time (for the frontend's
                // struck-through display), and countdownMinutes is null
                // ("-" in the design) rather than a number.
                .andExpect(jsonPath("$.departures[2].line").value("2"))
                .andExpect(jsonPath("$.departures[2].destination").value("Knielingen"))
                .andExpect(jsonPath("$.departures[2].platform").value("Gl. –"))
                .andExpect(jsonPath("$.departures[2].statusText").value("fällt aus"))
                .andExpect(jsonPath("$.departures[2].statusTone").value("cancelled"))
                .andExpect(jsonPath("$.departures[2].time").value(now.plusMinutes(12).toString()))
                .andExpect(jsonPath("$.departures[2].countdownMinutes").doesNotExist());
    }

    @Test
    void getDeparturesReturnsAnEmptyBoardRatherThanFailingWhenTheProviderIsNotConfiguredOrUnreachable() throws Exception {
        when(departureProvider.nextDepartureBoard())
                .thenThrow(new IllegalStateException("simulated: blank/unreachable TRIAS endpoint"));

        mockMvc.perform(get("/api/departures"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stopName\":null,\"departures\":[]}"));
    }

    @Test
    void getDeparturesReturnsANullStopNameAndEmptyListWhenTheProviderGenuinelyHasNoDepartures() throws Exception {
        when(departureProvider.nextDepartureBoard()).thenReturn(new DepartureBoard(null, List.of()));

        mockMvc.perform(get("/api/departures"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stopName\":null,\"departures\":[]}"));
    }
}
