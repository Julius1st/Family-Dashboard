package com.familydashboard.weather;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * WireMock-based test for {@link OpenMeteoWeatherProvider}, per {@code
 * CLAUDE.md}'s "Tests for these use WireMock, never the live API"
 * convention. (Correction to this class's older comment here: this
 * environment's egress firewall was found, while implementing the 3-day
 * outlook ticket, to actually reach {@code api.open-meteo.com} live — used
 * one-off to empirically verify the {@code models=icon_d2} vs. default
 * {@code best_match} question, see {@code OpenMeteoWeatherProvider}'s {@code
 * FORECAST_DAYS} javadoc. That doesn't change this test's own approach: it
 * still uses WireMock, never the live API, for determinism and to keep
 * running offline.)
 *
 * <p><b>Honesty note on the fixture:</b> {@code
 * weather/open-meteo-response.json} is a hand-constructed, realistic
 * example response, built from Open-Meteo's documented response schema (top-
 * level {@code current}/{@code daily}/{@code hourly} objects, each with a
 * {@code time} array and one parallel value array per requested variable),
 * shaped to match real values observed against the live API during the
 * verification above — it is still not a byte-for-byte captured response.
 */
class OpenMeteoWeatherProviderTest {

    private WireMockServer wireMockServer;
    private OpenMeteoWeatherProvider provider;

    @BeforeEach
    void startWireMockServer() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        WeatherProperties weatherProperties = new WeatherProperties(49.0069, 8.4037);
        provider = new OpenMeteoWeatherProvider(weatherProperties, wireMockServer.baseUrl() + "/v1/forecast");
    }

    @AfterEach
    void stopWireMockServer() {
        wireMockServer.stop();
    }

    @Test
    void fetchMapsEveryFieldFromTheOpenMeteoResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(readFixture())));

        Instant before = Instant.now();
        WeatherSnapshot snapshot = provider.fetch();
        Instant after = Instant.now();

        assertThat(snapshot.currentTemperature()).isEqualTo(18.4);
        assertThat(snapshot.weatherCode()).isEqualTo(3);
        assertThat(snapshot.conditionText()).isEqualTo("Bedeckt");
        assertThat(snapshot.highTemperature()).isEqualTo(21.3);
        assertThat(snapshot.lowTemperature()).isEqualTo(9.0);
        assertThat(snapshot.humidityPercent()).isEqualTo(62);
        assertThat(snapshot.windSpeedKmh()).isEqualTo(11.2);
        assertThat(snapshot.sunset()).isEqualTo(LocalDateTime.of(2026, 9, 26, 19, 32));

        // Even though the fixture's hourly block now spans 2 days (48
        // entries, since forecast_days=4 widens the daily block but the
        // hourly strip stays today-only), the snapshot's hourly list must
        // still only have today's 24 entries - proves the day-filter in
        // toHourlyForecasts actually filters, not just happens to work.
        assertThat(snapshot.hourly()).hasSize(24);
        assertThat(snapshot.hourly().get(0).hour()).isEqualTo(0);
        assertThat(snapshot.hourly().get(0).temperature()).isEqualTo(10.1);
        assertThat(snapshot.hourly().get(0).rainProbability()).isEqualTo(5);
        assertThat(snapshot.hourly().get(13).hour()).isEqualTo(13);
        assertThat(snapshot.hourly().get(13).temperature()).isEqualTo(20.6);
        assertThat(snapshot.hourly().get(13).rainProbability()).isEqualTo(5);
        assertThat(snapshot.hourly().get(18).hour()).isEqualTo(18);
        assertThat(snapshot.hourly().get(18).rainProbability()).isEqualTo(30);
        assertThat(snapshot.hourly().get(23).hour()).isEqualTo(23);

        // The 3-day outlook: fixture's daily block has 4 entries (today +
        // 3), and the outlook must map indices 1-3 only - index 0 (today,
        // 2026-09-26) must NOT reappear here, since it's already fully
        // represented by highTemperature/lowTemperature/conditionText/hourly
        // above.
        assertThat(snapshot.outlook()).hasSize(3);
        assertThat(snapshot.outlook()).extracting(DailyForecast::date)
                .containsExactly(
                        LocalDate.of(2026, 9, 27),
                        LocalDate.of(2026, 9, 28),
                        LocalDate.of(2026, 9, 29))
                .doesNotContain(LocalDate.of(2026, 9, 26));

        DailyForecast tomorrow = snapshot.outlook().get(0);
        assertThat(tomorrow.conditionText()).isEqualTo("Leichter Regen"); // WMO code 61, via WeatherConditionTexts
        assertThat(tomorrow.highTemperature()).isEqualTo(19.8);
        assertThat(tomorrow.lowTemperature()).isEqualTo(8.4);

        DailyForecast dayAfterTomorrow = snapshot.outlook().get(1);
        assertThat(dayAfterTomorrow.conditionText()).isEqualTo("Gewitter"); // WMO code 95
        assertThat(dayAfterTomorrow.highTemperature()).isEqualTo(17.5);
        assertThat(dayAfterTomorrow.lowTemperature()).isEqualTo(7.1);

        DailyForecast thirdDay = snapshot.outlook().get(2);
        assertThat(thirdDay.conditionText()).isEqualTo("Überwiegend klar"); // WMO code 1
        assertThat(thirdDay.highTemperature()).isEqualTo(22.1);
        assertThat(thirdDay.lowTemperature()).isEqualTo(10.6);

        // fetchedAt is our own bookkeeping (not an upstream field) - assert
        // it was stamped during this call, not any exact value.
        assertThat(snapshot.fetchedAt()).isBetween(before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
    }

    @Test
    void fetchSendsTheExpectedRequestUrlAndParams() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(readFixture())));

        provider.fetch();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/v1/forecast"))
                .withQueryParam("latitude", equalTo("49.0069"))
                .withQueryParam("longitude", equalTo("8.4037"))
                .withQueryParam("forecast_days", equalTo("4"))
                .withQueryParam("timezone", equalTo("Europe/Berlin"))
                .withQueryParam("current", equalTo("temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"))
                .withQueryParam("daily", equalTo("temperature_2m_max,temperature_2m_min,sunset,weather_code"))
                .withQueryParam("hourly", equalTo("temperature_2m,precipitation_probability"))
                // No `models` param: see FORECAST_DAYS's javadoc in
                // OpenMeteoWeatherProvider for the live-verified reasoning
                // (pinning models=icon_d2 truncates daily data beyond its
                // ~2-day horizon once forecast_days is 4).
                .withQueryParam("models", absent()));
    }

    private static String readFixture() {
        try {
            Path path = new ClassPathResource("weather/open-meteo-response.json").getFile().toPath();
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
