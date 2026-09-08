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

Phases 1 through 3 are done: `backend/` builds, boots, and exposes
`/api/widgets` plus a `/api/todos` CRUD API backing the first real widget;
`frontend/` renders a dashboard shell with a working Todo widget —
per-household-member lists you can add to, check off, and delete — with CI
running both sides' tests on every push/PR. Phase 4 (deployment packaging)
is up next. See [`docs/PLAN.md`](docs/PLAN.md) for the full phased roadmap
and the decisions behind it.

## Running the project

Both the backend and frontend can be run today (from the repo root):

- Backend tests: `cd backend && ./mvnw test`
- Backend run: `cd backend && ./mvnw spring-boot:run`
- Frontend tests: `cd frontend && npm test`
- Frontend dev server: `cd frontend && npm start` (proxies `/api` to
  `:8080`)

The Maven wrapper in `backend/` fetches Maven itself, so no local install is
required.

## Run with Docker

The whole app (Angular frontend + Spring Boot backend + H2 database) ships as
a single container, built from the repo-root `Dockerfile` and run via
`docker-compose.yml`. This requires Docker on the host machine — it is **not**
available inside this project's dev container/sandbox, so this section
describes running the app elsewhere (e.g. your own machine or a home server),
not something exercised as part of development here.

```bash
docker-compose up --build
```

- The first build takes several minutes (it runs the same Maven build that
  compiles the backend and bundles the Angular frontend into the jar,
  including a one-time Node download). The Dockerfile's early layers cache
  Maven Central dependency resolution (keyed off `backend/pom.xml` and
  `frontend/package.json`/`package-lock.json`), but the build stage runs
  `mvnw clean package` — the `clean` wipes the Node/npm install those
  layers can't cache — so any real source change still re-downloads Node
  and re-runs the full frontend build; a `docker build` after a code
  change takes about as long as the first one in an environment with
  similarly slow extraction.
- Once you see Spring Boot's startup log line (`Started FamilyDashboardApplication...`),
  open [http://localhost:8080](http://localhost:8080) in a browser — that's
  the dashboard, served from the same container/port as the `/api/*` backend.
- Data lives in the `family-dashboard-data` named volume, mounted at `/data`
  inside the container (via the `SPRING_DATASOURCE_URL` environment
  variable). It persists across `docker-compose down` followed by
  `docker-compose up` — only `docker-compose down -v` (which removes named
  volumes) or manually deleting the volume clears it.
- Stop the app with `docker-compose down`.

## License

MIT — see [`LICENSE`](LICENSE).
