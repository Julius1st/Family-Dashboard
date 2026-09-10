# Frontend Redesign Plan — Kiosk UI

This breaks the frontend redesign described in
[`docs/design_handoff_family_dashboard/README.md`](design_handoff_family_dashboard/README.md)
into ticket-sized units of work, the same way [`docs/phase-1-plan.md`](phase-1-plan.md)
through [`docs/phase-4-plan.md`](phase-4-plan.md) did for each phase.

The design handoff (`README.md` is the spec; `mockups.dc.html`/`support.js`
are an HTML prototype for visual reference only, not code to copy) defines
a dark, kiosk-styled two-page UI: **Aufgaben** (a per-household-member
task board — the real Todo widget, redesigned) and **Abfahrten & Wetter**
(departures + weather — Phase 5 widgets, not built yet). Both pages share
one header (live clock, German date, a pill that switches pages).

Today's frontend (`frontend/src/app/`) is Phase 2/3 scaffolding built
before any of this was designed: `DashboardShell` renders every widget
from `GET /api/widgets` as a generic tile in one grid, using
`widget-tile-registry.ts` (a type-keyed `Record` resolving a widget id to
a tile component via `NgComponentOutlet`) with `WidgetFallbackTile` as
the default, and `TodoWidgetTile` as the one real registration. That
uniform-tile-grid model doesn't fit the handoff — the two pages have
genuinely different, bespoke layouts (a 4-column member board vs. a
departures list beside a weather panel), not interchangeable tiles. This
plan retires that generic-tile machinery in favor of direct per-page
composition, which better fits "add a future widget in its own layout"
than keeping one generic grid alive.

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order — later tickets depend on earlier ones. This doc is meant to be
self-contained: a fresh implementer agent should be able to work a ticket
from this file alone, without needing prior conversation history.

## Decisions already made with the user (do not re-ask or re-derive)

- Build the full two-page shell now (header, nav pill, both pages
  reachable) — Page 1 fully implements the redesigned Tasks board; Page 2
  exists now but its two slots (departures, weather) show a placeholder
  state until Phase 5 adds those widgets.
- Widget-to-page/slot placement lives in a **frontend-only** static
  config, not a backend change — `backend/`'s `Widget`/`WidgetDescriptor`
  contract (`{ id, displayName }`) is untouched by this whole effort.
- Page switching is an **in-memory signal** (`activePage: 'tasks' |
  'transit'`), not Angular Router — matches the handoff's own "State"
  section exactly, and doesn't reverse Phase 2 Ticket 1's deliberate
  `--routing=false` scaffolding.
- Fonts (Schibsted Grotesk, IBM Plex Mono — both SIL OFL 1.1) are
  **self-hosted**, bundled as static assets — not loaded from Google
  Fonts at runtime. The user explicitly authorized fetching these two
  font files as a one-time exception to the "don't add dependencies
  without authorization" boundary (they're assets, not a package
  dependency).
- "Neue Aufgabe" (add task) opens a **dialog** with a plain text input —
  no on-screen/virtual keyboard work now (a physical keyboard during
  development is fine); that's explicitly deferred.
- UI copy is **hardcoded German**, matching the handoff's copy exactly —
  no i18n framework.
- Layout is **adaptive/responsive**, not the handoff's literal fixed
  1280×800 canvas — `docs/PLAN.md` leaves the real kiosk hardware
  undecided. Treat the handoff's pixel values as a *design language*
  (color tokens, type scale, spacing rhythm, component shapes) to
  preserve, while making the *containers* (grids/flex tracks) reflow
  instead of clipping/overflowing at a different viewport size. Still a
  landscape, touch, kiosk-sized display (tablet-and-up) — not
  phone-width responsive design, since this is a wall-mounted panel, not
  a general public site.

## Design tokens & type scale

The full color table, type scale, radii, and spacing values are already
fully specified in
[`docs/design_handoff_family_dashboard/README.md`](design_handoff_family_dashboard/README.md)
— tickets reference that file directly rather than restating every value
here. Implemented as CSS custom properties (e.g. `--bg-page`,
`--ink-primary`, `--font-mono`, etc.) on `:root` in a new global
stylesheet, so every component consumes tokens instead of repeating
literal `oklch()`/px values.

**Member colors**: the handoff specifies a fixed `L=0.74 C=0.13`, hue
stepping ~45° per member, computed rather than hardcoded to the mock's
four sample names (Mara/Jonas/Lena/Tobi are illustrative — this
project's real `household.members` config currently has two: Alice,
Bob). A small pure function computes `oklch(0.74 0.13 <hue>)` from a
member's position in `TodoService.members()`, so any number of
configured members gets a distinct color with zero code changes when the
list grows.

## What gets removed

`dashboard-shell.ts/.html/.css/.spec.ts`, `widget-fallback-tile.ts/.html/.css`,
and `widget-tile-registry.ts` are all superseded by direct per-page
composition and deleted (not left as dead code). `widget.service.ts` /
`widget-descriptor.ts` are **kept** — `GET /api/widgets` remains the
availability signal each page's slot uses to decide "show the real
widget" vs. "show a placeholder," which is exactly the hook Phase 5's
widgets need to plug into later. `todo.service.ts` / `todo-item.ts` are
kept unchanged — the new Tasks board is a new consumer of the existing,
already-correct API contract.

## Ticket 1 — Design foundation: fonts & tokens

**Scope:** shared infrastructure every other ticket builds on. No page
content yet.

**Implement:**
- Download Schibsted Grotesk (400/500/600/700) and IBM Plex Mono
  (400/500) — both SIL OFL 1.1 — and add them as static assets under
  `frontend/src/assets/fonts/` (or Angular's conventional assets path),
  with `@font-face` declarations in a new global stylesheet.
- Define the full color/spacing/radius token set from the handoff's
  README as CSS custom properties on `:root`.
- Apply the dark theme globally (`bg/page` background, `ink/primary`
  default text) via `frontend/src/styles.css`.

**Acceptance criteria:**
- Fonts load from same-origin assets (verify via network-independent
  means — e.g. confirm the built app's `dist/` output contains the font
  files and no `<link>` to `fonts.googleapis.com`/`fonts.gstatic.com`
  exists anywhere).
- A trivial visual smoke element (e.g. temporarily rendering a token
  swatch, removed before commit, or checked via a component test
  asserting computed styles) confirms the tokens are actually wired, not
  just declared unused.
- Existing test suite still passes.

## Ticket 2 — App shell: header, nav, page switch

**Scope:** the two-page skeleton. Depends on Ticket 1's tokens.

**Implement:**
- A small page-navigation service/signal (`activePage: Signal<'tasks' |
  'transit'>` + a method to switch it) — the handoff's own "State"
  model.
- A header component: live clock (updates once a minute — a simple
  interval-driven signal, not Angular's `DatePipe`/locale-data machinery,
  since it just needs `Intl.DateTimeFormat('de-DE', ...)` formatting) and
  German date, plus the nav pill (`Aufgaben` / `Abfahrten & Wetter`)
  switching `activePage`.
- Two page components: `TasksPage` (content built in Ticket 3) and
  `TransitWeatherPage` — for now, a responsive two-slot grid (departures
  slot + weather slot) each showing a simple, clearly-labeled placeholder
  card (matching the widget-card visual language: surface background,
  border, radius) rather than blank space, since neither backend widget
  exists yet.
- `App`/`app.html` mounts the shell, replacing the current
  `<h1>Hello...</h1>` + `<app-dashboard-shell>` placeholder.

**Acceptance criteria:**
- Tapping the inactive nav pill item switches pages; the active item is
  visually distinct per the token spec.
- Clock updates without a manual refresh (test via fakeAsync/tick or
  equivalent, not a real 60-second wait).
- `TransitWeatherPage`'s placeholders render without errors and clearly
  read as "not yet available," not broken/blank.
- All interactive elements (nav pill items) meet the ≥44px touch baseline
  from `CLAUDE.md`.

## Ticket 3 — Tasks page: the per-member board

**Scope:** the real content of Page 1. Depends on Tickets 1-2.

**Implement:**
- `TasksPage`, consuming the existing `TodoService` (`items()`,
  `members()` signals — unchanged from Phase 3). Header row (eyebrow /
  title / `x von y erledigt` — computed via `computed()`, never stored).
- One column per member (`TodoService.members()`, however many are
  configured — not hardcoded to 4): color dot + name + counter, a
  progress bar (derived, member-colored fill), the member's task rows
  (checkbox-style row toggles done via `TodoService.setDone`, done rows
  struck-through/dimmed per the token spec), and an "add task" control
  pinned to the column's bottom.
- The member-color function from the tokens work (Ticket 1) applied per
  column by position.
- Replaces `TodoWidgetTile`'s old role entirely — delete
  `todo-widget-tile.*` once `TasksPage` covers its behavior, migrating
  its existing test coverage (per-member rendering, toggle, delete) to
  `TasksPage`'s own spec rather than dropping that coverage.

**Acceptance criteria:**
- Renders correctly for any number of configured members (test with 2,
  matching this project's real `application.yml`, not just the mock's
  4).
- Tapping a task row toggles done and the member's progress bar / the
  page's `x von y erledigt` line update immediately (all derived from
  signals, no manual re-fetch).
- No `any`; zoneless-compatible; all interactive targets ≥44px, no
  hover-only affordances (the design allows a hover background tint on
  the add-task button as a bonus, but it must remain fully usable
  without hover, since this is a touch panel per `CLAUDE.md`).

## Ticket 4 — Add-task dialog

**Scope:** the "Neue Aufgabe" flow. Depends on Ticket 3.

**Implement:**
- A dialog (the native `<dialog>` element is a reasonable
  zero-dependency choice — no new package) opened by a column's add-task
  control, scoped to that member (no member picker needed — matches the
  handoff). Plain text input + submit, calling
  `TodoService.create(member, description)` on confirm; cancellable
  without side effects.
- No on-screen keyboard — relies on whatever keyboard is present
  (physical, for now).

**Acceptance criteria:**
- Submitting a non-blank description creates the task for the correct
  member and the dialog closes, with the new task visible in that
  member's column without a manual refresh.
- Blank/whitespace-only input doesn't submit (mirrors the backend's own
  validation, avoiding a round-trip just to get a 400).
- Dialog is dismissable (cancel / outside click or equivalent) without
  creating anything.

## Out of scope for this effort

- The actual departures/weather widgets (Phase 5) — Ticket 2 only builds
  their page's empty-state placeholders.
- Any backend change — `Widget`/`WidgetDescriptor`/`household.members`
  config are untouched.
- On-screen/virtual keyboard support.
- i18n/multi-language support.
- Fixed pixel-exact 1280×800 layout (explicitly traded for
  adaptive/responsive per the decisions above).

## Sequencing

1 → 2 → 3 → 4, in order — each depends on the previous. One branch and
one implementer/reviewer loop at a time, same discipline as every prior
phase.

## Verification

Each ticket's own AC covers its slice. End-to-end, after Ticket 4: run
`cd frontend && npm start` (backend running via `./mvnw spring-boot:run`)
and confirm the whole flow by hand — page switch, per-member board with
real `application.yml` members, toggling a task, adding a task via the
dialog, and the Page 2 placeholders — matches the design handoff's
intent if not its exact pixel grid. `npm test`/`npx tsc --noEmit`/`npx ng
build` stay green throughout, per every prior ticket's discipline in
this project.
