# Weather Widget Plan

This breaks the weather widget (Phase 5 per `docs/PLAN.md`) into
ticket-sized units of work, mirroring `archive/phase-3-plan.md`'s structure
for the Todo widget. The visual design is already fully specified in
[`docs/design_handoff_family_dashboard/README.md`](design_handoff_family_dashboard/README.md)'s
"Screen 2 — Abfahrten · Wetter" section (Weather widget) — this doc is
about the data side: which API, the backend provider pattern, DTOs.
`frontend/src/app/transit-weather-page.html` already has a placeholder
slot (`.transit-weather-page__slot--weather`) waiting to be replaced.

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order. This doc is self-contained: a fresh implementer agent should be
able to work a ticket from this file alone.

## Data source: Open-Meteo

`https://api.open-meteo.com/v1/forecast` — zero API key, zero
registration, zero credit card, free for non-commercial use up to 10,000
calls/day (well beyond a single household dashboard's needs).

**Regional data quality, checked specifically for Karlsruhe, not just API
terms/pricing**: Open-Meteo doesn't run its own weather model — it's an
API layer over 15+ national weather services' own models. For a German
location it automatically routes to **DWD's own ICON-D2 model** (Germany's
national weather service's high-resolution regional model, ~2.2 km grid,
covering Germany/Switzerland/Austria/Benelux, updated every 3 hours, with
15-minutely nowcast blending against Météo-France's AROME model for the
near-term). Karlsruhe's coordinates fall inside ICON-D2's coverage area,
and the default `best_match` endpoint routes there with no extra
parameter — confirmed via Open-Meteo's own docs. This is the same model
DWD itself runs, not a generic worldwide average. Pin `models=icon_d2`
explicitly in the request for a deterministic guarantee this stays true
even if Open-Meteo's default-selection logic ever changes.

Honest caveat: this is model/nowcast data, not a live observed-station
reading — true of most consumer weather APIs, worth knowing rather than
implying it's a station sensor.

**Fields needed** (confirmed available):
- `current`: `temperature_2m`, `relative_humidity_2m`, `weather_code`,
  `wind_speed_10m`
- `daily`: `temperature_2m_max`, `temperature_2m_min`, `sunset`
- `hourly`: `temperature_2m`, `precipitation_probability`

`weather_code` is a WMO code (0–99); needs a small static map to German
condition text (e.g. "Wolkig", "Regen", "Gewitter" — the design's exact
mock copy "Wolkig, später sonnig" is illustrative flavor text, not
something to reproduce literally from a single code).

## Design decisions

- **Location config**: Karlsruhe's coordinates (`weather.latitude`/
  `weather.longitude` in `application.yml`, e.g. `49.0069`/`8.4037`) —
  config, not code, same "hardcoded in config for v1" pattern as
  `household.members`.
- **Provider interface** (per `CLAUDE.md`'s stated convention): a new
  package `com.familydashboard.weather`, sibling of `com.familydashboard.
  todo`/`widget`. A `WeatherProvider` interface wrapping the actual HTTP
  call, one real implementation `OpenMeteoWeatherProvider`, our own
  domain type (`WeatherSnapshot` or similar) — the upstream JSON shape
  never leaks past the provider. WireMock tests seeded with a real
  captured Open-Meteo response.
- **Caching, not per-request live calls**: the design's own "Interactions
  & behavior" section requires "on a failed refresh keep the last values
  and mark them stale rather than blanking the widget" — which needs
  *some* persisted last-known-good state regardless of call volume
  concerns. Approach: an in-memory cached snapshot, refreshed on a fixed
  schedule (`@Scheduled`, e.g. every 15 minutes) via the provider;
  `GET /api/weather` always serves the cache instantly, including a
  `fetchedAt` timestamp; a failed scheduled refresh just leaves the old
  snapshot (and its now-older timestamp) in place rather than erroring.
  **This is a recommendation, not a locked decision** — flag to the user
  if a simpler lazy-fetch-with-TTL (no scheduler) is preferred instead.
- **Widget id**: `"weather"`, `displayName`: `"Wetter"`, matching the
  design's label and the existing `.transit-weather-page__slot--weather`
  placeholder.
- **Frontend**: `WeatherService` mirrors `TodoService`'s signal-based
  shape (a `Signal<WeatherSnapshot | undefined>`, `undefined` = not yet
  resolved). A `WeatherWidgetTile`/`WeatherPanel` component replaces
  `TransitWeatherPage`'s weather placeholder `<article>` — the page host
  itself is untouched apart from that swap.

## Environment constraint

This devcontainer's egress firewall doesn't reach `api.open-meteo.com`
(only the Anthropic API, npm, Maven Central, Gradle distributions, and
GitHub are reachable) — so `OpenMeteoWeatherProvider`'s parsing/mapping
logic is fully buildable and testable here via WireMock against a
captured real response, but a live call can't be exercised in this
sandbox. Unlike the departures widget, this needs no credentials at all,
so a live smoke-test is trivial for the user to run themselves once
merged — just `curl https://api.open-meteo.com/v1/forecast?...` or run
the app with real network access.

## Ticket 1 — Backend: weather provider + domain model

**Scope:** data layer only. No HTTP endpoint yet.

**Implement:**
- `WeatherProperties` (`@ConfigurationProperties(prefix = "weather")`,
  `latitude`/`longitude` as `double`), bound from new `weather.latitude`/
  `weather.longitude` values in `application.yml` (Karlsruhe's
  coordinates as the default).
- A small domain type, e.g. `WeatherSnapshot` (current temp, condition
  code, hi/lo, hourly list of `{ hour, temperature, rainProbability }`,
  humidity, windSpeed, sunset, `fetchedAt`) — plain Java, not a JPA
  entity (nothing here needs persisting to H2; it's a live/cached value).
- `WeatherProvider` interface: one method, something like
  `WeatherSnapshot fetch()`.
- `OpenMeteoWeatherProvider implements WeatherProvider`: builds the
  request URL from `WeatherProperties`, calls Open-Meteo via Spring's
  `RestClient` (or whichever HTTP client this Spring Boot version
  provides by default — check what's already in use elsewhere in
  `backend/`, if anything, before picking one), maps the response into
  `WeatherSnapshot`, including the WMO-code-to-German-text map.
- A small in-memory cache component (e.g. `WeatherCache` or folded into
  a `WeatherService`) holding the last successful `WeatherSnapshot`,
  refreshed via `@Scheduled` (remember `@EnableScheduling` on the main
  application class), keeping the previous snapshot on a failed refresh.

**Acceptance criteria:**
- A WireMock-based test for `OpenMeteoWeatherProvider` using a real
  captured Open-Meteo JSON response (fetch one manually via curl/browser
  once, save as a test resource) — asserts every field maps correctly,
  including at least one WMO code correctly resolving to its German text.
- A test proving the cache keeps serving the last good snapshot when a
  refresh fails (mock the provider to throw, confirm the previously
  cached value is still returned, not an error/empty state).

## Ticket 2 — Backend: REST API + widget registration

**Scope:** expose Ticket 1's cache over HTTP; register as a widget.
Depends on Ticket 1.

**Implement:**
- `WeatherController` (or similar) exposing `GET /api/weather`, returning
  a DTO (not the internal domain type directly — separate `WeatherDto`,
  matching this project's existing entity/DTO separation pattern from
  `TodoItem`/`TodoItemDto`) shaped to match exactly what the frontend
  needs per the design (current temp, condition text, hi/lo, hourly
  array, humidity, wind, sunset, plus a `stale: boolean`/`fetchedAt`
  marker the frontend can use for the "mark as stale on failed refresh"
  requirement).
- `WeatherWidget implements Widget` (`id() = "weather"`,
  `displayName() = "Wetter"`) — a small `@Component` bean, auto-
  discovered by the existing `WidgetRegistry` (Phase 1, unchanged).

**Acceptance criteria:**
- `@WebMvcTest(WeatherController.class)` with a mocked cache/service
  covering the success path and the "last known good value with an old
  `fetchedAt`" path.
- A test confirming `GET /api/widgets` includes
  `{ "id": "weather", "displayName": "Wetter" }`.

## Ticket 3 — Frontend: weather API client

**Scope:** typed access to Ticket 2's API. No rendering yet. Depends on
Ticket 2.

**Implement:**
- A `WeatherSnapshot` TypeScript interface matching the backend DTO
  exactly. No `any`.
- `WeatherService`: fetches `/api/weather` into a signal
  (`Signal<WeatherSnapshot | undefined>`, `undefined` = not yet
  resolved), mirroring `TodoService`'s pattern.

**Acceptance criteria:**
- `HttpTestingController`-based tests for the fetch (success case, and
  a case asserting the `stale`/`fetchedAt` fields come through
  correctly). No `any`.

## Ticket 4 — Frontend: weather widget component

**Scope:** the real content of the weather slot. Depends on Ticket 3.

**Implement:**
- A component replacing `TransitWeatherPage`'s weather placeholder
  `<article>`, following the design handoff's exact "Weather widget"
  layout (Screen 2): title row (`WETTER` / `Frankfurt`-style location
  label — use "Karlsruhe" / `heute`), current block (temperature,
  condition text, hi/lo), hourly strip (hour/temp/rain-bar per the
  handoff's exact visual spec — an empty rain-bar track at 0% is what
  makes it legible, per the handoff's own note), stats row (humidity,
  wind, sunset).
- A stale-data visual treatment (the design's own requirement: "on a
  failed refresh keep the last values and mark them stale rather than
  blanking the widget") — e.g. a small visual marker or dimmed freshness
  label when `stale` is true, still showing the last good numbers.

**Acceptance criteria:**
- Renders correctly from a mocked `WeatherService` for both a fresh and
  a stale snapshot.
- No `any`; zoneless-compatible; no interactive controls needed here
  (this widget is read-only per the design's "Interactions & behavior"
  section) — so no touch-target concerns beyond what's already global.
- Manual verification note (matching prior tickets' precedent): once
  merged, run the app with real network access and confirm the widget
  shows real Karlsruhe weather end-to-end.

## Sequencing

1 → 2 → 3 → 4, in order. One branch and one implementer/reviewer loop at
a time, same discipline as every prior ticket set in this project.

## Out of scope

- Any UI for changing the location (still config-only, per
  `docs/PLAN.md`'s "hardcoded in config for v1" pattern for this kind of
  setting).
- Weather alerts/warnings, multi-day forecast beyond hi/lo, radar/maps.
- Making the scheduler's interval configurable (fixed at a sensible
  value for now — revisit if it ever needs to be).
