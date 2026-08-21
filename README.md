# Family Dashboard

A wall-mounted dashboard for household widgets — todo lists per family
member, weather, and public transport departures — meant to eventually run
on a touchscreen in the living room. Built for private use and also
published on GitHub as a portfolio piece.

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

This project is still in Phase 0 (repo scaffolding). The `backend/` and
`frontend/` directories referenced above don't exist yet, so there is
nothing to run at this point. See [`docs/PLAN.md`](docs/PLAN.md) for the
full phased roadmap and the decisions behind it.

## Running the project

Once the backend and frontend exist, use these commands (from the repo
root):

- Backend tests: `cd backend && ./mvnw test`
- Backend run: `cd backend && ./mvnw spring-boot:run`
- Frontend tests: `cd frontend && npm test`
- Frontend dev server: `cd frontend && npm start` (proxies `/api` to
  `:8080`)

The Maven wrapper in `backend/` fetches Maven itself, so no local install is
required.

## License

MIT — see [`LICENSE`](LICENSE).
