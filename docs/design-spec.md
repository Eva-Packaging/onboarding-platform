# Onboarding Platform – Design Spec

> This spec constrains how AI coding assistants (e.g., Claude Code) should implement and extend the onboarding system. It describes outcomes, architecture, data contracts, and constraints rather than specific implementation steps.

## 1. Outcomes

When this system is working:

- A user who successfully signs in with GitHub in the Next.js app can be registered in the backend with a single request. The backend creates an internal user, starts an onboarding workflow, and returns IDs and state for the frontend to track.
- The system asynchronously:
    - Correlates the GitHub identity with an Atlassian/Jira identity (or records that no match is possible).
    - Provisions the user into one or more GitHub teams.
    - Provisions the user into one or more Atlassian/Jira groups.
- The frontend can:
    - Display the logged-in user profile and a high-level onboarding state.
    - Poll a status endpoint to render step-level progress and whether the user needs to take action (e.g., accept a GitHub org invite).
- Admin/support can:
    - Inspect failed or stuck onboarding requests.
    - View provider-level audit history (GitHub / Atlassian calls).
    - Retry specific steps without re-registering the user.
    - Manually fix identity correlation and reprovision access.

These behaviors must be preserved whenever agents modify or extend the codebase.

## 2. Scope and boundaries

### In scope

- Backend services for:
    - Registration and onboarding orchestration.
    - Identity correlation (GitHub ↔ Atlassian).
    - GitHub team provisioning.
    - Atlassian/Jira group provisioning.
- REST APIs as described in [rest-api-reference.md](schemas/rest-api-reference.md) and [request-flow.md](request-flow.md).
- Kafka event contracts described in [kafka-event-schema.md](schemas/kafka-event-schema.md) (Avro + Schema Registry).
- Database schema described in [normalized-database-schema](schemas/normalized-database-schema.md).
- Next.js integration at the HTTP boundary (assume frontend has already done GitHub auth).

### Out of scope (for this spec)

- Implementing GitHub OAuth flows in Next.js (handled by Auth.js/NextAuth separately).
- Implementing Atlassian SSO or SCIM configuration.
- Non-GitHub identity providers (Google, Microsoft) – system should not be coupled to them.
- Non-HTTP interfaces (gRPC, GraphQL) – may be added later but not required now.

## 3. Architecture

### High-level components

- **Next.js frontend**
    - Handles GitHub sign-in via Auth.js/NextAuth.
    - Calls backend REST APIs with an authenticated bearer token.
    - Polls onboarding status and renders step states.

- **User Service (Spring Boot, package `xyz.catuns.onboarding.*`)**
    - Owns `user_account` and identity persistence.
    - Exposes `POST /api/v1/registrations` and `GET /api/v1/me`.
    - Publishes `UserRegisteredV1` events to Kafka.

- **Onboarding Service (Spring Boot)**
    - Owns `onboarding_request` and `onboarding_step`.
    - Consumes `UserRegisteredV1` and other events.
    - Exposes status and admin/support APIs:
        - `GET /api/v1/onboarding/{requestId}`
        - `POST /api/v1/onboarding/{requestId}/retry`
        - `GET /api/v1/admin/onboarding`
        - `GET /api/v1/admin/onboarding/{requestId}/audit`
    - Orchestrates step transitions and publishes lifecycle events.

- **Provisioning Service(s) (Spring Boot)**
    - GitHub provisioning:
        - Consumes `GithubProvisioningRequestedV1`.
        - Calls GitHub APIs to add or update org/team membership.
        - Publishes `GithubProvisioningCompletedV1` with success, pending, or failure state.
    - Atlassian provisioning:
        - Consumes `AtlassianProvisioningRequestedV1`.
        - Calls Atlassian/Jira APIs (or SCIM) for group membership.
        - Publishes `AtlassianProvisioningCompletedV1`.

- **Identity Service (optional – may be a module in Onboarding Service)**
    - Consumes `IdentityCorrelationRequestedV1`.
    - Produces `IdentityCorrelationCompletedV1` or `IdentityCorrelationFailedV1`.
    - Exposes:
        - `GET /api/v1/users/{userId}/identity-links`
        - `POST /api/v1/admin/users/{userId}/identity-links`

- **Configuration/Admin APIs**
    - Manage provider targets (GitHub teams, Atlassian groups).
    - Manage group-mapping rules (role/cohort → target).
    - Reprovisioning and provider health.

### Data and storage

Backed by PostgreSQL using the normalized schema:

- `user_account`, `identity_link`, `onboarding_request`, `onboarding_step`,
  `external_group_mapping`, `provisioning_audit_log`, and related tables.

Kafka topics and Avro schemas:

- Topics like `edu.user.registered.v1`, `edu.provisioning.github.v1`, etc., with
  record namespaces under `xyz.catuns.onboarding.events.*`.

Agents should treat `normalized-database-schema.md` and `kafka-event-schema.md` as the source of truth.

## 4. Key flows

### 4.1 Registration and onboarding start

- Precondition: User is authenticated with GitHub on the frontend.
- Frontend calls `POST /api/v1/registrations` with GitHub identity and basic profile.
- Backend:
    - Upserts `user_account` + `identity_link` for GitHub.
    - Creates `onboarding_request` with initial steps:
        - `IDENTITY_CORRELATION`
        - `GITHUB_TEAM_PROVISIONING`
        - `JIRA_GROUP_PROVISIONING`
    - Publishes `UserRegisteredV1` and/or `OnboardingRequested` events.
    - Returns `userId`, `onboardingRequestId`, `status`, `steps[]`, and `correlationId`.

### 4.2 Frontend polling

- Frontend calls:
    - `GET /api/v1/me?include=identities,onboarding` for header/profile state.
    - `GET /api/v1/onboarding/{requestId}` to drive onboarding UI.
- Backend responds with:
    - Request-level state: `IN_PROGRESS`, `COMPLETED`, `FAILED`, `PARTIAL_SUCCESS`, `ACTION_REQUIRED`.
    - Per-step state: `PENDING`, `PROCESSING`, `SUCCEEDED`, `PENDING_EXTERNAL_ACCEPTANCE`, `FAILED`, `MANUAL_REVIEW`.

### 4.3 Provisioning internals

- Onboarding Service, based on `user` and `group-mappings`, emits:
    - `GithubProvisioningRequestedV1` events for each GitHub target.
    - `AtlassianProvisioningRequestedV1` events for each Atlassian target.
- Provisioning Services:
    - Call external APIs, handle rate limits and retries.
    - Log calls into `provisioning_audit_log`.
    - Emit `CompletedV1` events that the Onboarding Service uses to update `onboarding_step`.

### 4.4 Identity correlation

- Onboarding Service emits `IdentityCorrelationRequestedV1`.
- Identity Service:
    - Attempts to match GitHub identity to Atlassian identity (e.g., by email).
    - Emits `IdentityCorrelationCompletedV1` or `IdentityCorrelationFailedV1`.
- Onboarding Service updates the identity step state accordingly.

### 4.5 Support/admin flows

- Listing: `GET /api/v1/admin/onboarding` with filters.
- Audit: `GET /api/v1/admin/onboarding/{requestId}/audit`.
- Retry: `POST /api/v1/onboarding/{requestId}/retry` with selected steps.
- Manual identity link:
    - `POST /api/v1/admin/users/{userId}/identity-links`.
- Reprovisioning:
    - `POST /api/v1/admin/users/{userId}/reprovision`.
- Provider health:
    - `GET /api/v1/admin/provider-health`.

Agents must not break these flows when refactoring or adding features.

## 5. Constraints and assumptions

- **Languages & frameworks**
    - Backend: Java, Spring Boot 3+, Spring Web, Spring Data JPA, Spring Kafka.
    - Frontend: Next.js with Auth.js/NextAuth for GitHub auth.
    - DB: PostgreSQL.
    - Eventing: Kafka with Avro and Schema Registry.

- **Package and namespaces**
    - Java package root: `xyz.catuns.onboarding`.
    - Avro namespaces: `xyz.catuns.onboarding.events`.

- **Persistence**
    - Use the normalized schema from the schema docs; do not introduce new tables without updating the schema docs.
    - Prefer JPA entities for DB persistence; keep DTOs separate for REST.

- **API stability**
    - REST endpoints, request/response shapes, and status codes should align with `rest-api-reference.md`.
    - Non-backward compatible API changes require explicit spec updates.

- **Events and schema registry**
    - All Kafka payloads must match the Avro schemas in `kafka-event-schema.md`.
    - Use backward-compatible changes only (add optional fields with defaults; don’t remove/rename existing fields without versioning).

- **Security**
    - Assume an authenticated bearer token is present on all `/api/v1` calls.
    - Enforce admin-only access on `/api/v1/admin/**`.

## 6. Prior decisions

Agents must treat these as fixed unless the spec is explicitly updated:

- **Backend entry point** for onboarding is `POST /api/v1/registrations`, not `/auth/exchange`. Auth is handled by the frontend; backend handles registration and onboarding.
- **Onboarding is asynchronous**. Do not make external provisioning synchronous in user-facing endpoints.
- **Outbox/event-driven pattern**: services should use a reliable publish mechanism for Kafka events and not publish directly in the middle of DB transactions without an outbox or equivalent mechanism.
- **Role and mapping model**:
    - Internal roles (e.g., `STUDENT`, `INSTRUCTOR`) are mapped to provider targets via `group-mappings`.
    - Provider targets (GitHub teams, Atlassian groups) are configured via admin APIs.

## 7. Work breakdown for agents

When implementing or extending features, AI agents should work in small, focused tasks, for example:

1. **Entity/model changes**
    - Update JPA entities under `xyz.catuns.onboarding.*` to match schema docs.
    - Add migration scripts if necessary (e.g., Flyway/Liquibase).
    - Keep entity changes in a dedicated commit.

2. **New REST endpoint**
    - Add controller method(s) under appropriate module.
    - Add request/response DTOs.
    - Wire to service layer; add tests (unit + minimal integration).
    - Update `rest-api-reference.md` if shape changes.

3. **Event producer/consumer**
    - Implement Avro model usage per `kafka-event-schema.md`.
    - Configure Spring Kafka producers/consumers and error handling.
    - Add tests for serialization, deserialization, and handler behavior.

4. **Business logic**
    - Implement or adjust onboarding step transitions.
    - Preserve idempotency and correct handling of retries.
    - Update `request-flow.md` if behavior changes.

5. **Docs & validation**
    - Keep README, design spec, and reference docs in sync with non-trivial changes.
    - Do not generate additional “spec-like” files without updating this spec.

## 8. Verification / acceptance criteria

Changes are considered correct when:

- All unit and integration tests pass.
- REST endpoints behave as documented:
    - Expected status codes.
    - Error envelope shape.
    - No breaking changes to existing payloads without spec update.
- Kafka events produced and consumed conform to the Avro schemas and compatibility rules.
- Onboarding flows still work end to end:
    - New user can be registered.
    - Onboarding status can be polled.
    - Steps transition correctly based on events.
- Admin/support flows remain functional:
    - Listing, audit, retry, identity repair, reprovision, provider health endpoints respond as designed.