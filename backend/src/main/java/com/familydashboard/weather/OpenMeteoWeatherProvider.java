package com.familydashboard.weather;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The one real {@link WeatherProvider} implementation: calls Open-Meteo's
 * free forecast API (see {@code docs/weather-widget-plan.md}'s "Data
 * source: Open-Meteo" section) and maps its response into our own {@link
 * WeatherSnapshot}, so the upstream JSON shape never leaks past this class.
 *
 * <p>Uses Spring's {@link RestClient} (from {@code spring-web}, already a
 * transitive dependency via {@code spring-boot-starter-web}) — no new
 * dependency needed for the HTTP client itself, only for the WireMock test
 * fixture ({@code OpenMeteoWeatherProviderTest}). Built via the plain {@link
 * RestClient#create()} static factory rather than an injected {@code
 * RestClient.Builder}: unlike Spring Boot 3.x, this Spring Boot 4.1 project
 * doesn't auto-configure a {@code RestClient.Builder} bean from {@code
 * spring-boot-starter-web} alone (confirmed empirically — injecting one
 * failed full-context tests with {@code NoSuchBeanDefinitionException}) —
 * that auto-configuration now lives behind the separate, opt-in {@code
 * spring-boot-starter-restclient} starter (Spring Boot 4.x split its HTTP
 * client support out of the web starter into its own module family:
 * {@code spring-boot-restclient}/{@code -webclient}/{@code -resttestclient},
 * confirmed by browsing Maven Central's {@code org/springframework/boot/}
 * directory). Adding that starter for one internal provider class would be
 * an unnecessary new dependency; {@code RestClient.create()} needs none.
 */
@Component
class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final String DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast";

    /**
     * Pinned per {@code docs/weather-widget-plan.md}'s "Data source: Open-
     * Meteo" section: Karlsruhe's coordinates already route to DWD's ICON-D2
     * model via Open-Meteo's default {@code best_match} selection, but
     * pinning this explicitly guarantees that stays true even if Open-
     * Meteo's default-selection logic ever changes.
     */
    private static final String MODEL = "icon_d2";

    /**
     * This widget only ever shows "today" (current conditions, today's
     * hi/lo, today's sunset, and an hourly strip for the rest of today — see
     * the design handoff's "heute" framing), so the request is scoped to a
     * single forecast day. Not called out explicitly as a query parameter in
     * the plan doc's "Fields needed" list, but a direct consequence of that
     * "today only" scope, and it keeps the hourly array to 24 entries
     * instead of Open-Meteo's 7-day default (168 entries).
     */
    private static final int FORECAST_DAYS = 1;

    /**
     * Also not listed explicitly in the plan doc's "Fields needed" section,
     * but necessary for correctness: without a {@code timezone} parameter,
     * Open-Meteo returns every timestamp (including {@code daily.sunset}
     * and every {@code hourly.time} entry) as UTC rather than local time,
     * which would show a several-hours-wrong sunset time and hourly labels
     * for a Karlsruhe household. Hardcoded rather than derived from {@link
     * WeatherProperties}' lat/lon since this dashboard only ever targets one
     * German timezone (see {@code docs/weather-widget-plan.md}'s "Out of
     * scope" section: no UI for changing location).
     */
    private static final String TIMEZONE = "Europe/Berlin";

    private static final String CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m";
    private static final String DAILY_FIELDS = "temperature_2m_max,temperature_2m_min,sunset";
    private static final String HOURLY_FIELDS = "temperature_2m,precipitation_probability";

    private final RestClient restClient;
    private final WeatherProperties weatherProperties;
    private final String baseUrl;

    @Autowired
    OpenMeteoWeatherProvider(WeatherProperties weatherProperties) {
        this(weatherProperties, DEFAULT_BASE_URL);
    }

    /**
     * Package-private constructor letting {@code OpenMeteoWeatherProviderTest}
     * point {@code baseUrl} at a local WireMock server; production code
     * always goes through the {@code @Autowired} constructor above, which
     * defaults to the real Open-Meteo endpoint. Spring requires exactly one
     * constructor to be marked {@code @Autowired} when a class (like this
     * one) declares more than one, otherwise it falls back to looking for a
     * no-args constructor and fails ("No default constructor found") since
     * this class doesn't have one.
     */
    OpenMeteoWeatherProvider(WeatherProperties weatherProperties, String baseUrl) {
        this.restClient = RestClient.create();
        this.weatherProperties = weatherProperties;
        this.baseUrl = baseUrl;
    }

    @Override
    public WeatherSnapshot fetch() {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("latitude", weatherProperties.latitude())
                .queryParam("longitude", weatherProperties.longitude())
                .queryParam("models", MODEL)
                .queryParam("forecast_days", FORECAST_DAYS)
                .queryParam("timezone", TIMEZONE)
                .queryParam("current", CURRENT_FIELDS)
                .queryParam("daily", DAILY_FIELDS)
                .queryParam("hourly", HOURLY_FIELDS)
                .toUriString();

        OpenMeteoResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(OpenMeteoResponse.class);

        return toSnapshot(response);
    }

    private static WeatherSnapshot toSnapshot(OpenMeteoResponse response) {
        Current current = response.current();
        Daily daily = response.daily();
        Hourly hourly = response.hourly();

        return new WeatherSnapshot(
                current.temperature2m(),
                current.weatherCode(),
                WeatherConditionTexts.forCode(current.weatherCode()),
                daily.temperature2mMax().get(0),
                daily.temperature2mMin().get(0),
                toHourlyForecasts(hourly),
                current.relativeHumidity2m(),
                current.windSpeed10m(),
                LocalDateTime.parse(daily.sunset().get(0)),
                Instant.now());
    }

    private static List<HourlyForecast> toHourlyForecasts(Hourly hourly) {
        List<HourlyForecast> forecasts = new ArrayList<>(hourly.time().size());
        for (int i = 0; i < hourly.time().size(); i++) {
            int hourOfDay = LocalDateTime.parse(hourly.time().get(i)).getHour();
            forecasts.add(new HourlyForecast(
                    hourOfDay,
                    hourly.temperature2m().get(i),
                    hourly.precipitationProbability().get(i)));
        }
        return forecasts;
    }

    /**
     * Only the {@code current}/{@code daily}/{@code hourly} blocks this
     * provider actually reads are mapped here — Open-Meteo's response also
     * carries top-level {@code latitude}/{@code longitude}/{@code
     * generationtime_ms}/{@code timezone}/{@code *_units} fields we don't
     * need, hence {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenMeteoResponse(Current current, Daily daily, Hourly hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Current(
            @JsonProperty("temperature_2m") double temperature2m,
            @JsonProperty("relative_humidity_2m") int relativeHumidity2m,
            @JsonProperty("weather_code") int weatherCode,
            @JsonProperty("wind_speed_10m") double windSpeed10m) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Daily(
            List<String> time,
            @JsonProperty("temperature_2m_max") List<Double> temperature2mMax,
            @JsonProperty("temperature_2m_min") List<Double> temperature2mMin,
            List<String> sunset) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Hourly(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperature2m,
            @JsonProperty("precipitation_probability") List<Integer> precipitationProbability) {
    }
}
