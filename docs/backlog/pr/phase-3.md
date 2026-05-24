## EDP-179 · Phase 3: Onboarding Orchestration

Exposes the public-facing onboarding status and retry APIs in `onboarding-service` and wires provider target resolution so each created step is assigned to the correct GitHub team or Atlassian group based on the user's roles. The frontend can now poll `GET /api/v1/onboarding/{requestId}` for step-level progress, users can self-recover failed steps via `POST /api/v1/onboarding/{requestId}/retry`, and the `group_mapping_rule` table drives `providerTargetId` assignment at initialisation time. All endpoint shapes match `docs/rest-api-reference.md` sections 3 and 4.

---

## Commits

| Commit | Story | Summary |
|---|---|---|
| `1fe3e5b` | EDP-180 | GET /api/v1/onboarding/{requestId} — public onboarding status endpoint |
| `51addfe` | EDP-181 | POST /api/v1/onboarding/{requestId}/retry — retry failed onboarding steps |
| `9507ecb` | EDP-182 | Provider target resolution from role-based group mapping rules |

---

## What's in this PR

### EDP-180 · GET /api/v1/onboarding/{requestId} — public onboarding status endpoint

**Domain and persistence**

- `ProviderTarget` JPA entity mapping `provider_target` — `id`, `providerId`, `targetType`, `externalKey`, `displayName`, `enabled`; `@PrePersist`/`@PreUpdate` maintain `createdAt`/`updatedAt`
- `ProviderTargetRepository extends JpaRepository<ProviderTarget, UUID>` — used for bulk `findAllById` lookup during status assembly
- `V2__add_provider_target_table.sql` Flyway migration — `provider_target` table with `UNIQUE(provider_id, target_type, external_key)` and an index on `provider_id`

**DTOs**

- `ProviderTargetDto(String provider, String targetType, String externalKey)` — `provider` is derived from `targetType` prefix (`GITHUB*` → `"GITHUB"`, `ATLASSIAN*` → `"ATLASSIAN"`)
- `StepDetailDto` (`@JsonInclude(NON_NULL)`) — `type`, `state`, `target` (nullable), `attemptCount`, `lastErrorCode`, `startedAt`, `completedAt`
- `OnboardingStatusResponse` — `requestId`, `userId`, `state`, `correlationId`, `startedAt`, `steps[]`

**Service**

- `OnboardingStatusService.findById(requestId, callerId)` (`@Transactional(readOnly = true)`):
  - Throws `ResourceNotFoundException` if request absent (mapped to 404)
  - Throws `OnboardingAccessDeniedException` if `request.userProfileId ≠ callerId` (mapped to 403)
  - Loads steps via `OnboardingStepRepository.findByOnboardingRequest_Id`; bulk-loads provider targets with a single `findAllById` call; assembles `StepDetailDto` list in one pass

**Security**

- `JwtPrincipalExtractor` — strips `Bearer ` prefix, calls `AuthTokenProvider.getClaims(token)`, parses the `user` claim as a UUID (throws `IllegalStateException` for missing or non-UUID values)
- `JwtConfig` — exposes `AuthTokenProvider` as a `@Bean` from `JwtProperties`

**Controller and error handling**

- `OnboardingController` at `@RequestMapping("/api/v1/onboarding")` — `GET /{requestId}` extracts caller UUID from JWT and delegates to `OnboardingStatusService`; returns HTTP 200
- `GlobalExceptionHandler` extended with:
  - `ResourceNotFoundException` → 404 `NOT_FOUND`
  - `OnboardingAccessDeniedException` → 403 `FORBIDDEN`

---

### EDP-181 · POST /api/v1/onboarding/{requestId}/retry — retry failed onboarding steps

**State machine update**

- `StepStateTransitions.ALLOWED` extended: `FAILED → {PENDING, PROCESSING}` and `MANUAL_REVIEW → {PENDING, PROCESSING, FAILED}`. Adding `PENDING` as a valid target from both retryable states allows the retry endpoint to re-queue steps without bypassing the state machine.

**DTOs**

- `OnboardingRetryRequest` — `@NotEmpty List<String> steps`, nullable `String reason`; `@NotEmpty` triggers 400 when an empty array is submitted
- `OnboardingRetryResponse` — `requestId`, `state`, `requeuedSteps`, `correlationId`; shape matches `docs/rest-api-reference.md` section 4

**Exception and error mapping**

- `StepNotRetryableException` — carries `stepKey`; mapped to HTTP 409 `STEP_NOT_RETRYABLE` in `GlobalExceptionHandler` with `details.stepType` set to the offending step key

**Service**

- `OnboardingRetryService.retry(requestId, retryRequest)` (`@Transactional`):
  - Loads request (404 if absent); loads all steps for the request; builds a `Map<String, OnboardingStep>` keyed by step type name
  - Iterates requested step keys — throws `StepNotRetryableException` if a key is absent from the map OR if the step's state is not in `{FAILED, MANUAL_REVIEW}`; **no state changes are made before all keys are validated**
  - Transitions each eligible step to `PENDING` via `OnboardingDomainService.transitionStep`, which calls `recalculateRequestState` internally after each transition
  - Returns `OnboardingRetryResponse` with the request's updated state

**Controller**

- `OnboardingController` extended with `POST /{requestId}/retry` — accepts `@Valid @RequestBody OnboardingRetryRequest`, delegates to `OnboardingRetryService`, returns HTTP 202

---

### EDP-182 · Provider target resolution from role-based group mapping rules

**Migration**

- `V3__add_app_role_and_group_mapping_rule_tables.sql`:
  - `app_role` table — `id`, `role_key` (unique), `display_name`; seeded with fixed UUIDs for `STUDENT`, `INSTRUCTOR`, and `ADMIN`
  - `group_mapping_rule` table — `id`, `app_role_id` (FK), `provider_target_id` (FK), `cohort_key` (nullable), `priority_order`, `enabled`; index on `app_role_id`

**Entities and repositories**

- `AppRole` — `@Immutable` entity; no setters; read via `AppRoleRepository.findByRoleKeyIn(Collection<String>)`
- `GroupMappingRule` — mutable entity with `@PrePersist`/`@PreUpdate`; queried via `GroupMappingRuleRepository.findByAppRoleIdInAndEnabledTrueOrderByPriorityOrderAsc(Collection<UUID>)` — the repository-level `enabled = true` filter and `ORDER BY priority_order ASC` eliminate any in-memory sorting

**Service**

- `ProviderTargetResolutionService.resolveTarget(roleKeys, targetType)`:
  - Returns `Optional.empty()` immediately for null or empty `roleKeys`
  - Resolves role keys to app role UUIDs via `AppRoleRepository`; warns and returns empty when no rows are found
  - Loads enabled `GroupMappingRule` rows ordered by `priority_order ASC`; bulk-loads the referenced `ProviderTarget` rows in a single `findAllById` call; filters in memory for `targetType` match and `enabled = true`
  - Returns the `providerTargetId` of the first rule (lowest `priority_order`) whose target passes both filters

**Wiring into initialisation**

- `OnboardingInitialisationService` extended with `ProviderTargetResolutionService` dependency
- `TARGET_TYPE_BY_STEP` constant map: `GITHUB_TEAM_PROVISIONING → "GITHUB_TEAM"`, `JIRA_GROUP_PROVISIONING → "ATLASSIAN_GROUP"`; `IDENTITY_CORRELATION` has no entry, so resolution is skipped for that step type without an explicit conditional
- When resolution returns `Optional.empty()` for a provisioning step, a `WARN` log line records `stepType` and `roleKeys`; `providerTargetId` is left `null` and initialisation continues

---

## Test coverage

| Service | Story | Test type | Class | Tests |
|---|---|---|---|---|
| onboarding-service | EDP-180 | Mockito unit | `OnboardingStatusServiceTest` | 6 |
| onboarding-service | EDP-180, EDP-181 | Standalone MockMvc | `OnboardingControllerTest` | 8 |
| onboarding-service | EDP-181 | Mockito unit | `OnboardingRetryServiceTest` | 7 |
| onboarding-service | EDP-181 | Mockito unit | `OnboardingStepStateMachineTest` (2 new) | 2 |
| onboarding-service | EDP-182 | Mockito unit | `ProviderTargetResolutionServiceTest` | 7 |

30 new tests across 5 test classes. Full module suite passes at 87 tests.

---

## Key design decisions

**Retry transitions to `PENDING`, not `PROCESSING`.** The retry endpoint's role is re-queuing, not execution. Transitioning failed steps to `PENDING` allows the Kafka consumer to pick them up and drive the `PENDING → PROCESSING → SUCCEEDED/FAILED` cycle naturally. Transitioning directly to `PROCESSING` would bypass the consumer and misrepresent in-flight state. `StepStateTransitions` was extended with `FAILED → PENDING` and `MANUAL_REVIEW → PENDING` to formalise this.

**Full validation before any state mutation.** `OnboardingRetryService` iterates all requested step keys and checks retryability before transitioning any step. If a single key is non-retryable or absent, `StepNotRetryableException` is thrown and no steps are modified. This prevents partial mutations that would leave the request in an inconsistent state.

**`StepNotRetryableException` covers missing step type and non-retryable state uniformly.** A step key that does not match any step in the request is indistinguishable from a step in a terminal state from the caller's perspective — both result in 409 with `details.stepType` identifying the offending key.

**Bulk provider target lookup avoids N+1.** `ProviderTargetResolutionService` collects all `providerTargetId` values from the ordered rule list, loads them in one `findAllById` call, then filters and matches in memory. The sort order is enforced by the repository query (`ORDER BY priority_order ASC`), so no in-memory sort is needed to find the highest-priority match.

**`IDENTITY_CORRELATION` skip is implicit.** `TARGET_TYPE_BY_STEP` has no entry for `IDENTITY_CORRELATION`; `Map.get` returns `null`, the `if (targetType != null)` guard skips the resolution call. No explicit step-type check is needed inside the loop body.

**Fixed UUIDs for seeded `app_role` rows.** Inserting well-known UUIDs for `STUDENT`, `INSTRUCTOR`, and `ADMIN` in the V3 migration allows `group_mapping_rule` seed data in later migrations or test fixtures to reference these roles by ID without a pre-lookup.

---

## Docs & schema references

- REST contract: `docs/rest-api-reference.md` — section 3 (`GET /api/v1/onboarding/{requestId}`) and section 4 (`POST /api/v1/onboarding/{requestId}/retry`)
- Database schema: `docs/schemas/normalized-database-schema.md` — tables 5 (`app_role`), 7 (`provider_target`), 8 (`group_mapping_rule`)
- Architecture constraints: `docs/design-spec.md` — async provisioning, state machine determinism
- Roadmap exit criteria: `docs/roadmap.md` — Phase 3
- Backlog: `docs/backlog/phase-3-onboarding-orchestration.json`

---

## How to verify locally

```bash
# Start Postgres + Kafka + Consul
docker compose -f infra/docker/docker-compose.yml up -d

# Run onboarding-service tests
cd backend/onboarding-service && ./mvnw test

# Boot onboarding-service
cd backend/onboarding-service && ./mvnw spring-boot:run

# Poll onboarding status (requires a valid JWT and an existing requestId)
curl -s http://localhost:8082/api/v1/onboarding/<requestId> \
  -H "Authorization: Bearer <token>" | jq .

# Retry a failed step
curl -s -X POST http://localhost:8082/api/v1/onboarding/<requestId>/retry \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"steps":["JIRA_GROUP_PROVISIONING"],"reason":"Atlassian sync recovered"}' | jq .
```

Testcontainers spins up isolated Postgres and Kafka per test run — no local infrastructure required for the unit or integration test suites.
