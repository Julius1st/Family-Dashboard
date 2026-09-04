# Family Dashboard

A wall-mounted dashboard for household widgets — todo lists per family
member, weather, and public transport departures — meant to eventually run
on a touchscreen in the living room.

## Stack

- **Backend:** Spring Boot 4.1, Java 25, in `backend/`.
- **Frontend:** Angular 22 (standalone components, signals, zoneless), in
  `frontend/`.
- **Packaging:** the Angular build output is bundled into the Spring Boot
  jar, so the app ships as a single artifact.
- **Persistence:** H2 in file-persistent mode via Spring Data JPA, written
  in a JPA/Hibernate-portable way so switching to PostgreSQL later is a
  configuration change rather than a rewrite. Flyway arrives alongside
  PostgreSQL.

## Status

Phase 1 (widget framework backend) is done: `backend/` builds, boots, and
exposes `/api/widgets`, with CI running its tests on every push/PR. Phase 2
(dashboard shell frontend) is up next — `frontend/` doesn't exist yet. See
[`docs/PLAN.md`](docs/PLAN.md) for the full phased roadmap and the
decisions behind it.

## Running the project

The backend can be run today; the frontend commands below will work once
Phase 2 adds `frontend/` (from the repo root):

- Backend tests: `cd backend && ./mvnw test`
- Backend run: `cd backend && ./mvnw spring-boot:run`
- Frontend tests: `cd frontend && npm test`
- Frontend dev server: `cd frontend && npm start` (proxies `/api` to
  `:8080`)

The Maven wrapper in `backend/` fetches Maven itself, so no local install is
required.

## License

MIT — see [`LICENSE`](LICENSE).
