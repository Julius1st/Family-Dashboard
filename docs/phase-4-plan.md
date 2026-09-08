# Phase 4 Plan — Deployment Packaging

This breaks Phase 4 of [`docs/PLAN.md`](PLAN.md) ("Deployment packaging —
`docker-compose up` runs the full stack; sanity-check touch usability at
kiosk scale") into ticket-sized units of work, the same way
[`docs/phase-1-plan.md`](phase-1-plan.md), [`docs/phase-2-plan.md`](phase-2-plan.md),
and [`docs/phase-3-plan.md`](phase-3-plan.md) did for Phases 1-3.

Phases 1-3 are done and merged into `main`: a working backend (`Widget`
framework + a real Todo widget with a validated `/api/todos` CRUD API) and
a working frontend (`DashboardShell` + `TodoWidgetTile`, signal-based,
fetching from the backend via a dev-server proxy). `CLAUDE.md`'s Stack
section already states the packaging decision: "the Angular build outputs
into the Spring Boot jar; one artifact."

Each ticket is implemented on its own branch via the implementer/reviewer
loop described in [`CLAUDE.md`](../CLAUDE.md#workflow). Do the tickets in
order — later tickets depend on earlier ones. This doc is meant to be
self-contained: a fresh implementer agent should be able to work a ticket
from this file alone, without needing prior conversation history.

## A real constraint on this phase: no Docker in the dev container

This devcontainer has no `docker` CLI/daemon, and its egress firewall
(`CLAUDE.md`'s Environment section) only allows the Anthropic API, npm,
Maven Central, Gradle distributions, and GitHub — not Docker Hub or
`nodejs.org`'s binary distribution server. This shapes the ticket split:
Ticket 1 (bundling the frontend into the jar) is a plain Maven build step,
fully testable here with `./mvnw package` + `java -jar`. Ticket 2
(Docker/Compose) can only be verified by careful manual inspection in this
sandbox — the implementer/reviewer cannot build or run the image — and
must be smoke-tested by a human, on a machine with Docker, after merging.
Say this plainly in that ticket's own report; don't paper over it.

## Design decisions baked into these tickets

- **Bundling tool**: `frontend-maven-plugin` in `backend/pom.xml` (a new
  build-time Maven dependency — approved explicitly for this phase,
  unlike earlier phases' "no new dependency" defaults). It must be
  configured to use this container's already-installed system Node/npm
  rather than its default behavior of downloading its own pinned Node —
  that download goes to `nodejs.org`, not confirmed reachable behind
  this repo's firewall. Investigate the plugin's config for skipping its
  `install-node-and-npm` execution and just invoking the `npm`/`node`
  already on `PATH`.
- **No SPA-routing complexity**: Angular was scaffolded with
  `--routing=false` (Phase 2 Ticket 1) — there's no client-side router,
  so there's no SPA-fallback-routing question to solve. Spring Boot's
  default static-resource serving (classpath `static/`, `index.html` as
  the welcome page) is sufficient with zero new backend Java code.
- **Same-origin API calls, no code changes needed**: `WidgetService`/
  `TodoService` already call relative paths (`/api/widgets`,
  `/api/todos`). Once Spring Boot serves both the static frontend and
  the API from one origin, these work unmodified, with no proxy. The
  dev-mode `proxy.conf.json` workflow (`npm start`) is untouched and
  keeps working for local development — bundling only affects the
  packaged artifact.
- **Single-container deployment**: one Spring Boot container serves
  everything (matches the "one artifact" decision) — not separate
  frontend/backend containers. No reverse proxy or TLS, per the
  existing no-auth, LAN-only scope in `docs/PLAN.md`.
- **H2 persistence across container restarts, no code change needed**:
  Spring Boot supports overriding `spring.datasource.url` via the
  `SPRING_DATASOURCE_URL` environment variable out of the box (relaxed
  binding). `docker-compose.yml` sets this to an absolute path under a
  named volume (e.g. `jdbc:h2:file:/data/family-dashboard`, volume
  mounted at `/data`), so data survives `docker-compose down && up`.
- **Out of scope for Phase 4**: remote/outside-home access, TLS/reverse
  proxy, PostgreSQL migration, any new widgets (Phase 5), an admin UI
  for household members (still config-only, per `docs/PLAN.md`).

## Ticket 1 — Bundle the frontend into the Spring Boot jar

**Scope:** make `./mvnw package` alone produce a single self-contained
jar. Fully testable in this sandbox — no Docker involved.

**Implement:**
- Add `frontend-maven-plugin` to `backend/pom.xml`, bound to a phase
  before the jar is assembled (e.g. `generate-resources`/
  `prepare-package`), configured to run `npm run build` in `frontend/`
  using system-installed Node/npm (not the plugin's own downloaded
  copy).
- Ensure the Angular build output (`frontend/dist/frontend/browser/**`)
  ends up on the jar's classpath under `static/` (e.g. via the plugin's
  own copy support, or `maven-resources-plugin`), so Spring Boot's
  default static-resource handling serves it — no new backend Java code
  needed.

**Acceptance criteria:**
- `cd backend && ./mvnw clean package` (a single command, no separate
  script) produces a jar that, run via `java -jar target/*.jar`, serves
  the Angular app's `index.html` at `GET /` and its JS/CSS assets, while
  `/api/widgets` and `/api/todos` keep responding — one process, no dev
  server, no proxy.
- A full Todo CRUD round trip (create → toggle done → delete) works
  against this single running jar.
- The existing local dev workflow (`npm start` with its proxy,
  `./mvnw spring-boot:run`) is unaffected.

## Ticket 2 — Dockerize + docker-compose

**Scope:** package Ticket 1's jar into a container and run it via
Compose with persistent data. Depends on Ticket 1.

**Implement:**
- A multi-stage `Dockerfile`: a build stage that produces Ticket 1's jar
  (JDK 25 + Node available, or a build stage that pre-builds the
  frontend and feeds it into a Maven stage — implementer's choice, keep
  it as simple as correctly possible), and a slim JRE 25 runtime stage
  that copies in just the built jar and runs it (`ENTRYPOINT java -jar
  ...`), exposing the app's port.
- A `.dockerignore` excluding `node_modules/`, `target/`, `.git/`,
  `backend/data/`, `frontend/dist/`, etc., so the build context stays
  clean.
- `docker-compose.yml`: one service built from the Dockerfile, exposing
  the port, with a named volume mounted for H2 persistence and
  `SPRING_DATASOURCE_URL` set to point the datasource at that mounted
  path (see "Design decisions" above — no `application.yml` change
  needed, this is purely compose-level config).
- A short "Run with Docker" section added to `README.md` documenting
  `docker-compose up` and how data persists.

**Acceptance criteria:**
- The Dockerfile and `docker-compose.yml` are reviewed carefully by
  manual inspection against correct Docker/Compose syntax and against
  Ticket 1's actual, already-proven build steps (the Dockerfile's build
  stage must do the same thing `./mvnw clean package` already does, not
  diverge from it).
- The ticket's own report explicitly states that `docker build`,
  `docker-compose up`, and a data-survives-`down`-then-`up` check must
  be smoke-tested by a human with Docker installed, after merging — this
  cannot be verified inside the implementer/reviewer loop itself.

## Ticket 3 — Kiosk/touch usability pass

**Scope:** tune the frontend for a fixed-purpose touchscreen kiosk, and
re-verify (not just assume) the existing touch-UI baseline still holds
project-wide. Independent of Tickets 1-2's packaging work, but sequenced
last since it's a final polish pass on what's being packaged.

**Implement:**
- A viewport meta tag in `frontend/src/index.html` tuned for a
  fixed-scale kiosk display (disabling pinch/double-tap zoom is
  acceptable here — this is a single-purpose kiosk device, not a public
  page subject to normal accessibility zoom expectations).
- Global CSS (`frontend/src/styles.css`) preventing text-selection/
  callout on interactive controls and unwanted scroll/zoom gestures
  (e.g. pull-to-refresh bounce), and ensuring the app fills the
  viewport.

**Acceptance criteria:**
- Existing component tests still pass.
- A written checklist (in the ticket's own report, not necessarily new
  code) re-confirming every interactive control in
  `WidgetFallbackTile` and `TodoWidgetTile` still meets the ≥44px
  baseline from `CLAUDE.md` — read the actual current CSS values, don't
  assume they're still correct just because earlier tickets checked
  them once.

## Sequencing

1 → 2 → 3, in order — Ticket 2 depends on Ticket 1's jar; Ticket 3 is
independent but done last as a final pass. One branch and one
implementer/reviewer loop at a time — same discipline as Phases 1-3.
