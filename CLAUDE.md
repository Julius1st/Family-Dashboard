# Family Dashboard

A wall-mounted dashboard for household widgets (todos per family member, weather,
public transport departures). Runs on a touchscreen in the living room.

## Stack

- Backend: Spring Boot 4.1, Java 25. Lives in `backend/`.
- Frontend: Angular 22 (standalone components, signals, zoneless). Lives in `frontend/`.
- Packaging: the Angular build outputs into the Spring Boot jar; one artifact.
- Persistence: H2 in file-persistent mode via Spring Data JPA, from the
  start. Write JPA/Hibernate-portable code (avoid H2-specific SQL) so
  swapping the datasource to PostgreSQL later is a config change, not a
  rewrite. Flyway arrives when PostgreSQL does.

## Commands

Use the build wrapper in `backend/`; the tool itself is not installed in the
container and the wrapper fetches it.

- Backend tests: `cd backend && ./mvnw test`
- Backend run: `cd backend && ./mvnw spring-boot:run`
- Frontend tests: `cd frontend && npm test`
- Frontend dev server: `cd frontend && npm start` (proxies /api to :8080)

## Conventions

- External API calls (weather, transit) are wrapped behind a provider interface
  with our own DTOs, so an upstream change touches one adapter class.
  Tests for these use WireMock, never the live API.
- Angular: prefer signals over RxJS for component state. No `any`.
- Touch UI: interactive targets at least 44px, no hover-dependent behaviour.
- Regular, small commits with descriptive messages. Avoid large, multi-purpose PRs.

## Workflow

See `docs/PLAN.md` for the phased roadmap and the decisions behind it.

Implementation tasks use an implementer/reviewer subagent loop, not a direct
implementation:

1. Spin up an implementer agent scoped to one feature that fits on a single
   branch. If a task is too large for one sensible branch, split it into
   smaller branch-scoped tasks up front instead of doing one large run.
2. Once it's done, spin up a separate reviewer agent to review the branch
   like a human teammate would — correctness, and also flagging when the
   code has become more complex than sensible.
3. Pass the reviewer's feedback back to the *same* implementer agent
   (continue it, don't respawn fresh) — it must either change the code or
   defend its decision.
4. Loop steps 2-3 until both the implementer and reviewer are satisfied.
5. Only then hand the branch to the user for the final human review. Don't
   ask the user to look at it mid-loop.

## Environment

This runs in a dev container behind a default-deny egress firewall. Only the
Anthropic API, npm, Maven Central, Gradle distributions and GitHub are
reachable. If a command fails with a network timeout, that is the firewall,
not a broken dependency — say so rather than retrying or working around it.

## Boundaries

- Don't commit directly to `main`; work on a branch.
- Don't add dependencies without authorization. Explain why you need it and what alternatives you considered.
- Don't reformat files you weren't asked to change.
- Don't edit anything under `.devcontainer/`.
