# Phase 3 Plan — Todo Widget

This breaks Phase 3 of [`docs/PLAN.md`](PLAN.md) ("Todo widget — first
real widget built on the framework — per-household-member CRUD lists,
end-to-end") into ticket-sized units of work, the same way
[`docs/phase-1-plan.md`](phase-1-plan.md) and
[`docs/phase-2-plan.md`](phase-2-plan.md) did for Phases 1 and 2.

Phases 1 and 2 are done and merged into `main`. The backend exposes `GET
/api/widgets` via a `Widget` contract + `WidgetRegistry` that
auto-discovers Spring beans
(`backend/src/main/java/com/familydashboard/widget/`). The frontend has a
`DashboardShell` component that fetches widgets via `WidgetService`
(signal-based) and renders one tile per widget, resolved through a
minimal type-keyed seam in `frontend/src/app/widget-tile-registry.ts`
(currently an empty `Record<string, Type<unknown>>`, falling back to a
generic `WidgetFallbackTile`). `NgComponentOutlet` passes each resolved
tile component one input: `descriptor: WidgetDescriptor`.

This is the first phase that spans both `backend/` and `frontend/`
(Phase 1 was backend-only, Phase 2 frontend-only), and the first widget
to actually register into the seams Phases 1 and 2 built.

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order — later tickets depend on earlier ones. This doc is meant to be
self-contained: a fresh implementer agent should be able to work a ticket
from this file alone, without needing prior conversation history.

## Design decisions baked into these tickets

These aren't fixed elsewhere in the repo yet; they're decided here so each
ticket doesn't have to re-derive them:

- **Household members**: `docs/PLAN.md` says these are "hardcoded in
  config for v1 (e.g. `application.yml`)." No such config exists yet.
  Since Todo is the only consumer so far, the member list lives in the
  Todo backend package as a `@ConfigurationProperties(prefix =
  "household")` bean (`List<String> members`), not as a new general
  framework concept — same "don't speculate beyond what's driven by a
  real consumer" discipline `Widget` (Phase 1) and the tile registry
  (Phase 2) followed. If a second widget needs a member list later,
  that's when it gets promoted/shared.
- **No new Maven dependency for validation**: `backend/pom.xml` has no
  `spring-boot-starter-validation`. Per `CLAUDE.md`'s "don't add
  dependencies without authorization," validation (non-blank
  description, member must be one of the configured list) is done with
  plain code (`ResponseStatusException`), not Bean Validation
  annotations.
- **REST shape**, all under `/api/todos` (owned entirely by the Todo
  package, per "each widget owns its own endpoints" in `docs/PLAN.md`):
  - `GET /api/todos` → all items: `[{ id, householdMember, description,
    done }]`.
  - `GET /api/todos/members` → the configured household members (a
    plain `string[]`) — lets the UI render a section for every member,
    including ones with zero items.
  - `POST /api/todos` → body `{ householdMember, description }` → 201 +
    created item. 400 if `description` is blank or `householdMember`
    isn't one of the configured members.
  - `PUT /api/todos/{id}` → body `{ description, done }` → full replace,
    returns the updated item. Simpler than partial PATCH semantics for a
    first widget. 404 if `id` doesn't exist.
  - `DELETE /api/todos/{id}` → 204. 404 if `id` doesn't exist.
- **Frontend state**: `TodoService` fetches once into local writable
  signals (`items`, `members`) and updates `items` from each mutation's
  HTTP response — no polling or multi-client sync (matches the no-auth,
  single-kiosk-screen v1 scope; not a stated requirement yet).
- **Widget id**: `"todo"`, `displayName`: `"Todo Lists"`. The frontend
  registers `TodoWidgetTile` into `widget-tile-registry.ts` under the
  same `"todo"` key so `resolveWidgetTileComponent` picks it up
  automatically — no `DashboardShell` changes needed.
- **No new CI file**: `frontend-ci.yml`/`backend-ci.yml` already run
  `npm test`/`./mvnw test` unconditionally, so new Todo tests are picked
  up for free — no CI ticket this phase.
- **Out of scope for Phase 3**: reordering/priority, due dates,
  multi-user auth/permissions, editing the household member list at
  runtime (still config-only). All later, not blocking "first real
  widget, end-to-end."

## Ticket 1 — Backend: household config + Todo domain model

**Scope:** the data layer only. No HTTP yet — that's Ticket 2.

**Implement:**
- `HouseholdProperties`, a `@ConfigurationProperties(prefix =
  "household")` bean with `List<String> members`, bound from a new
  `household.members` list added to `application.yml` (e.g. two
  placeholder names).
- `TodoItem`, a JPA entity: `id` (generated), `householdMember`
  (String), `description` (String), `done` (boolean, default `false`).
  Written in a JPA/Hibernate-portable way (no H2-specific SQL), per
  `docs/PLAN.md`'s persistence decision.
- `TodoItemRepository extends JpaRepository<TodoItem, Long>`, with a
  finder suitable for listing all items (e.g. `findAllByOrderById`).

**Acceptance criteria:**
- A `@DataJpaTest` proves `TodoItemRepository` round-trips a `TodoItem`
  (save, then find/list it back with the same field values).
- A Spring context test proves `HouseholdProperties` actually binds
  `household.members` from `application.yml` (not just compiles).

## Ticket 2 — Backend: Todo REST API + widget registration

**Scope:** expose Ticket 1's model over HTTP, and register Todo as a
widget. Depends on Ticket 1.

**Implement:**
- `TodoController`, implementing the REST shape from "Design decisions"
  above: list, list-members, create, replace, delete. Validate
  `description` non-blank and `householdMember` against
  `HouseholdProperties.members()` — 400 via `ResponseStatusException` on
  either failure; 404 (also via `ResponseStatusException`) when
  replace/delete target an unknown `id`.
- `TodoWidget implements Widget` (`id() = "todo"`, `displayName() =
  "Todo Lists"`) — a small `@Component` bean. `WidgetRegistry` (Phase 1,
  unchanged) auto-discovers it, so `GET /api/widgets` will include it
  with no registry changes.

**Acceptance criteria:**
- `@WebMvcTest(TodoController.class)` with a `@MockitoBean` repository
  (and `HouseholdProperties`) covers each endpoint's success path and its
  validation-failure path(s).
- One `@SpringBootTest` integration test drives the real flow against H2:
  create → list (item appears) → replace with `done=true` → delete
  (confirms it's gone) — proving the full slice works together end to
  end, not just each layer in isolation.
- A test confirms `GET /api/widgets` (or `WidgetRegistry` directly)
  includes `{ "id": "todo", "displayName": "Todo Lists" }`.

## Ticket 3 — Frontend: Todo API client

**Scope:** typed access to Ticket 2's API. No UI rendering yet — that's
Ticket 4. Depends on Ticket 2.

**Implement:**
- A `TodoItem` TypeScript interface matching the backend DTO exactly:
  `{ id: number; householdMember: string; description: string; done:
  boolean }`. No `any`.
- `TodoService` in `frontend/src/app/`: fetches `/api/todos` and
  `/api/todos/members` into two signals, `items` and `members` (per
  `CLAUDE.md`'s "prefer signals over RxJS for component state" —
  consumers never see a raw `Observable`). Mutation methods `create`,
  `setDone`, `updateDescription`, `remove` call the corresponding
  backend endpoint and update `items` from the response.

**Acceptance criteria:**
- `HttpTestingController`-based tests cover: the initial fetch (both a
  populated and an empty-array response, the latter as a normal
  non-error state, mirroring the backend's own "empty is not an error"
  contract), and each mutation method (asserts the request method/body
  and the resulting `items` signal state).
- No `any` types anywhere in the new code.

## Ticket 4 — Frontend: Todo widget tile (CRUD UI)

**Scope:** render and interact with Todo lists in the dashboard. Depends
on Ticket 3.

**Implement:**
- `TodoWidgetTile`, registered into `widget-tile-registry.ts` under the
  `"todo"` key. Accepts `descriptor: input.required<WidgetDescriptor>()`
  (satisfying `NgComponentOutlet`'s existing input contract from Phase
  2's Ticket 4), but drives its actual data from `TodoService`, not the
  descriptor.
- Renders one section per configured household member (from
  `members()`, including members with zero items today), each showing
  its items with a toggle-done control and a delete control, plus an
  add-item input. Touch-UI baseline applies: interactive targets ≥44px,
  no hover-only affordances (e.g. delete must be always visible/tappable,
  not hover-revealed), per `CLAUDE.md`.

**Acceptance criteria:**
- A `TestBed` test with a mocked `TodoService` verifies per-member
  grouping renders correctly, including a member with zero items
  rendering a visible (non-broken) empty section, and that the add/
  toggle/delete UI actions call the corresponding `TodoService` methods.
- No `any` usage; zoneless-compatible (signals only, no manual
  `ChangeDetectorRef` calls).
- Manual verification before merging (like Phase 1/2 Ticket 1s): run the
  backend and `npm start` together and confirm the Todo widget actually
  renders and round-trips through the real API, not just its mocked
  tests.

## Out of scope for Phase 3

- Reordering/priority, due dates, or any richer item metadata.
- Multi-user auth/permissions.
- Editing the household member list at runtime — still config-only
  (`application.yml`) for v1.
- Any widget besides Todo (weather, transit) — Phase 5.
- Bundling the Angular build into the Spring Boot jar — Phase 4
  packaging.

## Sequencing

1 → 2 → 3 → 4, strictly in order — each depends on the previous. No
parallelization; one branch and one implementer/reviewer loop at a time,
same discipline as Phases 1 and 2.
