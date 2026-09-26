package com.familydashboard.weather;

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
 * convention — this devcontainer's egress firewall can't reach {@code
 * api.open-meteo.com} at all (see {@code docs/weather-widget-plan.md}'s
 * "Environment constraint" section).
 *
 * <p><b>Honesty note on the fixture:</b> {@code
 * weather/open-meteo-response.json} is a hand-constructed, realistic
 * example response, built from Open-Meteo's documented response schema (top-
 * level {@code current}/{@code daily}/{@code hourly} objects, each with a
 * {@code time} array and one parallel value array per requested variable) —
 * it is <b>not</b> a genuine response captured from the live API, since this
 * sandbox cannot reach {@code api.open-meteo.com} to capture one.
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

        assertThat(snapshot.hourly()).hasSize(24);
        assertThat(snapshot.hourly().get(0).hour()).isEqualTo(0);
        assertThat(snapshot.hourly().get(0).temperature()).isEqualTo(10.1);
        assertThat(snapshot.hourly().get(0).rainProbability()).isEqualTo(5);
        assertThat(snapshot.hourly().get(13).hour()).isEqualTo(13);
        assertThat(snapshot.hourly().get(13).temperature()).isEqualTo(20.6);
        assertThat(snapshot.hourly().get(13).rainProbability()).isEqualTo(5);
        assertThat(snapshot.hourly().get(18).hour()).isEqualTo(18);
        assertThat(snapshot.hourly().get(18).rainProbability()).isEqualTo(30);

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
                .withQueryParam("models", equalTo("icon_d2"))
                .withQueryParam("forecast_days", equalTo("1"))
                .withQueryParam("timezone", equalTo("Europe/Berlin"))
                .withQueryParam("current", equalTo("temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"))
                .withQueryParam("daily", equalTo("temperature_2m_max,temperature_2m_min,sunset"))
                .withQueryParam("hourly", equalTo("temperature_2m,precipitation_probability")));
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
