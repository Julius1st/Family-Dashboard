package com.familydashboard.weather;

import java.time.Instant;
import java.time.LocalDate;
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
     * This widget originally only ever showed "today" (current conditions,
     * today's hi/lo, today's sunset, an hourly strip for the rest of today),
     * hence {@code forecast_days=1}. Now also needs a compact 3-day outlook
     * (tomorrow, the day after, and the day after that — see the "next 3
     * days" summary section), so this covers today plus those 3 further
     * days. The hourly strip is still today-only ({@link #toHourlyForecasts}
     * filters the now-4-day-wide {@code hourly} array back down to just
     * today's entries) — only the {@code daily} block actually needs all 4
     * days.
     *
     * <p><b>No {@code models} parameter is pinned</b> — a deliberate
     * reversal of Ticket 1's original choice to pin {@code models=icon_d2},
     * made after live-verifying against the real Open-Meteo API
     * (api.open-meteo.com is reachable from this environment) once this
     * ticket widened the request from 1 to 4 forecast days. With {@code
     * models=icon_d2} pinned and {@code forecast_days=4}, Open-Meteo's
     * {@code daily.temperature_2m_max}/{@code temperature_2m_min}/{@code
     * weather_code} for Karlsruhe came back {@code null} for the last two of
     * the four requested days — confirming ICON-D2's documented ~2-day
     * forecast horizon: it can only support "today" plus one more day, not
     * the 3-day outlook this ticket needs. Dropping the {@code models}
     * parameter entirely (falling back to Open-Meteo's default {@code
     * best_match} selection) returned fully populated, non-null daily values
     * for all 4 requested days for the same coordinates and parameters —
     * {@code best_match} transparently blends ICON-D2 for the near term with
     * a longer-horizon model (e.g. ICON-EU) once ICON-D2's own horizon is
     * exceeded, which is exactly the documented multi-model behaviour this
     * fix relies on. Today's {@code current}/hourly values were byte-for-byte
     * identical between both requests, so this switch costs nothing for the
     * existing "today" display.
     */
    private static final int FORECAST_DAYS = 4;

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

    /**
     * {@code weather_code} added for this ticket: today's condition text
     * already comes from {@code current.weather_code}, but the 3-day outlook
     * needs each future day's own condition, which only exists at the daily
     * level (there's no "current" for a future day).
     */
    private static final String DAILY_FIELDS = "temperature_2m_max,temperature_2m_min,sunset,weather_code";
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
        LocalDate today = LocalDate.parse(daily.time().get(0));

        return new WeatherSnapshot(
                current.temperature2m(),
                current.weatherCode(),
                WeatherConditionTexts.forCode(current.weatherCode()),
                daily.temperature2mMax().get(0),
                daily.temperature2mMin().get(0),
                toHourlyForecasts(hourly, today),
                toOutlook(daily),
                current.relativeHumidity2m(),
                current.windSpeed10m(),
                LocalDateTime.parse(daily.sunset().get(0)),
                Instant.now());
    }

    /**
     * {@code hourly} now spans all {@link #FORECAST_DAYS} days (4), not just
     * today, since the {@code daily} block needs those extra days for the
     * outlook — but the hourly strip itself is still today-only (see the
     * design handoff's "heute" framing), so entries for any other day are
     * filtered back out here rather than left for the frontend to sift
     * through.
     */
    private static List<HourlyForecast> toHourlyForecasts(Hourly hourly, LocalDate today) {
        List<HourlyForecast> forecasts = new ArrayList<>();
        for (int i = 0; i < hourly.time().size(); i++) {
            LocalDateTime dateTime = LocalDateTime.parse(hourly.time().get(i));
            if (!dateTime.toLocalDate().equals(today)) {
                continue;
            }
            forecasts.add(new HourlyForecast(
                    dateTime.getHour(),
                    hourly.temperature2m().get(i),
                    hourly.precipitationProbability().get(i)));
        }
        return forecasts;
    }

    /**
     * Indices 1, 2 and 3 of {@code daily} — tomorrow, the day after, and the
     * day after that. Index 0 (today) is deliberately skipped: it's already
     * represented by {@link WeatherSnapshot#highTemperature()}/{@link
     * WeatherSnapshot#lowTemperature()}/{@link WeatherSnapshot#conditionText()}
     * /{@link WeatherSnapshot#hourly()}, so including it here too would
     * duplicate today's data under a second name.
     */
    private static List<DailyForecast> toOutlook(Daily daily) {
        List<DailyForecast> outlook = new ArrayList<>();
        for (int i = 1; i < daily.time().size(); i++) {
            outlook.add(new DailyForecast(
                    LocalDate.parse(daily.time().get(i)),
                    WeatherConditionTexts.forCode(daily.weatherCode().get(i)),
                    daily.temperature2mMax().get(i),
                    daily.temperature2mMin().get(i)));
        }
        return outlook;
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
            List<String> sunset,
            @JsonProperty("weather_code") List<Integer> weatherCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Hourly(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperature2m,
            @JsonProperty("precipitation_probability") List<Integer> precipitationProbability) {
    }
}
