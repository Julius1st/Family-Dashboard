package com.familydashboard.todo;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configured list of household members, sourced from {@code
 * household.members} in {@code application.yml}. Config-only for v1: no
 * runtime editing (see {@code docs/phase-3-plan.md}).
 *
 * <p>This is an immutable, constructor-bound {@code @ConfigurationProperties}
 * class (a record), which Spring Boot only binds correctly when activated via
 * {@code @EnableConfigurationProperties} (see {@code
 * FamilyDashboardApplication}) rather than plain {@code @Component}
 * scanning — component-scanned beans are constructed by the regular Spring
 * container before binding can run, which constructor binding doesn't
 * support.
 */
@ConfigurationProperties(prefix = "household")
public record HouseholdProperties(List<String> members) {
}
