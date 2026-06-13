## EDP-356 · Phase 6: Atlassian/Jira Provisioning

Replaces the Phase 4 provisioning stub with a real Atlassian Cloud group-membership adapter in `provisioning-service`, mirroring the GitHub adapter from Phase 5. A `RestClient`-based adapter resolves an Atlassian `accountId` (by pre-linked identity or by email search) and adds the user to the configured Jira group, enriches `provisioning_audit_log` with the real response, and publishes `AtlassianProvisioningCompletedV1` via the outbox. Separately, this phase closes the long-standing gap where `IdentityCorrelationRequestedV1` was published by `onboarding-service` but never consumed: a new internal user-service API (`/api/v1/internal/external-identities`, `/api/v1/internal/identity-links`) lets `onboarding-service` look up the matching GitHub/Atlassian identities by email, persist an `identity_link` row, and emit `IdentityCorrelationCompletedV1`/`IdentityCorrelationFailedV1`, unblocking the `IDENTITY_CORRELATION` onboarding step. `GET /api/v1/admin/provider-health` is extended with an Atlassian reachability check.

---

## Commits

| Commit    | Story   | Summary                                                                                |
|-----------|---------|------------------------------------------------------------------------------------------|
| `1654de9` | EDP-357 | Configure Atlassian Cloud REST API client with Basic Auth                              |
| `de9f873` | EDP-358 | Implement Atlassian group membership adapter                                            |
| `7b5a9a7` | EDP-359 | Wire Atlassian adapter into provisioning audit log and outbox                           |
| `f547c29` | EDP-360 | User-service internal API for Atlassian identity lookup and identity-link persistence  |
| `a678de3` | EDP-362 | Atlassian provider health endpoint                                                      |
| `afb2c78` | EDP-361 | Onboarding-service identity correlation consumer and Atlassian provisioning enrichment  |
| `688b98c` | EDP-363 | Unit and integration tests for Atlassian provisioning and identity correlation         |

---

## What's in this PR

### EDP-357 · Configure Atlassian Cloud REST API client with Basic Auth

**`AtlassianClientProperties`** — new `@ConfigurationProperties(prefix = "atlassian")` class

- Nested `AtlassianApiProperties` inner class binds `atlassian.api.base-url`, `atlassian.api.email`, and `atlassian.api.token`
- All three fields `@NotBlank` with `@Validated` on the outer class — startup fails if any value is absent or blank
- Bound via `@EnableConfigurationProperties` alongside `AppProperties` and `GithubClientProperties` in `AppConfig`

**`atlassianRestClient` bean** — new `RestClient` bean in `AppConfig`

- `RestClient.builder().baseUrl(properties.getApi().getBaseUrl())`
- Computes `Authorization: Basic base64(email:token)` once at construction time (Atlassian Cloud's Basic Auth scheme) and sets it as a default header — no per-request auth wiring required
- Default header `Accept: application/json`

**`application.yaml` / `.env.example`**

- `atlassian.api.base-url: ${ATLASSIAN_API_BASE_URL}`, `atlassian.api.email: ${ATLASSIAN_API_EMAIL}`, `atlassian.api.token: ${ATLASSIAN_API_TOKEN}` added alongside the existing `github:` block
- `ATLASSIAN_API_BASE_URL`, `ATLASSIAN_API_EMAIL`, `ATLASSIAN_API_TOKEN` added to `backend/provisioning-service/.env.example`

---

### EDP-358 · Implement Atlassian group membership adapter

**`AtlassianGroupMembershipResult`** — Java record

```
String membershipState   // "ACTIVE" | "FAILED" (Atlassian has no "pending" concept)
boolean success
String errorCode         // null on success; "ACCOUNT_NOT_FOUND" | "GROUP_OR_USER_NOT_FOUND" | "{statusCode}" | "NETWORK_ERROR" on failure
String errorMessage      // null on success; exception message on failure
String accountId         // resolved or supplied Atlassian accountId; null if unresolved
```

**`AtlassianRateLimitException`** — `RuntimeException` carrying `retryAfterSeconds` from the `Retry-After` response header, thrown so the Spring Kafka error handler can reprocess the event after the delay

**`AtlassianProvisioningAdapter.addGroupMember(atlassianIdentityId, atlassianEmail, groupName)`**

1. Resolves an `accountId`: uses `atlassianIdentityId` if present and non-blank; otherwise calls `GET /rest/api/3/user/search?query={email}` and takes the first match's `accountId`
2. If no account can be resolved, returns `FAILED`/`ACCOUNT_NOT_FOUND` without calling the group endpoint
3. Otherwise calls `POST /rest/api/3/group/user?groupName={groupName}` with body `{"accountId": accountId}`

| Atlassian response                          | Outcome                                                                     |
|----------------------------------------------|------------------------------------------------------------------------------|
| 2xx (e.g. 201 Created)                       | `AtlassianGroupMembershipResult("ACTIVE", true, null, null, accountId)`      |
| 400 with body containing "already a member"  | `AtlassianGroupMembershipResult("ACTIVE", true, null, null, accountId)`      |
| 404                                           | `AtlassianGroupMembershipResult("FAILED", false, "GROUP_OR_USER_NOT_FOUND", …)` |
| 429 + `Retry-After`                          | throws `AtlassianRateLimitException(retryAfterSeconds)`                     |
| other 4xx / 5xx                              | `AtlassianGroupMembershipResult("FAILED", false, "{statusCode}", …)`         |
| network error                                | `AtlassianGroupMembershipResult("FAILED", false, "NETWORK_ERROR", …)`        |
| no `accountId` resolved                      | `AtlassianGroupMembershipResult("FAILED", false, "ACCOUNT_NOT_FOUND", null, null)` |

---

### EDP-359 · Wire Atlassian adapter into provisioning audit log and outbox

**`ProvisioningEventService.handleAtlassianProvisioningRequested`** — replaces the Phase 4 stub (which hardcoded `ResultState.SUCCESS` and always published `success=true`)

- Calls `atlassianAdapter.addGroupMember(event.getAtlassianIdentityId(), event.getAtlassianEmail(), event.getGroupName())`
- Persists `ProvisioningAuditLog` with `responsePayload = JSON(AtlassianGroupMembershipResult)` (includes `membershipState` and resolved `accountId`) and `resultState` mapped via the existing `toResultState` helper shared with the GitHub path (`ACTIVE` → `SUCCESS`, `PENDING` → `PENDING`, anything else → `FAILURE`)
- Builds `AtlassianProvisioningCompletedV1` from the adapter result via `buildAtlassianCompletedEvent`, serialised through the existing generic `toOutboxPayload(SpecificRecord)` reflection helper (also shared with GitHub), and persists the `OutboxEvent` in the same `@Transactional` block as the audit log

Unlike `GithubProvisioningCompletedV1`, `AtlassianProvisioningCompletedV1` has **no `membershipState` field** — Atlassian group membership is binary, so the Avro contract only carries `success`/`errorCode`/`errorMessage`. The richer `membershipState`/`accountId` detail lives in `provisioning_audit_log.response_payload` for operator visibility.

---

### EDP-360 · User-service internal API for Atlassian identity lookup and identity-link persistence

**`InternalIdentityController`** — new `@RestController` at `/api/v1/internal` (not exposed via the gateway)

- `GET /api/v1/internal/external-identities?provider=ATLASSIAN&email=...` → looks up `external_identity` by provider + email, returns `200 AtlassianIdentitySummary` (`accountId`, `email`, `matchState="MATCHED"`) or `404`
- `POST /api/v1/internal/identity-links` → `201 IdentityLinkResponse`, delegates to `IdentityLinkService`

**`ExternalIdentityRepository.findByProvider_ProviderKeyAndEmail`** — new derived query method

**`IdentityLinkService.createIdentityLink`**

- Looks up `UserProfile`, the GitHub `ExternalIdentity`, and the Atlassian `ExternalIdentity` by id — throws `NoSuchElementException` if any is missing
- Persists a new `IdentityLink` row (`matchStrategy`, `confidenceScore`)
- Maps the saved entity to `IdentityLinkResponse`

**`InternalApiAuthFilter`** — new `OncePerRequestFilter` at `@Order(HIGHEST_PRECEDENCE + 5)`

- `shouldNotFilter` skips any request whose URI doesn't start with `/api/v1/internal`
- Validates `Authorization: Bearer {token}` via `ServiceTokenProvider`
- Returns `401 application/problem+json` if the header is missing/malformed, the token is invalid, the caller isn't `onboarding-service`, or the audience isn't `user-service`

**DTOs** — `IdentityLinkCreateRequest` (`userProfileId`, `githubIdentityId`, `atlassianIdentityId`, `matchStrategy` all `@NotNull`; `confidenceScore` optional), `IdentityLinkResponse` (adds `id`/`createdAt`)

---

### EDP-362 · Atlassian provider health endpoint

**`AtlassianHealthChecker.check()`**

- Calls `GET /rest/api/3/myself` via `atlassianRestClient` and measures wall-clock latency
- Returns `ProviderHealthResponse("ATLASSIAN", "UP", latencyMs, checkedAt)` on success
- Catches all exceptions, logs a warning, and returns `ProviderHealthResponse("ATLASSIAN", "DOWN", latencyMs, checkedAt)` — never throws

**`ProviderHealthController`**

- `GET /api/v1/admin/provider-health` now returns `[githubHealthChecker.check(), atlassianHealthChecker.check()]`

---

### EDP-361 · Onboarding-service identity correlation consumer and Atlassian provisioning enrichment

**`pom.xml` / `OnboardingServiceApplication`** — adds `spring-cloud-starter-openfeign` and `@EnableFeignClients`

**`UserServiceFeignClient`** — new `@FeignClient(name = "${app.feign.clients.user}", path = "/api/v1/internal", configuration = UserFeignConfig.class)`

- `getAtlassianIdentityByEmail(email)` → `GET /external-identities?provider=ATLASSIAN&email=...`
- `getGithubIdentityByUserProfileId(userProfileId)` → `GET /external-identities?provider=GITHUB&userProfileId=...`
- `createIdentityLink(request)` → `POST /identity-links`

**`FeignServiceAuthInterceptor`** — generates a short-lived service JWT via `ServiceTokenProvider` for `ServicePrincipal("onboarding-service", "user-service")` and attaches it as `Authorization: Bearer`. `FeignCorrelationInterceptor` forwards `X-Correlation-ID` from MDC. Both registered via `UserFeignConfig`, wired only into `UserServiceFeignClient`.

**`IdentityCorrelationService.handleIdentityCorrelationRequested`** — new, consumes `IdentityCorrelationRequestedV1`

- If `event.getPrimaryEmail()` is `null` → writes `IdentityCorrelationFailedV1` to the outbox with `reasonCode="NO_MATCHING_ATLASSIAN_IDENTITY"`
- Calls `getAtlassianIdentityByEmail(primaryEmail)`; a `404` (`FeignException.NotFound`) is mapped to the same failed-outbox path
- Otherwise: looks up the GitHub identity via `getGithubIdentityByUserProfileId(userId)`, calls `createIdentityLink` with `matchStrategy=EMAIL_EXACT`, `confidenceScore=1.00`, then writes `IdentityCorrelationCompletedV1` (`matched=true`) to the outbox

**`ProvisioningCompletedConsumer.consumeIdentityCorrelation`** — `IdentityCorrelationRequestedV1` was previously matched by no branch and silently dropped; now dispatched to `IdentityCorrelationService.handleIdentityCorrelationRequested`

**`OutboxPayloadBuilderService`** — new `buildIdentityCorrelationCompleted` and `buildIdentityCorrelationFailed` payload builders, following the existing `base(...)` envelope pattern

**`OnboardingEventService.buildAtlassianOutbox`** — `atlassianEmail` is now populated from `event.getPrimaryEmail()` instead of hardcoded `null`, so the adapter's email-resolution fallback (EDP-358) is reachable for first-time provisioning before any `identity_link` exists

**`application.yaml`** — `app.feign.clients.user: user-service` registers the Feign client target (resolved via Consul discovery)

**user-service** — `InternalIdentityController.getExternalIdentity` extended to also accept `userProfileId` (used for the GitHub-identity lookup above); `AtlassianIdentitySummary` renamed to `ExternalIdentityLookupResponse` and gains an `id` (`UUID`) field, shared verbatim as a client-side DTO in `onboarding-service`

---

### EDP-363 · Unit and integration tests for Atlassian provisioning and identity correlation

**`AtlassianProvisioningIntegrationTest`** — full consumer-to-outbox flow

- `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` starts real Postgres and Confluent Kafka containers
- `@RegisterExtension WireMockExtension` on a dynamic port; `@DynamicPropertySource` wires `atlassian.api.base-url` to the WireMock base URL
- `@BeforeEach` stubs `GET /rest/api/3/user/search` → one matching account, `POST /rest/api/3/group/user` → `201`; clears both repos before each test
- `@MockBean OutboxEventPublisher` avoids a `KafkaAvroSerializer` round-trip to Schema Registry from the background scheduler
- Awaitility polls up to 30 s for one `provisioning_audit_log` row (`actionName=ATLASSIAN_PROVISION`, `resultState=SUCCESS`, `responsePayload` contains `"membershipState":"ACTIVE"`) and one `outbox_event` row (`eventType=AtlassianProvisioningCompletedV1`, `topic=edu.provisioning.atlassian.v1`, payload contains `"success":true`)

**`application-test.yaml`** — new `atlassian:` block (`base-url: http://localhost`, dummy `email`/`token`) mirroring the existing `github:` block, required for `AtlassianClientProperties` `@NotBlank` validation to pass when the Spring context starts under the `test` profile

---

## Test coverage

| Service               | Story            | Test type                                      | Class                              | What it covers                                                                                          |
|------------------------|------------------|-------------------------------------------------|--------------------------------------|------------------------------------------------------------------------------------------------------|
| provisioning-service   | EDP-358          | Unit — Mockito                                 | `AtlassianProvisioningAdapterTest`  | account resolution by email, ACTIVE on create, already-a-member (400), 404, 429+Retry-After, ACCOUNT_NOT_FOUND |
| provisioning-service   | EDP-359          | `@SpringBootTest` + Testcontainers             | `ProvisioningEventServiceTest`      | audit log `responsePayload`/`resultState` and outbox `success`/`errorCode` for ACTIVE and FAILED results |
| provisioning-service   | EDP-362          | Unit — Mockito                                 | `AtlassianHealthCheckerTest`        | UP on 2xx, DOWN on exception, non-negative latency                                                      |
| provisioning-service   | EDP-363          | `@SpringBootTest` + Testcontainers + WireMock  | `AtlassianProvisioningIntegrationTest` | full Kafka consumer → Atlassian stub → audit log + outbox persistence                                |
| user-service           | EDP-360          | Standalone MockMvc                             | `InternalIdentityControllerTest`    | 200 by email, 404 on no match, 200 by `userProfileId`, 201 on identity-link create, 400 on missing fields |
| user-service           | EDP-360          | Standalone MockMvc                             | `InternalApiAuthFilterTest`         | 401 missing/malformed/invalid token, 401 wrong caller/audience, 200 on valid service token, non-internal paths bypass |
| user-service           | EDP-360          | Unit — Mockito                                 | `IdentityLinkServiceTest`           | persists identity link; throws `NoSuchElementException` for unknown profile/identity                  |
| user-service           | EDP-360          | `@DataJpaTest`                                 | `ExternalIdentityRepositoryTest`    | `findByProvider_ProviderKeyAndEmail` match and no-match                                                |
| onboarding-service     | EDP-361, EDP-363 | Unit — Mockito                                 | `IdentityCorrelationServiceTest`    | match found → identity link + `IdentityCorrelationCompletedV1`; no Atlassian match / no primary email → `IdentityCorrelationFailedV1` |
| onboarding-service     | EDP-361          | `@SpringBootTest` + Testcontainers             | `OnboardingEventServiceTest`        | `AtlassianProvisioningRequestedV1` outbox payload carries `atlassianEmail` from `primaryEmail`         |

---

## Key design decisions

**Basic Auth via a pre-computed default header, mirroring GitHub's Bearer token approach.** `atlassianRestClient` computes `Base64(email:token)` once at bean construction and sets it as a default `Authorization` header — no per-request auth wiring, consistent with `githubRestClient`'s `Authorization: Bearer` header from Phase 5.

**Email-based account resolution is a fallback, not the primary path.** `AtlassianProvisioningAdapter.addGroupMember` prefers a pre-resolved `atlassianIdentityId` and only calls `GET /rest/api/3/user/search?query={email}` when it's absent. This lets the same adapter serve first-time provisioning (email-only, before any `identity_link` exists) and future re-provisioning once identity correlation has run.

**HTTP 400 "already a member" is treated as success, not failure.** Atlassian's group-membership API returns 400 with an `errorMessages` array containing "already a member" if the account is already in the group. The adapter detects this and returns `ACTIVE`/`success=true`, making retries idempotent.

**`AtlassianProvisioningCompletedV1` intentionally has no `membershipState` field.** Atlassian group membership is binary — there's no "pending invitation" state like GitHub's. The Avro contract only carries `success`/`errorCode`/`errorMessage`; the richer `membershipState`/`accountId` detail is captured in `provisioning_audit_log.response_payload` for operators, without requiring a schema change.

**`AtlassianRateLimitException` reuses the GitHub retry path.** Thrown out of `handleAtlassianProvisioningRequested`, it's caught by the Spring Kafka `ExponentialBackOff` error handler configured in Phase 4; exhausted retries route to `edu.provisioning.atlassian.v1.DLT`.

**Identity correlation is a Feign-based internal API, not a shared database.** `onboarding-service` never queries `user-service`'s tables directly — `UserServiceFeignClient` calls `/api/v1/internal/external-identities` and `/api/v1/internal/identity-links`, guarded by `InternalApiAuthFilter` using the same `ServiceTokenProvider`/`ServicePrincipal` service-to-service JWT mechanism used elsewhere (distinct from user-facing JWTs).

**`IdentityCorrelationRequestedV1` was already being published in Phase 4 but had no consumer — this PR closes that gap.** Previously the `IDENTITY_CORRELATION` step would sit in `PROCESSING` indefinitely. `ProvisioningCompletedConsumer` now dispatches the event to `IdentityCorrelationService`, which always terminates the step by writing either `IdentityCorrelationCompletedV1` or `IdentityCorrelationFailedV1` to the outbox.

**A failed identity correlation transitions the step to `FAILED` and is covered by the existing generic retry mechanism.** `handleIdentityCorrelationFailed` (Phase 4) calls `domainService.transitionStep(step, OnboardingStepState.FAILED)`, so `OnboardingRetryService`'s generic FAILED/MANUAL_REVIEW → PENDING retry applies symmetrically to identity correlation — no Atlassian-specific retry logic was needed.

**`atlassianEmail` enrichment makes the adapter's email-fallback path reachable.** Before this PR, `OnboardingEventService` always passed `null` for `atlassianEmail` when building `AtlassianProvisioningRequestedV1`, so `AtlassianProvisioningAdapter`'s email-resolution branch (EDP-358) was unreachable. `event.getPrimaryEmail()` (captured from the GitHub OAuth profile at registration) is now passed through.

---

## Docs & schema references

- Avro schemas (`AtlassianProvisioningRequestedV1`, `AtlassianProvisioningCompletedV1`, `IdentityCorrelationRequestedV1`, `IdentityCorrelationCompletedV1`, `IdentityCorrelationFailedV1`): `docs/schemas/kafka-event-schema.md`
- REST endpoint shape for `GET /api/v1/admin/provider-health`: `docs/schemas/rest-api-reference.md`
- `provisioning_audit_log` schema and `ResultState` enum, `identity_link` table: `docs/schemas/normalized-database-schema.md`
- Async provisioning constraint and outbox pattern: `docs/design-spec.md`
- Roadmap exit criteria: `docs/roadmap.md` — Phase 6
- Backlog: `docs/backlog/phase-6-atlassian-jira-provisioning.json`

> Note: `docs/schemas/kafka-event-schema.md`'s producer/consumer map (line ~364) still lists a future `identity-service` as the consumer of `IdentityCorrelationRequestedV1` and producer of `IdentityCorrelationCompletedV1`/`IdentityCorrelationFailedV1`. This PR implements that role inside `onboarding-service` instead (it both produces and consumes `IdentityCorrelationRequestedV1`, and produces/consumes the completion events). The table may need a follow-up update to reflect the as-built architecture.

---

## How to verify locally

```bash
# Start local infrastructure
docker compose -f infra/docker/docker-compose.yml up -d

# Set required env vars
export GITHUB_API_TOKEN=<your-github-pat>
export GITHUB_ORG=<your-org>
export ATLASSIAN_API_BASE_URL=https://<your-site>.atlassian.net
export ATLASSIAN_API_EMAIL=<your-atlassian-account-email>
export ATLASSIAN_API_TOKEN=<your-atlassian-api-token>

# Run provisioning-service unit and integration tests
cd backend/provisioning-service && ./mvnw test -Dtest="AtlassianProvisioningAdapterTest"
cd backend/provisioning-service && ./mvnw test -Dtest="AtlassianHealthCheckerTest"
cd backend/provisioning-service && ./mvnw test -Dtest="AtlassianProvisioningIntegrationTest"

# Run user-service identity-link tests
cd backend/user-service && ./mvnw test -Dtest="InternalIdentityControllerTest,InternalApiAuthFilterTest,IdentityLinkServiceTest"

# Run onboarding-service identity correlation tests
cd backend/onboarding-service && ./mvnw test -Dtest="IdentityCorrelationServiceTest,OnboardingEventServiceTest"

# Boot all services
cd backend/provisioning-service  && ./mvnw spring-boot:run &
cd backend/onboarding-service    && ./mvnw spring-boot:run &
cd backend/user-service          && ./mvnw spring-boot:run &

# Check provider health (requires a valid admin JWT)
curl -s -H "Authorization: Bearer <admin-jwt>" \
  http://localhost:8083/api/v1/admin/provider-health | jq .

# Watch the Atlassian provisioning and identity correlation topics
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic edu.provisioning.atlassian.v1 \
  --from-beginning

docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic edu.identity.correlation.v1 \
  --from-beginning

# Verify audit log and outbox rows after a registration
psql -h localhost -U default -d provisioning_service \
  -c "SELECT action_name, result_state, response_payload FROM provisioning_audit_log ORDER BY created_at DESC LIMIT 5;"

psql -h localhost -U default -d provisioning_service \
  -c "SELECT event_type, topic, published, payload FROM outbox_event ORDER BY created_at DESC LIMIT 5;"

# Verify identity_link rows after correlation
psql -h localhost -U default -d user_service \
  -c "SELECT user_profile_id, github_identity_id, atlassian_identity_id, match_strategy, confidence_score FROM identity_link ORDER BY created_at DESC LIMIT 5;"
```