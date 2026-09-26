package com.familydashboard.weather;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link WeatherController}, with {@link
 * WeatherCache} mocked via {@code @MockitoBean} (the project's replacement
 * for the removed {@code @MockBean}, per {@code TodoControllerTest}'s
 * precedent) rather than exercising the real scheduled cache.
 */
@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherCache weatherCache;

    @Test
    void getWeatherReturnsTheFreshSnapshotAsJson() throws Exception {
        WeatherSnapshot snapshot = new WeatherSnapshot(
                18.4,
                3,
                "Bedeckt",
                21.3,
                9.0,
                List.of(new HourlyForecast(13, 20.6, 5, 0.0), new HourlyForecast(14, 21.0, 10, 0.4)),
                List.of(
                        new DailyForecast(LocalDate.of(2026, 9, 27), "Leichter Regen", 19.8, 8.4, 20, 1.2),
                        new DailyForecast(LocalDate.of(2026, 9, 28), "Gewitter", 17.5, 7.1, 90, 8.5),
                        new DailyForecast(LocalDate.of(2026, 9, 29), "Überwiegend klar", 22.1, 10.6, 5, 0.3)),
                62,
                11.2,
                LocalDateTime.of(2026, 9, 26, 19, 42),
                Instant.parse("2026-09-26T12:00:00Z"));
        when(weatherCache.current()).thenReturn(Optional.of(snapshot));
        when(weatherCache.isStale()).thenReturn(false);

        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.currentTemperature").value(18.4))
                .andExpect(jsonPath("$.conditionText").value("Bedeckt"))
                .andExpect(jsonPath("$.highTemperature").value(21.3))
                .andExpect(jsonPath("$.lowTemperature").value(9.0))
                .andExpect(jsonPath("$.hourly").isArray())
                .andExpect(jsonPath("$.hourly[0].hour").value(13))
                .andExpect(jsonPath("$.hourly[0].temperature").value(20.6))
                .andExpect(jsonPath("$.hourly[0].rainProbability").value(5))
                .andExpect(jsonPath("$.hourly[0].rainAmountMm").value(0.0))
                .andExpect(jsonPath("$.hourly[1].hour").value(14))
                .andExpect(jsonPath("$.hourly[1].rainAmountMm").value(0.4))
                .andExpect(jsonPath("$.outlook").isArray())
                .andExpect(jsonPath("$.outlook[0].date").value("2026-09-27"))
                .andExpect(jsonPath("$.outlook[0].conditionText").value("Leichter Regen"))
                .andExpect(jsonPath("$.outlook[0].highTemperature").value(19.8))
                .andExpect(jsonPath("$.outlook[0].lowTemperature").value(8.4))
                .andExpect(jsonPath("$.outlook[0].rainProbability").value(20))
                .andExpect(jsonPath("$.outlook[0].rainAmountMm").value(1.2))
                .andExpect(jsonPath("$.outlook[2].date").value("2026-09-29"))
                .andExpect(jsonPath("$.humidityPercent").value(62))
                .andExpect(jsonPath("$.windSpeedKmh").value(11.2))
                .andExpect(jsonPath("$.sunset").value("2026-09-26T19:42:00"))
                .andExpect(jsonPath("$.fetchedAt").value("2026-09-26T12:00:00Z"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void getWeatherReturnsTheLastKnownGoodSnapshotMarkedStaleWhenTheLastRefreshFailed() throws Exception {
        Instant oldFetchedAt = Instant.now().minus(2, ChronoUnit.HOURS);
        WeatherSnapshot staleSnapshot = new WeatherSnapshot(
                15.0,
                61,
                "Leichter Regen",
                17.0,
                10.0,
                List.of(new HourlyForecast(9, 14.5, 40, 0.9)),
                List.of(new DailyForecast(LocalDate.of(2026, 9, 27), "Bedeckt", 16.0, 9.5, 65, 3.1)),
                70,
                8.5,
                LocalDateTime.of(2026, 9, 26, 19, 40),
                oldFetchedAt);
        when(weatherCache.current()).thenReturn(Optional.of(staleSnapshot));
        when(weatherCache.isStale()).thenReturn(true);

        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.currentTemperature").value(15.0))
                .andExpect(jsonPath("$.fetchedAt").value(oldFetchedAt.toString()))
                .andExpect(jsonPath("$.stale").value(true));
    }

    @Test
    void getWeatherReturns503WhenNoRefreshHasEverSucceededYet() throws Exception {
        when(weatherCache.current()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isServiceUnavailable());
    }
}
