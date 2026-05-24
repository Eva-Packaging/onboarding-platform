## EDP-144 · Phase 2: Registration API

Implements the full GitHub-first registration flow across `user-service` and `onboarding-service`. A frontend that has completed GitHub OAuth can `POST /api/v1/registrations` and immediately receive a user ID, onboarding request ID, correlation ID, and initial step list. The authenticated user profile is then retrievable via `GET /api/v1/me` with optional identity and onboarding includes. All inter-service calls are traced with a propagated correlation ID that appears in every log line.

---

## Commits

| Commit | Story | Summary |
|---|---|---|
| `5ee8fa4` | EDP-145 | Registration DTO, Bean Validation, and global error envelope — user-service |
| `3900875` | EDP-146 | UserRegistrationService — user profile upsert and GitHub identity snapshot |
| `9ee5eef` | EDP-144 | Consul service discovery — container in docker-compose, config for user-service and onboarding-service |
| `a96ffb2` | EDP-147 | POST /api/v1/registrations controller and synchronous onboarding-service call |
| `482e4bb` | EDP-148 | Onboarding request initialisation — internal endpoint in onboarding-service |
| `09a04f5` | EDP-149 | GET /api/v1/me — current user profile with optional includes |
| `f41cdb3` | EDP-149 | GET /api/v1/me — fix jwt-core classpath; use AuthTokenProvider directly |
| `44dcee4` | EDP-150 | Correlation ID propagation and MDC request tracing |

---

## What's in this PR

### EDP-145 · Registration DTO, Bean Validation, and global error envelope — user-service

- `RegistrationRequest` — `@NotBlank githubUserId`, `@NotBlank githubLogin`, `@NotBlank displayName`, nullable `primaryEmail` (`@Email`), `avatarUrl`, `roleKeys`
- `RegistrationResponse` — `userId`, `onboardingRequestId`, `status`, `correlationId`, `steps[]`
- `StepSummaryDto` — `type` and `state` string pair
- `ErrorResponse` extending Spring's `ProblemDetail` with `code` (String) and `correlationId` (UUID) fields
- `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping:
  - `MethodArgumentNotValidException` → 400
  - `ConstraintViolationException` → 400
  - `DuplicateRegistrationException` → 409
  - `OnboardingServiceUnavailableException` → 503
  - `NoSuchElementException` → 404
  - `Throwable` → 500
- `correlationId` in every error response read from MDC so it is available immediately, even before the filter story landed

### EDP-146 · UserRegistrationService — user profile upsert and GitHub identity snapshot

- `UserRegistrationService.register()` (`@Transactional`):
  - Resolves existing `ExternalIdentity` by `(GITHUB, githubUserId)` — throws `DuplicateRegistrationException` if the linked `UserProfile.status` is `ACTIVE`
  - Creates `UserProfile` (`status = PENDING_ONBOARDING`), `ExternalIdentity` (`is_primary = true`), and `UserRoleAssignment` rows for each resolved `roleKey`
  - Generates a UUID `correlationId` and writes an unpublished `UserRegisteredV1` `OutboxEvent` row in the same transaction (Kafka relay deferred to Phase 4)
- `RegistrationResult` record — carries `userId` and `correlationId` out of the service
- `DuplicateRegistrationException` — unchecked, holds the conflicting `githubUserId`
- `UserRegisteredV1PayloadBuilder` — serialises the outbox payload to a JSON string

### EDP-144 · Consul service discovery

- Consul container added to `infra/docker/docker-compose.yml`
- `spring-cloud-starter-consul-discovery` added to user-service `pom.xml`
- `spring.cloud.consul.*` block in user-service `application.yaml` (`CONSUL_HOST`, `CONSUL_PORT` env vars with localhost defaults)
- `spring-cloud-starter-openfeign` wired in user-service; `@EnableFeignClients` on `UserServiceApplication`
- `app.feign.clients.onboarding: onboarding-service` — Feign resolves the address via Consul by service name

### EDP-147 · POST /api/v1/registrations controller and synchronous onboarding-service call

- `RegistrationController.register()` — calls `UserRegistrationService` then `OnboardingServiceClient`; composes the HTTP 201 `RegistrationResponse` from both results
- `OnboardingServiceFeignClient` (package-private) — `@FeignClient` targeting `onboarding-service` at path `/api/v1/internal`; `POST /onboarding-requests`
- `OnboardingServiceClient` (`@Component`) — wraps the Feign client; catches `FeignException` and rethrows as `OnboardingServiceUnavailableException`
- `OnboardingInitRequest` / `OnboardingInitResponse` client records — `userId`, `correlationId`, `roleKeys` in; `requestId`, `state`, `steps[]` out
- `FeignCorrelationInterceptor` — reads `correlationId` from MDC and attaches it as `X-Correlation-ID` on every outbound Feign call
- `OnboardingServiceUnavailableException` — maps to HTTP 503 via `GlobalExceptionHandler`

### EDP-148 · Onboarding request initialisation — internal endpoint in onboarding-service

- DTOs in `xyz.catuns.onboarding.service.api.dto`: `OnboardingInitRequest` (`@NotNull userId`, `@NotNull correlationId`, nullable `roleKeys`), `OnboardingInitResponse`, `StepSummaryDto`, `OnboardingLatestResponse`, `ErrorResponse`
- `GlobalExceptionHandler` in onboarding-service — same `MethodArgumentNotValidException` / `ConstraintViolationException` / `Throwable` mapping as user-service
- `OnboardingInitialisationService.initialise()` (`@Transactional`):
  - Creates `OnboardingRequest` (`state = REQUESTED`, `source = SELF_REGISTRATION`)
  - Calls `OnboardingDomainService.startRequest()` to transition to `IN_PROGRESS` within the same transaction
  - Creates one `OnboardingStep` per `StepType` (IDENTITY_CORRELATION, GITHUB_TEAM_PROVISIONING, JIRA_GROUP_PROVISIONING) with `state = PENDING` and `providerTargetId = null` (provider-target resolution deferred to Phase 3)
- `OnboardingInternalController` at `POST /api/v1/internal/onboarding-requests` — returns HTTP 201

### EDP-149 · GET /api/v1/me — current user profile with optional includes

- `JwtPrincipalExtractor` — injects `AuthTokenProvider`, strips the `Bearer ` prefix from the `Authorization` header, calls `tokenProvider.getClaims(token)`, returns the `user` claim as `githubUserId`
- Response DTOs: `MeResponse` (`@Builder`, `@JsonInclude(NON_NULL)`), `GitHubIdentitySummary` (`userId`, `login`, `email`), `AtlassianIdentitySummary` (`accountId`, `email`, `matchState`), `OnboardingSummary` (`requestId`, `state`)
- `UserProfileService.getMe(githubUserId, includes)` (`@Transactional(readOnly = true)`):
  - Finds `ExternalIdentity` by `(GITHUB, githubUserId)` — 404 if absent
  - When `include=identities`: adds GitHub summary; queries for Atlassian identity (`matchState = MATCHED` or `PENDING` when none found)
  - When `include=onboarding`: calls `OnboardingServiceClient.getLatestOnboardingForUser()` — `Optional.empty()` on 404
- `UserProfileController` at `GET /api/v1/me` — parses comma-separated `include` param into a `Set<String>`
- `ExternalIdentityRepository` extended with `findByProvider_ProviderKeyAndUserProfile_Id`
- `OnboardingServiceFeignClient` extended with `GET /onboarding-requests/latest?userId=`
- `OnboardingServiceClient.getLatestOnboardingForUser()` — returns `Optional`, maps `FeignException.NotFound` to `Optional.empty()`
- `OnboardingRequestRepository` extended with `findTopByUserProfileIdOrderByCreatedAtDesc`
- `OnboardingInitialisationService.findLatestForUser()` (`@Transactional(readOnly = true)`) and `GET /api/v1/internal/onboarding-requests/latest` in `OnboardingInternalController`

### EDP-150 · Correlation ID propagation and MDC request tracing

- `CorrelationIdFilter` in both user-service (`xyz.catuns.onboarding.user.filter`) and onboarding-service (`xyz.catuns.onboarding.service.filter`):
  - `OncePerRequestFilter` registered at `Ordered.HIGHEST_PRECEDENCE`
  - Reads `X-Correlation-ID` header; generates a UUID when absent
  - Stores in MDC under key `correlationId`; writes the value back in the `X-Correlation-ID` response header
  - Removes the MDC key in a `finally` block — no leakage between requests
- `logback-spring.xml` in both services — console pattern includes `[cid=%X{correlationId:-}]`; the `:-` default leaves the slot blank rather than `null` for unauthenticated health-check traffic
- End-to-end trace chain: inbound request → `CorrelationIdFilter` populates MDC → `FeignCorrelationInterceptor` forwards header → onboarding-service `CorrelationIdFilter` re-populates MDC → all log lines carry the same ID

---

## Test coverage

| Service | Story | Test type | Class | Tests |
|---|---|---|---|---|
| user-service | EDP-145 | Standalone MockMvc | `GlobalExceptionHandlerTest` | 5 |
| user-service | EDP-146 | `@SpringBootTest` + Testcontainers | `UserRegistrationServiceTest` | 6 |
| user-service | EDP-147 | Standalone MockMvc | `RegistrationControllerTest` | 5 |
| user-service | EDP-149 | Standalone MockMvc | `UserProfileControllerTest` | 5 |
| user-service | EDP-150 | Standalone MockMvc + filter | `CorrelationIdFilterTest` | 4 |
| onboarding-service | EDP-148 | `@SpringBootTest` + Testcontainers | `OnboardingInitialisationServiceTest` | 5 |

---

## Key design decisions

**Synchronous onboarding-service call is intentional and non-transactional.** `user-service` commits the `user_profile`, `external_identity`, and `OutboxEvent` rows first, then calls `onboarding-service`. If the downstream call fails, HTTP 503 is returned to the caller but the user record is preserved — re-registration will be blocked by `DuplicateRegistrationException` and a support path can recreate the onboarding request. The `UserRegisteredV1` outbox event (Phase 4) provides the eventual-consistency recovery path.

**`providerTargetId = null` on Phase 2 steps.** Provider-target resolution against `group_mapping_rule` is deferred to Phase 3. Steps are created with `null` targets so the onboarding request structure is immediately valid and the frontend can start polling, even though provisioning cannot proceed yet.

**`ErrorResponse` extends `ProblemDetail`.** Both services share the same extension pattern (`code` + `correlationId` added to Spring's RFC 7807 problem detail) so the error envelope shape is consistent across all endpoints without a custom serialiser.

**`JwtPrincipalExtractor` injects `AuthTokenProvider` directly.** `jwt-core` is a transitive dependency of `jwt-auth` but is not resolvable by name in the user-service compiler classpath. Injecting `AuthTokenProvider` (from `jwt-auth`, which is explicitly declared in the starter) and calling its `getClaims(token)` method achieves the same result through the concrete class.

**Atlassian identity `matchState` is derived, not stored.** Phase 2 has no identity-correlation step running yet. When `include=identities` is requested, the service checks for an Atlassian `ExternalIdentity` row and returns `matchState = MATCHED` or `PENDING` inline — no separate correlation state table is needed at this stage.

---

## Docs & schema references

- REST contract: `docs/rest-api-reference.md` — sections 1 (`POST /registrations`) and 2 (`GET /me`)
- Architecture constraints: `docs/design-spec.md` (outbox pattern, async provisioning, no OAuth in backend)
- Roadmap exit criteria: `docs/roadmap.md` — Phase 2

---

## How to verify locally

```bash
# Start Postgres + Kafka + Consul
docker compose -f infra/docker/docker-compose.yml up -d

# Run user-service tests
cd backend/user-service && ./mvnw test

# Run onboarding-service tests
cd backend/onboarding-service && ./mvnw test

# Boot both services
cd backend/user-service     && ./mvnw spring-boot:run
cd backend/onboarding-service && ./mvnw spring-boot:run

# Register a user (requires a valid JWT in Authorization header)
curl -s -X POST http://localhost:8080/api/v1/registrations \
  -H "Content-Type: application/json" \
  -d '{
    "githubUserId": "12345678",
    "githubLogin": "student-dev",
    "primaryEmail": "student@example.com",
    "displayName": "Student Dev",
    "roleKeys": ["STUDENT"]
  }' | jq .

# Fetch profile with onboarding summary
curl -s http://localhost:8080/api/v1/me?include=identities,onboarding \
  -H "Authorization: Bearer <token>" | jq .
```

Testcontainers spins up isolated Postgres and Kafka per test run — no local infrastructure required for the test suite.