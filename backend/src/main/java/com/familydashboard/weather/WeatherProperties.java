package com.familydashboard.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configured location this dashboard shows weather for, sourced from
 * {@code weather.latitude}/{@code weather.longitude} in {@code
 * application.yml}. Config-only for v1: no runtime editing (see {@code
 * docs/weather-widget-plan.md}'s "Out of scope" section). Defaults to
 * Karlsruhe's coordinates.
 *
 * <p>This is an immutable, constructor-bound {@code @ConfigurationProperties}
 * class (a record), which Spring Boot only binds correctly when activated via
 * {@code @EnableConfigurationProperties} (see {@code
 * FamilyDashboardApplication}) rather than plain {@code @Component}
 * scanning — component-scanned beans are constructed by the regular Spring
 * container before binding can run, which constructor binding doesn't
 * support. Same pattern as {@code HouseholdProperties} in the {@code todo}
 * package.
 */
@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(double latitude, double longitude) {
}
