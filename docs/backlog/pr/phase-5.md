## EDP-314 · Phase 5: GitHub Provisioning

Replaces the Phase 4 provisioning stub with a real GitHub team membership adapter in `provisioning-service`. A `RestClient`-based GitHub adapter calls the GitHub memberships API, handles the `active`/`pending`/`failed` lifecycle, enriches `provisioning_audit_log` with real response data, and publishes `GithubProvisioningCompletedV1` via the outbox. `onboarding-service` consumes the completion event and updates `onboarding_step` state, driving the parent `onboarding_request` through the full state machine including the `ACTION_REQUIRED` (pending invitation) branch. A `GET /api/v1/admin/provider-health` endpoint exposes real-time GitHub API reachability with admin-only access enforcement.

---

## Commits

| Commit    | Story   | Summary                                                                          |
|-----------|---------|----------------------------------------------------------------------------------|
| `4c8bdd0` | —       | Create phase 5 epic backlog JSON                                                 |
| `0a75f39` | EDP-315 | Configure GitHub REST API client with token authentication                       |
| `88511b4` | EDP-316 | Implement GitHub team membership adapter                                         |
| `5432948` | EDP-316 | Wire adapter into ProvisioningEventService and add audit log / outbox enrichment |
| `316a110` | EDP-319 | Onboarding-service: update step state on GithubProvisioningCompletedV1           |
| `9588571` | EDP-320 | Provider health endpoint for GitHub                                              |
| `f040aeb` | —       | Build: migrate artifact resolution from repolite to Maven Central                |
| `8f224cf` | EDP-321 | Unit and integration tests for GitHub provisioning adapter                       |

---

## What's in this PR

### EDP-315 · Configure GitHub REST API client with token authentication

**`GithubClientProperties`** — new `@ConfigurationProperties(prefix = "github")` class

- Nested `GithubApiProperties` inner class binds `github.api.base-url`, `github.api.token`, and `github.api.org`
- All three fields annotated `@NotBlank` with `@Validated` on the outer class — startup fails with `ConfigurationPropertiesBindException` if any value is absent or blank
- Bound via `@EnableConfigurationProperties` in `AppConfig`

**`githubRestClient` bean** — new `RestClient` bean in `AppConfig`

- `RestClient.builder()` with `.baseUrl(properties.getApi().getBaseUrl())`
- Default header `Authorization: Bearer {token}` set at construction time — no per-request auth wiring required
- Default header `Accept: application/json`

**`application.yaml`**

- `github.api.base-url: https://api.github.com`, `github.api.token: ${GITHUB_API_TOKEN}`, `github.api.org: ${GITHUB_ORG}` added
- `GITHUB_API_TOKEN` and `GITHUB_ORG` added to `infra/docker/.env.example`

---

### EDP-316 · Implement GitHub team membership adapter

**`GithubMembershipResult`** — Java record

```
String membershipState   // "ACTIVE" | "PENDING" | "FAILED"
boolean success
String errorCode         // null on success; "USER_OR_TEAM_NOT_FOUND" or HTTP status code on failure
String errorMessage      // null on success; exception message on failure
```

**`GithubRateLimitException`** — `RuntimeException` carrying `retryAfterSeconds` from the `Retry-After` response header; thrown so the Spring Kafka error handler can reprocess the event after the delay

**`GithubProvisioningAdapter.addTeamMember(githubLogin, org, teamSlug)`**

Calls `PUT /orgs/{org}/teams/{slug}/memberships/{login}` via `githubRestClient`:

| GitHub response           | Outcome                                                                |
|---------------------------|------------------------------------------------------------------------|
| 200 `{"state":"active"}`  | `GithubMembershipResult("ACTIVE", true, null, null)`                   |
| 200 `{"state":"pending"}` | `GithubMembershipResult("PENDING", true, null, null)`                  |
| 404                       | `GithubMembershipResult("FAILED", false, "USER_OR_TEAM_NOT_FOUND", …)` |
| 403 + `Retry-After`       | throws `GithubRateLimitException(retryAfterSeconds)`                   |
| other 4xx / 5xx           | `GithubMembershipResult("FAILED", false, "{statusCode}", …)`           |
| network error             | `GithubMembershipResult("FAILED", false, "NETWORK_ERROR", …)`          |

**`ProvisioningEventService.handleGithubProvisioningRequested`** — replaces Phase 4 stub

- Calls `githubAdapter.addTeamMember(event.getGithubLogin(), event.getGithubOrg(), event.getGithubTeamSlug())`
- Persists `ProvisioningAuditLog` with `responsePayload = JSON(GithubMembershipResult)` and `resultState` mapped from membership state (`ACTIVE` → `SUCCESS`, `PENDING` → `PENDING`, `FAILED` → `FAILURE`)
- Builds `GithubProvisioningCompletedV1` Avro record from adapter result; serialises field map to JSON string; persists `OutboxEvent` in same `@Transactional` block
- `ResultState.PENDING` added to the enum

---

### EDP-319 · Onboarding-service: update step state on GithubProvisioningCompletedV1

**`OnboardingEventService.handleGithubProvisioningCompleted`**

Maps `GithubProvisioningCompletedV1.membershipState` to `OnboardingStepState`:

| membershipState | OnboardingStepState           |
|-----------------|-------------------------------|
| `ACTIVE`        | `SUCCEEDED`                   |
| `PENDING`       | `PENDING_EXTERNAL_ACCEPTANCE` |
| `FAILED`        | `FAILED`                      |

After persisting the step state calls `emitLifecycleEventIfTerminal`, which re-evaluates the parent `OnboardingRequest` state via `OnboardingRequestStateResolver`.

**`OnboardingRequestStateResolver`** — new `@Component`

Derives `OnboardingRequestState` from the current set of step states:

| Step state set                                                               | Request state     |
|------------------------------------------------------------------------------|-------------------|
| Any step in `PENDING`, `PROCESSING`, or `MANUAL_REVIEW`                      | `IN_PROGRESS`     |
| Any step in `PENDING_EXTERNAL_ACCEPTANCE` (and no actively-processing steps) | `ACTION_REQUIRED` |
| All terminal, mix of `SUCCEEDED` and `FAILED`                                | `PARTIAL_SUCCESS` |
| All terminal, all `SUCCEEDED`                                                | `COMPLETED`       |
| All terminal, all `FAILED`                                                   | `FAILED`          |

**`RequestStateTransitions`** — new `final` class with an `EnumMap<OnboardingRequestState, Set<OnboardingRequestState>>` of allowed transitions. Guards `domainService.transitionRequest` against illegal moves (e.g. `COMPLETED → IN_PROGRESS`). Terminal states (`COMPLETED`, `PARTIAL_SUCCESS`, `FAILED`) have empty allowed sets.

**`OnboardingRequestState`** — `ACTION_REQUIRED` and `PARTIAL_SUCCESS` added to the enum.

---

### EDP-320 · Provider health endpoint for GitHub

**`ProviderHealthResponse`** — Java record: `String provider`, `String status` (`UP`/`DOWN`), `long latencyMs`, `String checkedAt` (ISO-8601)

**`GithubHealthChecker.check()`**

- Calls `GET /rate_limit` via `githubRestClient` and measures wall-clock latency with `System.currentTimeMillis()`
- Returns `ProviderHealthResponse("GITHUB", "UP", latencyMs, checkedAt)` on any 2xx
- Catches all exceptions, logs a warning, and returns `ProviderHealthResponse("GITHUB", "DOWN", latencyMs, checkedAt)` — the endpoint itself never throws

**`ProviderHealthController`**

- `GET /api/v1/admin/provider-health` returns `200 List<ProviderHealthResponse>`
- Mounted under `@RequestMapping("/api/v1/admin")` which is covered by `AdminApiAuthFilter`

**`AdminApiAuthFilter`** — `OncePerRequestFilter` at `HIGHEST_PRECEDENCE + 5`

- Skips any request whose URI does not begin with `/api/v1/admin`
- Extracts `Authorization: Bearer {token}`, validates it via `PayloadTokenProvider`
- Returns `401 application/problem+json` on missing/malformed header or invalid token
- Returns `403 application/problem+json` if `payload.isAdmin()` is false
- Wires into `PayloadTokenProvider` and `ObjectMapper` from `common`

**`JwtConfig`** — new `@Configuration` in provisioning-service registering the `PayloadTokenProvider` bean using `jwt.secret` from properties

---

### EDP-321 · Unit and integration tests for GitHub provisioning adapter

**`pom.xml`** — `org.wiremock:wiremock` replaced with `org.wiremock:wiremock-jetty12:3.9.0` at test scope; `wiremock-jetty12` bundles the Jetty 12 `HttpServerFactory` required by Spring Boot 3.x

**`GithubProvisioningAdapterTest`** — pure unit test, no Spring context

- Mocks full `RestClient` chain (`put() → uri() → retrieve() → body()`) using `mock(RestClient.class)`
- `uri()` stub changed from `(Object[]) any()` to `any(), any(), any()` to correctly match the three-arg varargs call; `@MockitoSettings(LENIENT)` suppresses strict-stub warnings from the shared `@BeforeEach` setup
- Four tests covering all adapter paths: `active` state, `pending` state, 404 not found, and 403 rate-limit with `Retry-After: 30`

**`GithubProvisioningIntegrationTest`** — full consumer-to-outbox flow

- `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` starts real Postgres and Confluent Kafka containers
- `@RegisterExtension WireMockExtension` starts WireMock on a dynamic port; `@DynamicPropertySource` wires `github.api.base-url` to the WireMock base URL
- `@BeforeEach` stubs `PUT /orgs/.*/teams/.*/memberships/.*` → `{"state":"active"}`; clears both repos before each test
- `@MockBean OutboxEventPublisher` prevents the background scheduler from attempting a `KafkaAvroSerializer` round-trip to Schema Registry during the test
- Schema Registry: Confluent's `mock://test` in-memory registry is configured for both the test-side `KafkaTemplate` producer and the application-side `KafkaAvroDeserializer` consumer — full Avro serialisation path exercised without a real registry container
- Awaitility polls up to 30 s for one `provisioning_audit_log` row and one `outbox_event` row; asserts `actionName = GITHUB_PROVISION`, `resultState = SUCCESS`, `responsePayload` contains `ACTIVE`; asserts outbox `eventType = GithubProvisioningCompletedV1`, `topic = edu.provisioning.github.v1`, payload contains `"membershipState":"ACTIVE"` and `"success":true`

**`application-test.yaml`** — new `src/test/resources/application-test.yaml` activated by `@ActiveProfiles("test")` on all `@SpringBootTest` classes; centralises GitHub dummy credentials, JWT secret, and `spring.kafka.properties.schema-registry-url: mock://test`; replaces per-class `@TestPropertySource` annotations

**`application.yaml`** — `@...@` Maven filter tokens in the `app.open-api` block single-quoted (`'@project.description@'`) so SnakeYAML can parse the file in environments where Maven resource filtering is not active

---

## Test coverage

| Service              | Story   | Test type                                     | Class                               | What it covers                                                                              |
|----------------------|---------|-----------------------------------------------|-------------------------------------|---------------------------------------------------------------------------------------------|
| provisioning-service | EDP-316 | Unit — Mockito                                | `GithubProvisioningAdapterTest`     | ACTIVE, PENDING, 404, 403+Retry-After adapter paths                                         |
| provisioning-service | EDP-316 | `@DataJpaTest` + Testcontainers               | `ProvisioningEventServiceTest`      | audit log + outbox fields, real ResultState, Avro payload JSON                              |
| provisioning-service | EDP-320 | `@WebMvcTest`                                 | `AdminApiAuthFilterTest`            | 401 on missing header, 401 on invalid token, 403 on non-admin, 200 on valid admin token     |
| provisioning-service | EDP-320 | Unit — Mockito                                | `GithubHealthCheckerTest`           | UP on 2xx, DOWN on exception, latencyMs non-negative                                        |
| provisioning-service | EDP-321 | `@SpringBootTest` + Testcontainers + WireMock | `GithubProvisioningIntegrationTest` | full Kafka consumer → GitHub stub → audit log + outbox persistence                          |
| onboarding-service   | EDP-319 | `@SpringBootTest` + Testcontainers            | `OnboardingEventServiceTest`        | ACTIVE→SUCCEEDED, PENDING→PENDING_EXTERNAL_ACCEPTANCE, FAILED→FAILED; request state machine |

---

## Key design decisions

**`RestClient` for GitHub, `OpenFeign` for inter-service calls.** `RestClient` is used for the external GitHub API because it is a third-party HTTP endpoint — Feign is reserved for internal service-to-service calls where `FeignCorrelationInterceptor` forwards `X-Correlation-ID`. This matches the constraint in `CLAUDE.md`.

**`PENDING` membership is a success, not a failure.** When GitHub responds with `state: pending`, the invitation was successfully dispatched — the user simply has not yet accepted it. The adapter sets `success = true` for both `ACTIVE` and `PENDING` results. The distinction is surfaced through `membershipState` so onboarding-service can set the correct step state (`PENDING_EXTERNAL_ACCEPTANCE`) and request state (`ACTION_REQUIRED`).

**`GithubRateLimitException` is unchecked and propagates out of the Kafka listener.** Throwing out of `handleGithubProvisioningRequested` causes the Spring Kafka error handler to catch it and apply the `ExponentialBackOff` retry policy (configured in Phase 4). If retries are exhausted, the event is routed to `edu.provisioning.github.v1.DLT`. The `Retry-After` seconds are logged so an operator knows the required wait time.

**`OnboardingRequestStateResolver` is a pure function over step states.** It holds no mutable state and takes only the current list of `OnboardingStep` objects, making it independently testable and safe to call multiple times within the same transaction. `RequestStateTransitions` enforces the allowed state machine edges separately, keeping validation logic out of the resolver.

**`AdminApiAuthFilter` is a servlet filter, not Spring Security.** The provisioning-service does not use Spring Security — it uses the same JWT validation mechanism (`PayloadTokenProvider` from `common`) that other services use for token parsing. Placing the admin check in a `OncePerRequestFilter` at high precedence means it runs before any other filter or controller and short-circuits with `application/problem+json` error responses consistent with the rest of the API.

**Outbox and audit log written in the same transaction.** `handleGithubProvisioningRequested` is `@Transactional` — `ProvisioningAuditLog` and `OutboxEvent` are both persisted in the same DB transaction. If the outbox write fails after the audit log write succeeds, both roll back. This upholds the outbox pattern constraint from `docs/design-spec.md`.

**WireMock with Jetty 12 for integration tests.** The `org.wiremock:wiremock` artifact embeds Jetty 11 which conflicts with Spring Boot 3.x's Jetty 12 runtime. `wiremock-jetty12:3.9.0` provides the `HttpServerFactory` SPI implementation for Jetty 12 and is a drop-in replacement at test scope.

---

## Docs & schema references

- REST endpoint shape for `GET /api/v1/admin/provider-health`: `docs/rest-api-reference.md`
- Avro schemas (`GithubProvisioningRequestedV1`, `GithubProvisioningCompletedV1`): `docs/schemas/kafka-event-schema.md`
- `provisioning_audit_log` schema and `ResultState` enum: `docs/schemas/normalized-database-schema.md`
- Async provisioning constraint and outbox pattern: `docs/design-spec.md`
- Roadmap exit criteria: `docs/roadmap.md` — Phase 5
- Backlog: `docs/backlog/phase-5-github-provisioning.json`

---

## How to verify locally

```bash
# Start local infrastructure
docker compose -f infra/docker/docker-compose.yml up -d

# Set required env vars
export GITHUB_API_TOKEN=<your-github-pat>
export GITHUB_ORG=<your-org>

# Run provisioning-service unit and integration tests
cd backend/provisioning-service && ./mvnw test -Dtest="GithubProvisioningAdapterTest"
cd backend/provisioning-service && ./mvnw test -Dtest="GithubProvisioningIntegrationTest"

# Run onboarding-service step state tests
cd backend/onboarding-service && ./mvnw test -Dtest="OnboardingEventServiceTest"

# Boot all services
cd backend/provisioning-service  && ./mvnw spring-boot:run &
cd backend/onboarding-service    && ./mvnw spring-boot:run &
cd backend/user-service          && ./mvnw spring-boot:run &

# Check provider health (requires a valid admin JWT)
curl -s -H "Authorization: Bearer <admin-jwt>" \
  http://localhost:8083/api/v1/admin/provider-health | jq .

# Watch the provisioning topic for completion events
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic edu.provisioning.github.v1 \
  --from-beginning

# Verify audit log and outbox rows after a registration
psql -h localhost -U default -d provisioning_service \
  -c "SELECT action_name, result_state, response_payload FROM provisioning_audit_log ORDER BY created_at DESC LIMIT 5;"

psql -h localhost -U default -d provisioning_service \
  -c "SELECT event_type, topic, published, payload FROM outbox_event ORDER BY created_at DESC LIMIT 5;"
```