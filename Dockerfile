# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
#
# This Dockerfile lives at the repo root (not backend/Dockerfile) because the
# Maven build it runs reaches into ../frontend (see backend/pom.xml's
# frontend-maven-plugin config, added in Phase 4 Ticket 1) -- the build
# context therefore has to include both backend/ and frontend/, which a
# backend/Dockerfile with a backend/-scoped context could not do without
# extra -f/context gymnastics in docker-compose.yml. docker-compose.yml uses
# `context: .` (the repo root) to match.
#
# JDK 25 matches backend/pom.xml's <java.version>25</java.version>.
# eclipse-temurin publishes an image per GA'd Java feature/LTS release
# (21-jdk, 21-jre, etc. after Java 21 shipped); Java 25 (LTS) GA'd September
# 2025, so `eclipse-temurin:25-jdk` / `eclipse-temurin:25-jre` are the
# expected tags. This could not be confirmed by actually pulling the image
# in this sandbox (no `docker` here, no registry egress) -- verify the tag
# exists on first `docker build` and swap to a dated/variant tag (e.g.
# `25-jdk-jammy`, `25-jdk-noble`) if the bare tag has been retired.
#
# No Node/npm is installed in this image: backend/pom.xml's
# frontend-maven-plugin downloads its own pinned Node v24.20.0 during the
# Maven build itself (bound to the `prepare-package` phase, see the plugin
# comment in backend/pom.xml). This stage therefore just needs a JDK and
# runs the exact same command already proven to work outside Docker:
#
#   cd backend && ./mvnw clean package
#
# -- nothing more, nothing different.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Layer 1 -- Maven wrapper + pom.xml only. This changes only when a
# dependency/plugin version changes, so it -- and the ~/.m2 local
# repository it populates -- stays cached across ordinary source edits.
# `dependency:go-offline` resolves the project's dependencies AND its
# declared plugins (spring-boot-maven-plugin, frontend-maven-plugin,
# maven-resources-plugin) from Maven Central, without running any plugin
# *executions* -- so it does not trigger frontend-maven-plugin's
# install-node-and-npm/npm goals (those are bound to the prepare-package
# phase of the `package` lifecycle, not touched by dependency:go-offline).
# The real Node download and npm install/build only happen in the
# `./mvnw clean package` layer below, identical to the proven build.
COPY backend/mvnw backend/mvnw
COPY backend/.mvn backend/.mvn
COPY backend/pom.xml backend/pom.xml
RUN cd backend && ./mvnw -B dependency:go-offline

# Layer 2 -- frontend dependency manifests only, so a change to these two
# files invalidates the cache from here on without unrelated source edits
# doing the same. There is no supported way to run `npm ci` against
# frontend-maven-plugin's own downloaded Node before the Maven build
# itself runs (it only exists once install-node-and-npm has executed), so
# this alone doesn't pre-warm npm's cache -- it only isolates manifest
# changes from source changes for Docker's layer cache. Copying these
# early is still worth doing: layer 3 below re-copies the same files as
# part of the full frontend/ tree, so no divergent copy of the manifests
# is introduced.
COPY frontend/package.json frontend/package.json
COPY frontend/package-lock.json frontend/package-lock.json

# Layer 3 -- the rest of the source, then the real, already-proven build.
COPY backend/src backend/src
COPY frontend/ frontend/

RUN cd backend && ./mvnw clean package

# ---------------------------------------------------------------------------
# Runtime stage
#
# Slim JRE (no JDK/compiler needed at runtime), same Java 25 line as the
# build stage. Only the built jar is copied in -- no source, no Maven/Node
# toolchains, no build cache.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
