# Departures Widget Plan

**Status: implemented, pending merge** (`com.familydashboard.departures`,
`DeparturesPanel`). MobiData BW access was granted after this doc was
written — `transit.*` in `application.yml`/`backend/.env` is now filled in
for the real "Wolfartsweierer Straße" stop (see README's "Configuration"
section). This doc reflects the plan as originally approved, not every
follow-up refinement made after Ticket 4 against real live data (e.g. the
"Bstg." bus-platform label handling, the real stop name replacing a
hardcoded placeholder, a timezone bug in the countdown, 60-second polling,
KVV's own per-line badge colors) — read the current code/tests for exact
final behavior.

This breaks the departures widget (Phase 5 per `docs/PLAN.md`) into
ticket-sized units of work, mirroring `archive/phase-3-plan.md`'s structure
for the Todo widget. The visual design is already fully specified in
[`docs/design_handoff_family_dashboard/README.md`](design_handoff_family_dashboard/README.md)'s
"Screen 2 — Abfahrten · Wetter" section (Departures widget) — this doc is
about the data side: which API, the backend provider pattern, DTOs.
`frontend/src/app/transit-weather-page.html` already has a placeholder
slot (`.transit-weather-page__slot--departures`) waiting to be replaced.

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order. This doc is self-contained: a fresh implementer agent should be
able to work a ticket from this file alone.

## Data source: TRIAS API via MobiData BW

Free, state-wide (Baden-Württemberg, covers KVV/Karlsruhe) real-time
departures API, based on the VDV-standardized "TRIAS" interface (spec
431-2). Access is by email request to `mobidata-bw@nvbw.de` — **in
progress, not yet granted as of this doc's writing.** This does not block
Tickets 1–3 below (see "Working without credentials yet").

The protocol is XML (not JSON/REST), and fully publicly documented with
no credentials needed to read the spec itself:
- XSD schema: [github.com/VDVde/TRIAS](https://github.com/VDVde/TRIAS)
  (`Trias.xsd` + module files — `Trias_StopEvents.xsd` is the relevant
  one for departure/arrival queries).
- A real example `StopEventRequest`/response in
  [VDVde/TRIAS#1](https://github.com/VDVde/TRIAS/issues/1) — the exact
  query shape needed ("next departures at stop X"), with params like
  `NumberOfResults`, `StopEventType`, `IncludeRealtimeData`.
- MobiData BW's own example queries and interface description, linked
  from their [TRIAS dataset page](https://mobidata-bw.de/dataset/trias/resource/ca2587e3-f497-4dea-a2f9-176a58bd0c61),
  licensed "Datenlizenz Deutschland – Namensnennung 2.0."
- A real reference implementation to skim (not copy): [OpenTripPlanner's
  TRIAS client](https://docs.opentripplanner.org/en/latest/sandbox/TriasApi/)
  (Java, same ecosystem as this backend).

**Confirmed**: MobiData BW's own documentation states the data is
"in der gleichen Qualität... wie die Verkehrsunternehmen zur
Fahrgastinformation einsetzen" (the same quality the transport companies
use for their own passenger information) — i.e. this is not a reduced
copy of KVV's real-time data, it's the same underlying live source KVV's
own system produces, just accessed via a shared statewide front door.
Going directly to KVV instead (same protocol, different registration
email) would not provide materially different data.

## Working without credentials yet

`CLAUDE.md`'s own convention already mandates WireMock-based tests
against captured example payloads for external calls like this — "never
the live API" — so this isn't a new constraint. Ticket 1's request-
building and response-parsing logic can be fully implemented and tested
using the publicly available example request/response from
[VDVde/TRIAS#1](https://github.com/VDVde/TRIAS/issues/1) (and the
VRR-region's public, no-registration TRIAS test endpoint,
`openservice-test.vrr.de/opendataT/trias`, if a second real example
response is useful — same protocol, different region's data). The
endpoint URL, auth token, and the actual KVV stop ID are all
`application.yml` placeholders (see below) — fill them in once MobiData
BW responds, no code changes needed at that point.

## Design decisions

- **Config placeholders**: `TransitProperties`
  (`@ConfigurationProperties(prefix = "transit")`) with `endpointUrl`,
  `authToken`/`requestorRef` (TRIAS auth is typically a "RequestorRef"
  identifier issued alongside the endpoint, not a bearer token — confirm
  the exact auth mechanism once MobiData BW's response arrives, and
  adjust this property's name/shape then if needed), and `stopPointRef`
  (KVV's internal stop ID for the one stop this dashboard cares about —
  not yet known; leave empty/placeholder in `application.yml` with a
  comment explaining it needs to come from either MobiData BW's response
  or KVV's own GTFS static feed, `https://projekte.kvv-efa.de/GTFS/
  google_transit.zip`, once we're looking at it for real stop IDs).
  Same "config, not code" pattern as `household.members`.
- **Provider interface**: new package `com.familydashboard.departures`,
  sibling of `com.familydashboard.todo`/`weather`/`widget`. A
  `DepartureProvider` interface (one method, e.g.
  `List<Departure> nextDepartures()`), one real implementation
  `TriasDepartureProvider`. Our own domain type (`Departure`: line,
  destination, platform, scheduled time, expected/real-time time,
  status) never leaks the TRIAS XML shape past the provider.
- **XML handling, no new dependency**: TRIAS requests are simple enough
  to build via string templating (a `StopEventRequest` has a handful of
  parameters, not a deep object graph). Responses are parsed with the
  JDK's built-in `javax.xml.parsers`/DOM/XPath (bundled in the JDK
  itself, no Maven dependency — distinct from JAXB, which was removed
  from the JDK in Java 11+ and would need a new dependency to restore).
  More verbose than a proper XML-binding library, but zero new
  dependencies, consistent with this project's default stance. If this
  turns out to be unreasonably painful once real response payloads are
  in hand, revisit as an explicit, flagged decision — don't silently add
  a dependency.
- **No caching/scheduling** (unlike the weather widget): departures are
  inherently time-sensitive per-request data (a cached "next departure in
  6 minutes" becomes wrong within seconds), so `GET /api/departures`
  calls the provider live on every request, no cache layer. If this needs
  reconsidering once real rate limits are known (MobiData BW's usage
  terms may specify limits), that's a fast-follow, not blocking this
  ticket set.
- **Widget id**: `"departures"`, `displayName`: `"Abfahrten"`, matching
  the design's label and the existing
  `.transit-weather-page__slot--departures` placeholder.
- **Frontend**: `DeparturesService` mirrors `TodoService`'s signal-based
  shape (`Signal<readonly Departure[] | undefined>`). A
  `DeparturesPanel`/similar component replaces `TransitWeatherPage`'s
  departures placeholder `<article>` — the page host itself is untouched
  apart from that swap.

## Ticket 1 — Backend: TRIAS client foundation

**Scope:** data layer only. No HTTP endpoint yet. Fully buildable without
real credentials (see "Working without credentials yet" above).

**Implement:**
- `TransitProperties` bound from new `transit.*` placeholder values in
  `application.yml` (empty/placeholder defaults, documented with a
  comment on where real values come from).
- `Departure` domain type: line, destination, platform, scheduled time,
  expected time (nullable — falls back to scheduled if no real-time data
  available), status (`ON_TIME` / `DELAYED` / `CANCELLED` or similar,
  matching the design's `pünktlich`/`+3 Min`/`fällt aus` states).
- `DepartureProvider` interface.
- `TriasDepartureProvider implements DepartureProvider`: builds a
  `StopEventRequest` XML body from `TransitProperties.stopPointRef`,
  POSTs it to `TransitProperties.endpointUrl`, parses the
  `StopEventResponse` via DOM/XPath into a `List<Departure>`.

**Acceptance criteria:**
- A WireMock-based test for `TriasDepartureProvider` using the publicly
  available example `StopEventRequest`/response (from
  [VDVde/TRIAS#1](https://github.com/VDVde/TRIAS/issues/1), saved as a
  test resource) — asserts the request body is well-formed TRIAS XML
  with the configured stop ref, and that the response parses into the
  correct `Departure` list, covering at least an on-time case, a delayed
  case, and a cancelled case (construct synthetic response XML for the
  cases the one public example doesn't cover, based on the XSD's
  documented structure for delay/cancellation status).
- A test confirming the request-building logic doesn't require real
  `application.yml` values to be non-empty to construct valid XML (i.e.
  it's genuinely testable with placeholder config).

## Ticket 2 — Backend: REST API + widget registration

**Scope:** expose Ticket 1's provider over HTTP; register as a widget.
Depends on Ticket 1.

**Implement:**
- `DeparturesController` exposing `GET /api/departures`, returning a
  `DepartureDto` list (separate from the internal `Departure` domain
  type, matching this project's existing entity/DTO separation pattern)
  shaped to match the design's departures widget fields exactly: line,
  destination, platform (`Gl. X`), status text + tone (for the
  pünktlich/late/cancelled color coding), time + countdown-in-minutes.
- `DeparturesWidget implements Widget` (`id() = "departures"`,
  `displayName() = "Abfahrten"`) — a small `@Component` bean, auto-
  discovered by the existing `WidgetRegistry` (Phase 1, unchanged).
- Sensible error handling for when the real TRIAS endpoint isn't
  reachable/configured yet (e.g. empty `stopPointRef`) — return an empty
  list or a clear error status rather than throwing an unhandled
  exception, so the frontend's "not yet available" placeholder logic (if
  kept as a fallback) has something sane to react to.

**Acceptance criteria:**
- `@WebMvcTest(DeparturesController.class)` with a mocked provider
  covering the success path and the "provider not configured/unreachable"
  path.
- A test confirming `GET /api/widgets` includes
  `{ "id": "departures", "displayName": "Abfahrten" }`.

## Ticket 3 — Frontend: departures API client

**Scope:** typed access to Ticket 2's API. No rendering yet. Depends on
Ticket 2.

**Implement:**
- A `Departure` TypeScript interface matching the backend DTO exactly.
  No `any`.
- `DeparturesService`: fetches `/api/departures` into a signal
  (`Signal<readonly Departure[] | undefined>`), mirroring `TodoService`'s
  pattern.

**Acceptance criteria:**
- `HttpTestingController`-based tests for the fetch (success with
  multiple departures, and an empty-array case treated as normal —
  mirroring the "empty is not an error" contract already established for
  Todo/widgets in this project). No `any`.

## Ticket 4 — Frontend: departures widget component

**Scope:** the real content of the departures slot. Depends on Ticket 3.

**Implement:**
- A component replacing `TransitWeatherPage`'s departures placeholder
  `<article>`, following the design handoff's exact "Departures widget"
  layout (Screen 2): title row (`ABFAHRTEN` / stop name / `Stand
  hh:mm` freshness marker), one row per departure (line badge,
  destination, platform, status with the pünktlich/late/cancelled color
  coding, time + countdown — struck-through/dimmed time for a cancelled
  departure per the design's exact spec).
- A non-blank empty state if the list is genuinely empty (no departures
  in the queried window) — same "empty is not an error" discipline as
  the Todo widget.

**Acceptance criteria:**
- Renders correctly from a mocked `DeparturesService` for a populated
  list (covering on-time/delayed/cancelled rows) and an empty list.
- No `any`; zoneless-compatible; read-only widget (no interactive
  controls, per the design's "Interactions & behavior" section — same as
  weather).
- Manual verification note: this one **cannot** be smoke-tested with real
  data until MobiData BW grants access and `application.yml`'s transit
  placeholders are filled in — call this out explicitly rather than
  implying an end-to-end real-data check happened, unlike the weather
  widget's equivalent ticket.

## Sequencing

1 → 2 → 3 → 4, in order. One branch and one implementer/reviewer loop at
a time, same discipline as every prior ticket set in this project.

## Out of scope

- Multiple stops / a stop picker UI (still config-only, one stop, per
  `docs/PLAN.md`'s "hardcoded in config for v1" pattern).
- Journey planning (A→B routing) — TRIAS supports this, but the design
  only calls for a departure board, not a trip planner.
- Fare information (also supported by TRIAS, not part of the design).
- Any caching/rate-limit handling beyond what's described above — revisit
  once real usage terms/limits are known from MobiData BW.
