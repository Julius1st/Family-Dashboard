# Phase 2 Plan — Dashboard Shell Frontend

This breaks Phase 2 of [`docs/PLAN.md`](PLAN.md) ("Dashboard shell
frontend — dynamic widget loading from the API, component tests") into
ticket-sized units of work, the same way [`docs/phase-1-plan.md`](phase-1-plan.md)
did for Phase 1. No `frontend/` directory exists yet — Ticket 1 is the
first frontend code in this repo.

Phase 1 is done and merged into `main`: `backend/` exposes
`GET /api/widgets`, returning a JSON array of `WidgetDescriptor` objects —
`{ "id": string, "displayName": string }` (see
`backend/src/main/java/com/familydashboard/widget/WidgetDescriptor.java`
and `WidgetController.java`).

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order — later tickets depend on earlier ones. This doc is meant to be
self-contained: a fresh implementer agent should be able to work a ticket
from this file alone, without needing prior conversation history.

## Assumptions / decisions baked into these tickets

These aren't fixed elsewhere in the repo yet; they're decided here so each
ticket doesn't have to re-derive them:

- Angular 22 standalone workspace under `frontend/`, zoneless change
  detection, strict mode, no `any` anywhere.
- `@angular/cli` is a devDependency of `frontend/`, not relied on as a
  global install — the same self-contained spirit as the Maven wrapper in
  `backend/`.
- Unlike `start.spring.io` (unreachable behind this container's egress
  firewall), npm's registry *is* reachable — Ticket 1 can scaffold via the
  Angular CLI directly (e.g. `npx @angular/cli@22 new`) rather than
  hand-rolling workspace config the way Ticket 1 of Phase 1 had to
  hand-roll Maven.
- Per `CLAUDE.md`'s "prefer signals over RxJS for component state":
  `HttpClient` calls are wrapped so components consume a **signal**, never
  a raw `Observable` directly.
- No real widget component exists yet — the Todo widget is Phase 3. Ticket
  4 renders a generic fallback tile for every widget and introduces only
  the minimal seam (a type-keyed lookup with a default fallback) that
  Phase 3 will register the Todo component into. Don't build a full plugin
  framework now — same "don't speculate beyond what's driven by a real
  consumer" discipline Phase 1 applied to the `Widget` interface.
- Touch-UI baseline (interactive targets ≥44px, no hover-dependent
  behavior — per `CLAUDE.md` Conventions) applies from the start, even to
  placeholder UI.
- Bundling the Angular build into the Spring Boot jar is Phase 4
  packaging, not Phase 2 — `frontend/` stays a standalone dev-server setup
  for now.

## Ticket 1 — Frontend project scaffolding

**Scope:** a bootable, testable Angular skeleton in `frontend/`, with the
dev server proxying `/api` to the backend, and nothing else.

**Implement:**
- Angular 22 standalone workspace under `frontend/`: zoneless change
  detection, strict mode, no `any`. Scaffold via the Angular CLI (`npx
  @angular/cli@22 new` or equivalent) rather than hand-writing config.
- `package.json` scripts matching `CLAUDE.md` exactly: `npm test`, `npm
  start`.
- Dev server proxy config (e.g. `proxy.conf.json`) so `npm start` proxies
  `/api` requests to `http://localhost:8080`, wired into the `start`
  script.
- Minimal root `AppComponent` — just enough to prove the app boots. The
  real dashboard shell is Ticket 4; don't build it here.
- `.gitignore` additions: `frontend/node_modules/`, the Angular CLI's
  build output directory (e.g. `frontend/dist/`), and any Angular CLI
  cache directory it creates.

**Acceptance criteria:**
- `cd frontend && npm ci && npm test` passes (the scaffolded smoke test,
  e.g. "should create the app").
- `cd frontend && npm start` serves the app and proxies `/api` calls to
  `:8080`.
- The commands in `CLAUDE.md` (`npm test`, `npm start`) work exactly as
  documented — don't diverge from them.

## Ticket 2 — CI skeleton (GitHub Actions, frontend)

**Scope:** catch frontend regressions from here on; depends on Ticket 1
existing. Mirrors Phase 1's Ticket 2 for the backend.

**Implement:**
- `.github/workflows/frontend-ci.yml`, structured like the existing
  `.github/workflows/backend-ci.yml` (`on: push`/`pull_request`, single
  job): set up a pinned Node LTS version via `actions/setup-node@v4`
  (`cache: npm`), run `cd frontend && npm ci`, then run the test script
  headlessly/non-interactively (e.g. `npm test -- --watch=false
  --browsers=ChromeHeadless`, adjusted to whatever Ticket 1's scaffolded
  test runner actually needs to run non-interactively in CI).
- This is a new, separate workflow file — do not modify
  `backend-ci.yml`.

**Acceptance criteria:**
- Workflow runs and passes against Ticket 1's branch state.
- Scoped to `frontend/` only. `backend-ci.yml` is untouched.

## Ticket 3 — Widget API client

**Scope:** typed access to `GET /api/widgets` from the frontend. No UI
rendering yet — that's Ticket 4.

**Implement:**
- A `WidgetDescriptor` TypeScript interface matching the backend JSON
  shape exactly: `{ id: string; displayName: string }`. No `any`.
- A `WidgetService` (or similar) in `frontend/src/app/` that calls `GET
  /api/widgets` via Angular's `HttpClient` and exposes the result to
  consumers as a **signal** (e.g. via `toSignal`/`resource()`) — not a raw
  `Observable`.
- Wire `provideHttpClient()` into the app config from Ticket 1.

**Acceptance criteria:**
- Unit tests using `HttpTestingController` (or equivalent) cover:
  - a successful fetch returning a typed list of widgets,
  - an empty-array response (`[]`) handled as a normal empty state, not an
    error — mirroring the backend's "empty is not an error" contract from
    Phase 1 Ticket 4.
- No `any` types anywhere in the new code.

## Ticket 4 — Dashboard shell component

**Scope:** render the widgets returned by the API. Depends on Ticket 3.

**Implement:**
- A shell component (e.g. `DashboardShellComponent`), mounted from
  `AppComponent`, that consumes `WidgetService` and renders one tile per
  returned widget.
- Since no real widget component exists yet (Todo widget is Phase 3),
  render a generic fallback tile (showing `displayName`) for every widget.
  Introduce a minimal type-keyed lookup (e.g. a `Record<string, Type<...>>`
  with a default fallback) as the seam Phase 3 will register the Todo
  component into — keep it to exactly what's needed for one fallback case,
  nothing more speculative.
- Touch-UI baseline applies even to placeholder tiles: interactive targets
  ≥44px, no hover-dependent behavior.

**Acceptance criteria:**
- A component test (`TestBed`) with a mocked `WidgetService` verifies one
  tile is rendered per widget in the list.
- Renders a sensible empty state (not blank/broken) when the API returns
  `[]`.
- No `any` usage; zoneless-compatible (driven by signals, no manual
  `ChangeDetectorRef` calls).

## Out of scope for Phase 2

- Any real widget component (Todo, weather, transit) — Phase 3.
- Bundling the Angular build into the Spring Boot jar — Phase 4
  packaging.
- Household member config / per-member views — later, tied to the Todo
  widget in Phase 3.

## Sequencing

1 → 2 can happen back-to-back (CI needs something to build against). 3
depends on 1. 4 depends on 3. Work them in order, one branch and one
implementer/reviewer loop at a time — don't parallelize 3 and 4, since 4
builds directly on 3's output.
