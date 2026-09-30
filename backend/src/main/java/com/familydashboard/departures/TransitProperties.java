package com.familydashboard.departures;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configured TRIAS (VDV 431-2) connection this dashboard fetches
 * departures from, sourced from {@code transit.*} in {@code
 * application.yml}. Config-only for v1: no runtime editing, one stop only
 * (see {@code docs/departures-widget-plan.md}'s "Out of scope" section).
 *
 * <p>All three values are placeholders as of this ticket — MobiData BW
 * (email request to {@code mobidata-bw@nvbw.de}) had not yet granted access
 * at the time this was written — see {@code application.yml}'s {@code
 * transit.*} comments for where real values come from. Fully empty/blank
 * values must still let {@link TriasDepartureProvider} build well-formed
 * request XML (just against an empty stop ref), so the request-building
 * logic can be developed and tested ahead of real credentials.
 *
 * <p>This is an immutable, constructor-bound {@code @ConfigurationProperties}
 * class (a record), which Spring Boot only binds correctly when activated via
 * {@code @EnableConfigurationProperties} (see {@code
 * FamilyDashboardApplication}) rather than plain {@code @Component}
 * scanning — component-scanned beans are constructed by the regular Spring
 * container before binding can run, which constructor binding doesn't
 * support. Same pattern as {@code HouseholdProperties}/{@code
 * WeatherProperties}.
 *
 * @param endpointUrl  the TRIAS {@code StopEventRequest} endpoint to POST to.
 * @param requestorRef TRIAS auth is typically a "RequestorRef" identifier
 *                      issued alongside the endpoint, not a bearer token —
 *                      confirm the exact mechanism once MobiData BW responds,
 *                      and adjust this property's name/shape then if needed.
 *                      May be blank; {@link TriasDepartureProvider} omits the
 *                      {@code <RequestorRef>} element entirely rather than
 *                      sending an empty one when this is blank.
 * @param stopPointRef  KVV's internal stop ID for the one stop this
 *                      dashboard cares about.
 */
@ConfigurationProperties(prefix = "transit")
public record TransitProperties(String endpointUrl, String requestorRef, String stopPointRef) {
}
