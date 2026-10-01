package com.familydashboard.departures;

import java.util.List;

/**
 * A {@link DepartureProvider} call's full result: the next departures at the
 * configured stop, plus that stop's own human-readable name.
 *
 * <p><b>Why a wrapper, rather than a {@code stopName} field on {@link
 * Departure}/{@link DepartureDto} itself.</b> TRIAS attaches {@code
 * StopPointName} per-departure (inside each {@code CallAtStop}), never as an
 * independent top-level field of a {@code StopEventResponse} — see {@link
 * TriasDepartureProvider}'s parsing. Putting {@code stopName} on every
 * individual {@link Departure} row instead of here would be both redundant
 * (this dashboard only ever queries one {@code StopPointRef} — see {@code
 * docs/departures-widget-plan.md}'s "Out of scope" section — so every row
 * would carry the exact same value) and fragile: a response with zero
 * departures would then have no row left to carry it on at all, turning a
 * perfectly normal state into one the shape can't represent. Hoisting the
 * name out to this one wrapper instead makes "zero departures, so the name
 * is unknown" a real, directly representable value ({@link #stopName()}
 * simply {@code null}) rather than an edge case requiring a special-cased
 * return type or sentinel row.
 *
 * @param stopName   the configured stop's human-readable name (TRIAS
 *                    {@code StopPointName/Text}), taken from the first
 *                    departure that carries a non-blank one — see {@link
 *                    TriasDepartureProvider#parseStopEventResponse}'s javadoc
 *                    for why "first non-blank wins" is a safe assumption for
 *                    this dashboard specifically — or {@code null} if it
 *                    could not be determined (no departures in the response,
 *                    or the provider call failed entirely; see {@link
 *                    DeparturesController} for how the latter maps to this
 *                    same {@code null}).
 * @param departures the next departures at that stop, in the order the
 *                    provider returned them.
 */
public record DepartureBoard(String stopName, List<Departure> departures) {
}
