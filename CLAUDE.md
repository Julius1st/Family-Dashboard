# Family Dashboard

A wall-mounted dashboard for household widgets (todos per family member, weather,
public transport departures). Runs on a touchscreen in the living room.

## Stack

- Backend: Spring Boot 4.1, Java 25. Lives in `backend/`.
- Frontend: Angular 22 (standalone components, signals, zoneless). Lives in `frontend/`.
- Packaging: the Angular build outputs into the Spring Boot jar; one artifact.
- Persistence: not yet. Widget data is in-memory for now; PostgreSQL and Flyway
  arrive in a later phase. Do not add a database dependency unless asked.

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
