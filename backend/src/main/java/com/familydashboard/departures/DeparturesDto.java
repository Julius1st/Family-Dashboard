package com.familydashboard.departures;

import java.util.List;

/**
 * External-facing (JSON) representation of {@link DeparturesController}'s
 * {@code GET /api/departures} response: {@code { stopName, departures }},
 * mirroring {@link DepartureBoard} one-to-one except each {@link Departure}
 * is mapped to its own {@link DepartureDto}, following the same "don't
 * expose the internal domain type directly" discipline as {@code
 * TodoItemDto}/{@link DepartureDto} itself.
 *
 * <p>This is a breaking shape change from the endpoint's previous bare
 * {@code DepartureDto[]} array — there is no external consumer and no API
 * versioning concern for this project yet, so that's fine.
 *
 * <p><b>{@code stopName}'s {@code null} fallback is a frontend decision, not
 * a backend one.</b> When {@link DepartureBoard#stopName()} is {@code null}
 * (zero departures, or {@link DepartureProvider#nextDepartureBoard()}
 * failing entirely — see {@link DeparturesController#getDepartures()}), this
 * DTO passes that {@code null} straight through to JSON rather than
 * substituting placeholder text here. Reasoning: the frontend has a third
 * state this backend doesn't — "the initial fetch hasn't resolved yet" (see
 * {@code DeparturesService}/{@code DeparturesPanel} on the frontend) — so
 * only the frontend can tell "no name known yet because still loading"
 * apart from "no name known because the resolved response genuinely has
 * none," and therefore only the frontend is in a position to decide the
 * right placeholder copy for each of those cases. Keeping this field a
 * literal, honest {@code null} here keeps that decision in one place
 * (matching this project's existing split: this layer already defers
 * loading/empty-state copy — e.g. "Abfahrten werden geladen…" — entirely to
 * {@code DeparturesPanel}, never duplicating it backend-side).
 *
 * @param stopName   the configured stop's human-readable name, or {@code
 *                    null} if it could not be determined — see {@link
 *                    DepartureBoard#stopName()}.
 * @param departures the next departures at that stop, as {@link
 *                    DepartureDto}s.
 */
public record DeparturesDto(String stopName, List<DepartureDto> departures) {
}
