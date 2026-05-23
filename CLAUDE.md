# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

```
frontend/          Nx monorepo (pnpm) — Next.js app + auth feature library
backend/          Spring Boot microservices (Maven multi-module)
infra/docker/      Docker Compose for local Postgres + Kafka + Schema Registry
infra/terraform/   GCP infrastructure (Cloud Run, Cloud SQL, Secret Manager)
docs/              Authoritative specs — treat as source of truth
```

## Frontend commands

All frontend commands run from the `frontend/` directory with pnpm.

```bash
# Dev server
pnpm nx run web:serve

# Build
pnpm nx run web:build

# Lint
pnpm nx run web:lint

# Test (all)
pnpm nx run web:test

# Test (single file or describe block)
pnpm nx run web:test --testFile=apps/web/specs/index.spec.tsx

# Run targets across all projects
pnpm nx run-many -t build
pnpm nx run-many -t test
```

## Backend (Spring Boot) commands

Each service lives under `backend/` and has its own Maven wrapper.

```bash
# Run a specific service (from its directory)
cd backend/user-service && ./mvnw spring-boot:run
cd backend/onboarding-service && ./mvnw spring-boot:run
cd backend/provisioning-service && ./mvnw spring-boot:run

# Build all (from a service directory)
./mvnw clean package

# Test all
./mvnw test

# Test a specific class or method
./mvnw test -Dtest=UserRegistrationServiceTest
./mvnw test -Dtest=UserRegistrationServiceTest#shouldCreateUserOnValidRequest
```

## Local infrastructure

```bash
# Start Postgres + Kafka + Schema Registry
docker compose -f infra/docker/docker-compose.yml up -d

# Stop
docker compose -f infra/docker/docker-compose.yml down

# Env vars — copy and fill before first run
cp infra/docker/.env.example infra/docker/.env
```

Postgres initialises three databases automatically (`user_service`, `onboarding_service`, `provisioning_service`). Each Spring Boot service connects to its own database. Kafka runs in KRaft mode on port 9092. Schema Registry is exposed on port 9090.

## Architecture overview

### Service boundaries and data ownership

| Service | Owns tables | Produces events | Exposes |
|---|---|---|---|
| `user-service` | `user_profile`, `external_identity`, `outbox_event` | `UserRegisteredV1` | `POST /api/v1/registrations`, `GET /api/v1/me` |
| `onboarding-service` | `onboarding_request`, `onboarding_step`, `outbox_event` | provisioning request events, lifecycle events | status, retry, admin, identity APIs |
| `provisioning-service` | `provisioning_audit_log`, `outbox_event` | provisioning completion events | provider health |

### Request flow (happy path)

1. Frontend finishes GitHub OAuth (Auth.js/NextAuth handles this entirely).
2. Frontend calls `POST /api/v1/registrations` with the GitHub identity snapshot.
3. `user-service` upserts `user_profile` + `external_identity`, creates `onboarding_request` + initial steps, writes `UserRegisteredV1` to the outbox.
4. `onboarding-service` consumes `UserRegisteredV1`, emits `IdentityCorrelationRequestedV1`, `GithubProvisioningRequestedV1`, `AtlassianProvisioningRequestedV1`.
5. `provisioning-service` calls provider APIs, logs to `provisioning_audit_log`, emits completion events.
6. `onboarding-service` consumes completion events and updates step states.
7. Frontend polls `GET /api/v1/onboarding/{requestId}` until the request reaches a terminal state.

### Frontend structure

- `frontend/apps/web/` — Next.js app (pages: `/register`, `/onboarding`, `/dashboard`, `/support`)
- `frontend/features/auth/` — Auth.js/NextAuth feature library; GitHub OAuth + Credentials providers, JWT callbacks that capture `githubId`, `githubLogin`, `githubAccessToken`
- `frontend/apps/web/lib/config/env.ts` — Zod-validated env schema; `BACKEND_API_URL` is required

### Key naming conventions

- Java package root: `xyz.catuns.onboarding`
- Avro record namespace: `xyz.catuns.onboarding.events`
- Kafka topics: `edu.user.registered.v1`, `edu.identity.correlation.v1`, `edu.provisioning.github.v1`, `edu.provisioning.atlassian.v1`, `edu.onboarding.lifecycle.v1`

## Hard constraints (from design-spec.md)

**Do not break these without updating the spec:**

- Backend registration entry point is `POST /api/v1/registrations`. Auth is frontend-only; the backend never handles GitHub OAuth.
- All external provisioning must be **async**. Never make GitHub or Atlassian calls synchronously inside user-facing request handlers.
- Kafka events must be published via the **outbox pattern** — write to `outbox_event` in the same DB transaction as the domain change, never publish directly mid-transaction.
- All Kafka payloads must conform to the Avro schemas in `docs/schemas/kafka-event-schema.md`. Schema evolution must be **backward-compatible** (add optional fields with defaults; never remove or rename existing fields without versioning).
- REST endpoint shapes and status codes must match `docs/rest-api-reference.md`. Non-backward-compatible changes require updating that doc.
- DB changes require updating `docs/schemas/normalized-database-schema.md` and a migration script (Flyway/Liquibase).
- `GET /api/v1/admin/**` endpoints must enforce admin-only access.

## Docs as source of truth

| Doc | What it governs |
|---|---|
| `docs/design-spec.md` | Architecture decisions, constraints, acceptance criteria |
| `docs/rest-api-reference.md` | All REST endpoint shapes, status codes, error envelope |
| `docs/request-flow.md` | End-to-end flow descriptions for all workflows |
| `docs/schemas/normalized-database-schema.md` | Canonical DB schema — don't add tables without updating this |
| `docs/schemas/kafka-event-schema.md` | Avro schemas, topic names, compatibility rules |
| `docs/roadmap.md` | Phased delivery plan; each phase has explicit exit criteria |

## Backlog generation

When asked to generate Jira backlog JSON (projects, epics, stories, or tasks), follow the rules and schemas defined in `.claude/backlog-generator.md`. That file is the authoritative spec for output format, interaction flow, and sizing guidance. Output files go to `docs/backlog/<relevant-filename>.json`.
