# Plan

Family dashboard: private use first, also published on GitHub as a portfolio
piece. Runs on a wall-mounted touchscreen in the living room eventually.

## Decisions

- Backend: Spring Boot, Java. Frontend: Angular. Chosen to build on existing
  skills rather than to learn a new stack.
- Monorepo: `backend/` and `frontend/` in this one repo.
- No authentication — shared, no-login dashboard. LAN-only access; no
  remote/outside-home access planned for now.
- Persistence: H2, file-persistent mode, via Spring Data JPA from the start
  — written in a JPA/Hibernate-portable way (avoid H2-specific SQL) so it
  can be swapped to PostgreSQL later without a rewrite. Flyway arrives when
  PostgreSQL does.
- Household members are hardcoded in config for v1 (e.g. `application.yml`)
  — no admin UI/DB-backed CRUD yet.
- Widget architecture: a backend `Widget` contract + registry exposed via
  `/api/widgets`; the Angular shell dynamically mounts one component per
  widget type. Each widget owns its own endpoints/persistence behind the
  shared contract, so adding a widget later doesn't touch the framework.
- Portfolio polish (tests, CI via GitHub Actions, Docker/Compose, docs) is
  baked into every phase from Phase 0, not bolted on at the end.
- Deployment hardware (Raspberry Pi vs NAS vs server) is intentionally left
  undecided/deferred — not a blocker for v1, which runs via
  `docker-compose up`.

## Phases

- [x] **Phase 0 — Scaffolding.** Devcontainer, `CLAUDE.md` conventions. Still
      open: CI skeleton, Dockerfiles/compose, license.
- [ ] **Phase 1 — Widget framework backend.** `Widget` contract, registry,
      `/api/widgets`, unit tests.
- [ ] **Phase 2 — Dashboard shell frontend.** Dynamic widget loading from the
      API, component tests.
- [ ] **Phase 3 — Todo widget.** First real widget built on the framework —
      per-household-member CRUD lists, end-to-end.
- [ ] **Phase 4 — Deployment packaging.** `docker-compose up` runs the full
      stack; sanity-check touch usability at kiosk scale.
- [ ] **Phase 5 (later, separately scoped).** Weather widget, transit/train
      widget, layout persistence/customization, remote access, real hardware
      deployment.

Each phase (or a smaller slice of one, if it doesn't fit a single sensible
branch) is implemented following the workflow in `CLAUDE.md`.
