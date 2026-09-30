package com.familydashboard.departures;

import java.time.LocalDateTime;

/**
 * Our own domain representation of one upcoming departure at the configured
 * stop, as returned by a {@link DepartureProvider} — the upstream TRIAS XML
 * shape never leaks past {@link TriasDepartureProvider}, per this project's
 * provider/DTO convention (see {@code CLAUDE.md}). Plain Java, not a JPA
 * entity: departures are inherently time-sensitive, live-fetched data, never
 * persisted to H2 (see {@code docs/departures-widget-plan.md}'s "No
 * caching/scheduling" design decision).
 *
 * @param line          the published line name/number as shown to
 *                      passengers, e.g. {@code "S2"} or {@code "5"} (TRIAS
 *                      {@code PublishedLineName}, falling back to the raw
 *                      {@code LineRef} if no published name is given).
 * @param destination   the service's destination text, e.g. {@code "Bad
 *                      Herrenalb"} (TRIAS {@code DestinationText}).
 * @param platform      the boarding bay/platform, e.g. {@code "3"} (TRIAS
 *                      {@code EstimatedBay} if real-time data narrowed it
 *                      down, otherwise the planned {@code PlannedBay}), or
 *                      {@code null} if the response carries neither.
 * @param scheduledTime the timetabled departure time (TRIAS {@code
 *                      ServiceDeparture/TimetabledTime}).
 * @param expectedTime  the real-time estimated departure time (TRIAS {@code
 *                      ServiceDeparture/EstimatedTime}), or {@code null} when
 *                      no real-time data is available for this departure —
 *                      callers displaying a single time to the passenger
 *                      should fall back to {@code scheduledTime} in that
 *                      case, matching the design's "no realtime info yet
 *                      still shows a time" expectation.
 * @param status        {@link DepartureStatus#CANCELLED} if the service was
 *                      cancelled, else {@link DepartureStatus#DELAYED} if
 *                      {@code expectedTime} is later than {@code
 *                      scheduledTime}, else {@link DepartureStatus#ON_TIME}.
 */
public record Departure(
        String line,
        String destination,
        String platform,
        LocalDateTime scheduledTime,
        LocalDateTime expectedTime,
        DepartureStatus status) {
}
