# Phase 1 Plan — Widget Framework Backend

This breaks Phase 1 of [`docs/PLAN.md`](PLAN.md) ("Widget framework
backend — `Widget` contract, registry, `/api/widgets`, unit tests") into
ticket-sized units of work. No `backend/` directory exists yet — Ticket 1 is
the first code in this repo.

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order — later tickets depend on earlier ones. This doc is meant to be
self-contained: a fresh implementer agent should be able to work a ticket
from this file alone, without needing prior conversation history.

## Assumptions / decisions baked into these tickets

These aren't fixed elsewhere in the repo yet; they're decided here so each
ticket doesn't have to re-derive them:

- Maven `groupId`: `com.familydashboard`, `artifactId`: `backend`, base
  Java package: `com.familydashboard`.
- Widget-related code lives under `com.familydashboard.widget`.
- `start.spring.io` (Spring Initializr) may not be reachable behind this
  container's egress firewall — only Maven Central, GitHub, npm, Gradle
  distributions, and the Anthropic API are allowlisted (see `CLAUDE.md`
  Environment section). Build the Maven project by hand (`pom.xml` +
  wrapper) rather than depending on Initializr being reachable.
- No real widget implementation exists yet — that's the Todo widget in
  Phase 3. Don't add an example/demo widget to `src/main/java` in these
  tickets; it's speculative code with no consumer yet. Test doubles for the
  registry/controller belong under `src/test/java` only.

## Ticket 1 — Backend project scaffolding

**Scope:** get a bootable, testable Spring Boot skeleton in `backend/`,
with H2 file-persistent JPA wired up, and nothing else.

**Implement:**
- `backend/pom.xml`: Spring Boot 4.1 parent, Java 25, groupId/artifactId as
  above. Dependencies: `spring-boot-starter-web`,
  `spring-boot-starter-data-jpa`, `com.h2database:h2`,
  `spring-boot-starter-test`. Don't add anything beyond these without
  authorization (per `CLAUDE.md` boundary on dependencies).
- Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) so the tool doesn't
  need to be preinstalled, per `CLAUDE.md`.
- `com.familydashboard.FamilyDashboardApplication` — the
  `@SpringBootApplication` main class.
- `backend/src/main/resources/application.yml`: H2 in file-persistent mode
  (e.g. a file under `backend/data/`), Spring Data JPA configured against
  it. Write the config in a JPA/Hibernate-portable way — no H2-specific SQL
  or dialect tricks — so switching to PostgreSQL later (per `docs/PLAN.md`)
  is a config change, not a rewrite. Set `ddl-auto` to something sensible
  for dev (`update`) since Flyway isn't introduced until PostgreSQL is.
- Update `.gitignore`: `backend/target/`, the H2 data file(s) (e.g.
  `backend/data/`).

**Acceptance criteria:**
- `cd backend && ./mvnw clean verify` succeeds.
- `cd backend && ./mvnw spring-boot:run` boots the app and creates the
  H2 file-backed database on disk.
- A minimal `@SpringBootTest` (context-load-only) test passes.
- The commands in `CLAUDE.md` (`./mvnw test`, `./mvnw spring-boot:run`)
  work exactly as documented — don't diverge from them.

## Ticket 2 — CI skeleton (GitHub Actions, backend)

**Scope:** catch regressions from here on; depends on Ticket 1 existing.

**Implement:**
- `.github/workflows/backend-ci.yml`: on push and pull_request, set up
  Java 25, run `cd backend && ./mvnw -B test`.

**Acceptance criteria:**
- Workflow runs and passes against Ticket 1's branch state.
- Scoped to `backend/` only — no frontend job yet (that arrives in
  Phase 2 when `frontend/` exists).

## Ticket 3 — `Widget` contract + registry

**Scope:** the core abstraction and its discovery mechanism. No HTTP, no
real widget implementations.

**Implement**, under `com.familydashboard.widget`:
- `Widget` — interface with a minimal contract: unique `id()`, human
  readable `displayName()`. Don't add config-schema hooks or other fields
  speculatively — the real widget in Phase 3 will drive what's actually
  needed there.
- `WidgetDescriptor` — small record/DTO capturing what's exposed
  externally: `id`, `displayName`.
- `WidgetRegistry` — a Spring `@Component` that takes `List<Widget>` via
  constructor injection (Spring auto-collects all `Widget` beans in the
  context), builds an id-keyed map at construction, and throws a clear
  exception at startup if two widgets share an id. Exposes `getAll()` and
  `findById(String)`.

**Acceptance criteria:**
- Unit tests, using ≥2 fake `Widget` test doubles defined in
  `src/test/java` (not `src/main/java`), cover:
  - normal discovery/listing of registered widgets,
  - duplicate-id rejection at construction time,
  - empty-registry behavior (zero `Widget` beans present in the context).
- `WidgetRegistry` has no HTTP/JSON concerns — pure Java + Spring DI, no
  dependency on Spring MVC.

## Ticket 4 — `/api/widgets` REST endpoint

**Scope:** expose the registry over HTTP for the future Angular shell
(Phase 2) to consume. Depends on Ticket 3.

**Implement:**
- `WidgetController` — `@RestController` at `/api/widgets`, with
  `GET /api/widgets` returning the list of registered widgets as JSON.
  Reuse `WidgetDescriptor` for the response shape if it's already
  HTTP-appropriate; if the `Widget` contract grows internal-only fields
  later, don't let those leak over HTTP — introduce a separate response DTO
  at that point rather than now.

**Acceptance criteria:**
- `GET /api/widgets` returns `200` with a JSON array.
- Returns `[]` (not an error) when no widgets are registered.
- A `@WebMvcTest`/MockMvc test verifies the JSON response shape against a
  mocked `WidgetRegistry`.

## Sequencing

1 → 2 can happen back-to-back (CI needs something to build against). 3
depends on 1. 4 depends on 3. Work them in order, one branch and one
implementer/reviewer loop at a time — don't parallelize 3 and 4, since 4
builds directly on 3's output.
