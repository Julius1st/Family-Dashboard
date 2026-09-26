package com.familydashboard.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Proves {@link WeatherCache} keeps serving the last good {@link
 * WeatherSnapshot} when a scheduled refresh fails, per {@code
 * docs/weather-widget-plan.md}'s "on a failed refresh keep the last values"
 * requirement — never an error or an empty state, and never a crashed
 * scheduler.
 */
class WeatherCacheTest {

    @Test
    void currentIsEmptyBeforeAnyRefreshHasSucceeded() {
        WeatherProvider weatherProvider = mock(WeatherProvider.class);
        WeatherCache cache = new WeatherCache(weatherProvider);

        assertThat(cache.current()).isEmpty();
    }

    @Test
    void isStaleIsFalseBeforeAnyRefreshHasBeenAttempted() {
        WeatherProvider weatherProvider = mock(WeatherProvider.class);
        WeatherCache cache = new WeatherCache(weatherProvider);

        assertThat(cache.isStale()).isFalse();
    }

    @Test
    void refreshPopulatesTheCacheFromTheProvider() {
        WeatherProvider weatherProvider = mock(WeatherProvider.class);
        WeatherSnapshot snapshot = aSnapshot(18.4);
        when(weatherProvider.fetch()).thenReturn(snapshot);
        WeatherCache cache = new WeatherCache(weatherProvider);

        cache.refresh();

        assertThat(cache.current()).contains(snapshot);
        assertThat(cache.isStale()).isFalse();
    }

    @Test
    void aFailedRefreshKeepsServingThePreviousSnapshotInsteadOfAnErrorOrEmptyStateAndMarksItStale() {
        WeatherProvider weatherProvider = mock(WeatherProvider.class);
        WeatherSnapshot goodSnapshot = aSnapshot(18.4);
        when(weatherProvider.fetch()).thenReturn(goodSnapshot);
        WeatherCache cache = new WeatherCache(weatherProvider);
        cache.refresh();

        when(weatherProvider.fetch()).thenThrow(new RuntimeException("Open-Meteo is unreachable"));

        assertThatCode(cache::refresh).doesNotThrowAnyException();
        assertThat(cache.current()).contains(goodSnapshot);
        assertThat(cache.isStale()).isTrue();
    }

    @Test
    void aSuccessfulRefreshAfterAFailedOneClearsTheStaleFlag() {
        WeatherProvider weatherProvider = mock(WeatherProvider.class);
        WeatherSnapshot goodSnapshot = aSnapshot(18.4);
        when(weatherProvider.fetch()).thenReturn(goodSnapshot);
        WeatherCache cache = new WeatherCache(weatherProvider);
        cache.refresh();
        when(weatherProvider.fetch()).thenThrow(new RuntimeException("Open-Meteo is unreachable"));
        cache.refresh();
        assertThat(cache.isStale()).isTrue();

        // doReturn(...).when(...), not when(...).thenReturn(...): the mock is
        // still stubbed to throw at this point, and when(mock.fetch())
        // would invoke that throwing stub immediately while evaluating its
        // own argument, before Mockito gets a chance to re-stub it.
        WeatherSnapshot freshSnapshot = aSnapshot(19.1);
        doReturn(freshSnapshot).when(weatherProvider).fetch();
        cache.refresh();

        assertThat(cache.current()).contains(freshSnapshot);
        assertThat(cache.isStale()).isFalse();
    }

    @Test
    void aFailedFirstRefreshLeavesTheCacheEmptyRatherThanErroring() {
        WeatherProvider weatherProvider = mock(WeatherProvider.class);
        when(weatherProvider.fetch()).thenThrow(new RuntimeException("Open-Meteo is unreachable"));
        WeatherCache cache = new WeatherCache(weatherProvider);

        assertThatCode(cache::refresh).doesNotThrowAnyException();
        assertThat(cache.current()).isEmpty();
    }

    private static WeatherSnapshot aSnapshot(double currentTemperature) {
        return new WeatherSnapshot(
                currentTemperature,
                3,
                "Bedeckt",
                21.3,
                9.0,
                List.of(new HourlyForecast(13, 20.6, 5)),
                62,
                11.2,
                LocalDateTime.of(2026, 9, 26, 19, 32),
                Instant.now());
    }
}
