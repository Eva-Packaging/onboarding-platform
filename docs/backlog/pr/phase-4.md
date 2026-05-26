## EDP-279 · Phase 4: Kafka Event Foundation

Establishes the full Kafka event backbone connecting `user-service`, `onboarding-service`, and `provisioning-service`. All 10 Avro schemas are centralized in `backend/common` under the `xyz.catuns.onboarding.common.events` namespace. Each microservice gets its own `KafkaConfig` with `KafkaAvroSerializer`/`KafkaAvroDeserializer`, independent topic declarations, and `AckMode.RECORD` listener container factories. The outbox pattern is extended to all three services: domain writes and Kafka publication stay in the same DB transaction; a `@Scheduled` publisher deserializes the stored JSON payload to an Avro `SpecificRecord` before sending. `provisioning-service` is fully stubbed for Phase 4 and adds an exponential-backoff error handler that routes unrecoverable failures to per-topic DLT topics.

---

## Commits

| Commit    | Story   | Summary                                                                                  |
|-----------|---------|------------------------------------------------------------------------------------------|
| `257988b` | EDP-280 | Centralize Avro schemas in backend/common with updated namespace                         |
| `e3edc18` | EDP-281 | Update user-service KafkaConfig for Avro and wire outbox publisher to Avro serialization |
| `0283490` | EDP-282 | Create KafkaConfig in onboarding-service with producer, consumer, and topic declarations |
| `4877ca2` | EDP-283 | Implement event consumers and outbox publisher in onboarding-service                     |
| `864479d` | EDP-284 | Create KafkaConfig in provisioning-service with producer, consumer, and error handler    |
| `71a0ad0` | EDP-285 | Implement event consumers and outbox publisher in provisioning-service                   |

---

## What's in this PR

### EDP-280 · Centralize Avro schemas in backend/common

**New module: `backend/common`**

- Maven module with Maven wrapper, `pom.xml` inheriting from `base-starter-parent`
- `avro-maven-plugin` configured to generate Java from `src/main/avro` into `target/generated-sources/avro`
- `io.confluent:kafka-avro-serializer` at compile scope — all three services receive it transitively via the `onboarding-common` dependency
- Namespace `xyz.catuns.onboarding.common.events` applied uniformly across all 10 schemas

**Avro schemas created**

| Schema                             | Topic                           | Producer                   |
|------------------------------------|---------------------------------|----------------------------|
| `UserRegisteredV1`                 | `edu.user.registered.v1`        | user-service               |
| `IdentityCorrelationRequestedV1`   | `edu.identity.correlation.v1`   | onboarding-service         |
| `IdentityCorrelationCompletedV1`   | `edu.identity.correlation.v1`   | identity-service (Phase 5) |
| `IdentityCorrelationFailedV1`      | `edu.identity.correlation.v1`   | identity-service (Phase 5) |
| `GithubProvisioningRequestedV1`    | `edu.provisioning.github.v1`    | onboarding-service         |
| `GithubProvisioningCompletedV1`    | `edu.provisioning.github.v1`    | provisioning-service       |
| `AtlassianProvisioningRequestedV1` | `edu.provisioning.atlassian.v1` | onboarding-service         |
| `AtlassianProvisioningCompletedV1` | `edu.provisioning.atlassian.v1` | provisioning-service       |
| `OnboardingCompletedV1`            | `edu.onboarding.lifecycle.v1`   | onboarding-service         |
| `OnboardingFailedV1`               | `edu.onboarding.lifecycle.v1`   | onboarding-service         |

Nullable fields use Avro union type `["null", "string"]` with `"default": null` to ensure backward compatibility. `docs/schemas/kafka-event-schema.md` updated to reflect the new namespace.

---

### EDP-281 · Update user-service KafkaConfig for Avro

**`KafkaConfig.java`**

- `producerFactory` now adds `ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG = KafkaAvroSerializer.class` to the properties map after `buildProducerProperties(null)` — `KafkaTemplate` becomes `KafkaTemplate<String, SpecificRecord>`
- `idCorrelationTopic` bean removed — `edu.identity.correlation.v1` is owned by onboarding-service; user-service was incorrectly declaring it
- `app.kafka.partitions: 1` and `app.kafka.replicas: 1` added to `application.yaml` — mandatory for the single-node KRaft broker in the local dev stack

**`OutboxEventPublisher.java`**

- Now holds `KafkaTemplate<String, SpecificRecord>` and `ObjectMapper`
- `toAvroRecord(OutboxEvent)` switch dispatches on `event.getEventType()`:  `"UserRegisteredV1"` → deserializes JSON payload via `ObjectMapper` into the Avro builder and returns a `UserRegisteredV1` `SpecificRecord`; unrecognized types throw `IllegalStateException`
- `OutboxEventPublisherTest` updated to assert the published Kafka record value is a `UserRegisteredV1` instance rather than a raw `String`

---

### EDP-282 · KafkaConfig in onboarding-service

**`KafkaConfig.java`** — new `@Configuration` class in `onboarding-service`

- `producerFactory` — `KafkaAvroSerializer` as value serializer; `schema.registry.url` injected from `spring.kafka.properties.schema-registry-url`
- `kafkaTemplate` — `KafkaTemplate<String, SpecificRecord>`
- `consumerFactory` — `KafkaAvroDeserializer` as value deserialiser, `StringDeserializer` as key deserialiser, `specific.avro.reader = true`
- `kafkaListenerContainerFactory` — `ConcurrentKafkaListenerContainerFactory` with `AckMode.RECORD`

**Topic declarations** — onboarding-service owns four topics:

| Bean                         | Topic name                      | Retention override                    |
|------------------------------|---------------------------------|---------------------------------------|
| `idCorrelationTopic`         | `edu.identity.correlation.v1`   | default                               |
| `githubProvisioningTopic`    | `edu.provisioning.github.v1`    | default                               |
| `atlassianProvisioningTopic` | `edu.provisioning.atlassian.v1` | default                               |
| `onboardingLifecycleTopic`   | `edu.onboarding.lifecycle.v1`   | `RETENTION_MS = 1209600000` (14 days) |

The lifecycle topic retention is doubled from the broker default because `OnboardingCompletedV1` and `OnboardingFailedV1` are audit-relevant outcome events. `app.kafka.partitions: 1` and `app.kafka.replicas: 1` set in `application.yaml` for the single-node broker.

---

### EDP-283 · Event consumers and outbox publisher in onboarding-service

**`UserRegisteredEventConsumer`**

- `@KafkaListener(topics = "${app.kafka.topics.user-registered}")` receives `UserRegisteredV1`
- Delegates to `OnboardingEventService.handleUserRegistered` which, in a single `@Transactional` method:
  - Resolves the `OnboardingRequest` by `userId`; skips if already in `PROCESSING` or later (idempotency guard)
  - Transitions all steps to `PROCESSING` via `OnboardingDomainService`
  - Writes three `outbox_event` rows: `IdentityCorrelationRequestedV1` → `edu.identity.correlation.v1`, `GithubProvisioningRequestedV1` → `edu.provisioning.github.v1`, `AtlassianProvisioningRequestedV1` → `edu.provisioning.atlassian.v1`
  - Payload for each row is built by `OutboxPayloadBuilderService`, which assembles the Avro field map from the inbound event and step/provider metadata

**`ProvisioningCompletedConsumer`**

Three `@KafkaListener` methods, each consuming `SpecificRecord` and dispatching on `instanceof`:

- `consumeIdentityCorrelation` (`edu.identity.correlation.v1`): handles `IdentityCorrelationCompletedV1` (persists `atlassianIdentityId` and `matchStrategy`, transitions step to `SUCCEEDED`) and `IdentityCorrelationFailedV1` (sets step to `FAILED` with `reasonCode`); silently ignores `IdentityCorrelationRequestedV1` published by this service
- `consumeGithubProvisioning` (`edu.provisioning.github.v1`): handles `GithubProvisioningCompletedV1`; ignores `GithubProvisioningRequestedV1`
- `consumeAtlassianProvisioning` (`edu.provisioning.atlassian.v1`): handles `AtlassianProvisioningCompletedV1`; ignores `AtlassianProvisioningRequestedV1`

After every step state change, `OnboardingEventService` checks whether all steps have reached a terminal state:
- All `SUCCEEDED` → writes `OnboardingCompletedV1` outbox row to `edu.onboarding.lifecycle.v1`
- Any `FAILED` with no retries remaining → writes `OnboardingFailedV1` outbox row

**`OutboxEventPublisher` (onboarding-service)**

- `@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")`
- `toAvroRecord` switch covers all five outbox event types produced by this service: `IdentityCorrelationRequestedV1`, `GithubProvisioningRequestedV1`, `AtlassianProvisioningRequestedV1`, `OnboardingCompletedV1`, `OnboardingFailedV1`

---

### EDP-284 · KafkaConfig in provisioning-service

**`KafkaConfig.java`** — rewritten `@Configuration` class

- `producerFactory` — `KafkaAvroSerializer` + `schema.registry.url`; `KafkaTemplate<String, SpecificRecord>`
- `consumerFactory` — `KafkaAvroDeserializer` + `specific.avro.reader = true`; `KafkaConsumerMetrics` bean retained for Micrometer binding

**Error handler**

- `CommonErrorHandler errorHandler` bean:
  - `DeadLetterPublishingRecoverer` routes failed records to `<original-topic>.DLT` at partition 0
  - `ExponentialBackOff(2000L, 2.0)` with `maxAttempts = 3` — retries at 2 s, 4 s, 8 s before routing to DLT
- `kafkaListenerContainerFactory` wires the error handler and sets `AckMode.RECORD`

**DLT topic declarations** — provisioning-service owns no main topics (those are created by onboarding-service) but declares the DLT topics with infinite retention so dead-lettered failures are never expired before manual resolution:

| Bean                            | Topic name                          | `RETENTION_MS`  | `CLEANUP_POLICY` |
|---------------------------------|-------------------------------------|-----------------|------------------|
| `githubProvisioningDltTopic`    | `edu.provisioning.github.v1.DLT`    | `-1` (infinite) | `delete`         |
| `atlassianProvisioningDltTopic` | `edu.provisioning.atlassian.v1.DLT` | `-1` (infinite) | `delete`         |

`pom.xml` updated to add `onboarding-common:1.0-SNAPSHOT` as a compile dependency. `application.yaml` updated: `consumer.group-id` changed from `provisioning-service-group` to `provisioning-service`; JSON serialiser/deserialiser properties removed (replaced by programmatic Avro config); `schema-registry-url` and all four `app.kafka.topics.*` entries added.

---

### EDP-285 · Event consumers and outbox publisher in provisioning-service

**`GithubProvisioningRequestedEventHandler`**

- `@KafkaListener(topics = "${app.kafka.topics.github-provisioning}")`, receives `SpecificRecord`
- Dispatches `instanceof GithubProvisioningRequestedV1` to `ProvisioningEventService`; silently ignores `GithubProvisioningCompletedV1` (produced by this service, re-consumed by its own consumer group)

**`AtlassianProvisioningRequestedEventHandler`**

- `@KafkaListener(topics = "${app.kafka.topics.atlassian-provisioning}")`, same dispatch pattern

**`ProvisioningEventService`**

Both `@Transactional` handler methods follow the same structure — within a single transaction:

1. Build and persist a `ProvisioningAuditLog` row:
   - `actionName` = `GITHUB_PROVISION` / `ATLASSIAN_PROVISION`
   - `resultState` = `SUCCESS` (Phase 4 stub — real provider call in Phase 5/6)
   - `providerId` = `UUID.fromString(event.getProviderTargetId())`
   - `correlationId` = `UUID.fromString(event.getCorrelationId())`
   - `requestPayload` = JSON of the inbound event fields
2. Build and persist an `OutboxEvent` row with `eventType = GithubProvisioningCompletedV1` / `AtlassianProvisioningCompletedV1`, `success = true`, `membershipState = ACTIVE` (GitHub), and the topic set to the same channel the request arrived on

**`OutboxEventPublisher` (provisioning-service)**

- `KafkaTemplate<String, SpecificRecord>` + `ObjectMapper`
- `toAvroRecord` switch covers `GithubProvisioningCompletedV1` and `AtlassianProvisioningCompletedV1`; all nullable fields (`errorCode`, `errorMessage`) serialized as `null` so the Avro builder receives correct typed values

---

## Test coverage

| Service              | Story   | Test type                          | Class                          | Tests                                    |
|----------------------|---------|------------------------------------|--------------------------------|------------------------------------------|
| user-service         | EDP-281 | `@SpringBootTest` + Testcontainers | `OutboxEventPublisherTest`     | updated — asserts `SpecificRecord` value |
| onboarding-service   | EDP-283 | `@SpringBootTest` + Testcontainers | `OnboardingEventServiceTest`   | 11                                       |
| provisioning-service | EDP-285 | `@SpringBootTest` + Testcontainers | `ProvisioningEventServiceTest` | 8                                        |

`OnboardingEventServiceTest` covers: `UserRegisteredV1` writing three outbox rows in one transaction; step state transitions to `PROCESSING` on ingest; idempotency guard preventing duplicate outbox rows on redelivery; GitHub and Atlassian completion handlers transitioning steps to `SUCCEEDED`/`FAILED`; identity correlation completion and failure handlers; terminal state check writing `OnboardingCompletedV1` and `OnboardingFailedV1` to the lifecycle topic; outbox topic routing correctness for all five event types.

`ProvisioningEventServiceTest` covers: audit log row fields (`actionName`, `resultState`, `providerId`, `correlationId`); completion outbox row event type, topic, and `aggregateId`; payload JSON content (`success:true`, `membershipState:ACTIVE`, `eventType`); atomic write of audit log + outbox in one transaction; cross-topic routing (GitHub → `edu.provisioning.github.v1`, Atlassian → `edu.provisioning.atlassian.v1`).

All `@SpringBootTest` tests use `ConfluentKafkaContainer` + `PostgreSQLContainer` via Testcontainers. `OutboxEventPublisher` is `@MockBean`-ed in every test class to prevent `KafkaAvroSerializer` from contacting Schema Registry, which is not present in the CI environment.

---

## Key design decisions

**Avro schemas in `backend/common`, not inline per service.** Centralising all 10 schemas in a single Maven module eliminates schema duplication and ensures the producer and consumer always share the identical generated Java class. `specific.avro.reader = true` on all consumer factories means the Schema Registry returns the exact generated type, not a `GenericRecord`.

**JSON stored in `outbox_event.payload`, Avro built at publish time.** The domain write path stores a plain JSON string in `outbox_event.payload` — no Avro dependency in the entity or repository layer. The `@Scheduled` publisher converts the JSON to an Avro `SpecificRecord` immediately before calling `kafkaTemplate.send`. This keeps the transactional domain logic free of serialisation concerns and makes payloads human-readable in the DB.

**Request and completion events share the same provisioning topics.** `edu.provisioning.github.v1` carries both `GithubProvisioningRequestedV1` (onboarding-service → provisioning-service) and `GithubProvisioningCompletedV1` (provisioning-service → onboarding-service). Each service's consumer group receives all messages; each handler filters by `instanceof` and silently ignores the event type it produced itself. This avoids proliferating topic names while keeping the routing explicit in code.

**Topic ownership is exclusive.** Only one service declares a `NewTopic` bean for each topic. `provisioning-service` declares no main-topic `NewTopic` beans — it relies on onboarding-service having created `edu.provisioning.github.v1` and `edu.provisioning.atlassian.v1`. This prevents split-brain topic configuration and avoids conflicting partition/replica settings across services.

**`AckMode.RECORD` on all listener container factories.** Offsets are committed individually after each `@Transactional` listener method returns, ensuring a DB write failure rolls back without committing the offset. A batch ack mode could silently advance the offset past an uncommitted DB write, masking the failure.

**DLT topics created by provisioning-service with infinite retention.** `RETENTION_MS = -1` and `CLEANUP_POLICY = delete` ensure that records routed to `edu.provisioning.github.v1.DLT` or `edu.provisioning.atlassian.v1.DLT` are never expired before an operator inspects and replays them. Compaction is explicitly disabled (`CLEANUP_POLICY_DELETE`) so all failure records, including duplicates of the same key, are preserved.

**`onboarding-lifecycle` topic uses 14-day retention.** The default broker retention is 7 days. `OnboardingCompletedV1` and `OnboardingFailedV1` are terminal outcome events used for audit and support investigations; doubling retention is an inexpensive way to ensure they outlive the standard window without requiring external archival in Phase 4.

**Phase 4 provisioning stub is transactionally safe.** `ProvisioningEventService` writes the `ProvisioningAuditLog` row and the `GithubProvisioningCompletedV1`/`AtlassianProvisioningCompletedV1` outbox row in a single transaction. If either write fails, neither is committed. The exponential-backoff error handler in `KafkaConfig` will retry up to 3 times before routing the original request record to the DLT. The stub is a drop-in replacement for the real provider call in Phase 5 — only the body of `handleGithubProvisioningRequested` changes.

**Idempotency guard in `handleUserRegistered`.** If the onboarding-service consumer receives a `UserRegisteredV1` event twice (Kafka at-least-once delivery), the second invocation detects that the `OnboardingRequest` is already in `PROCESSING` or later and returns without writing duplicate outbox rows. This prevents onboarding-service from emitting duplicate provisioning requests to downstream services.

---

## Docs & schema references

- Avro schemas and topic names: `docs/schemas/kafka-event-schema.md`
- Database schema (outbox tables, `provisioning_audit_log`): `docs/schemas/normalized-database-schema.md`
- Architecture constraints (outbox pattern, async-only provisioning): `docs/design-spec.md`
- Roadmap exit criteria: `docs/roadmap.md` — Phase 4
- Backlog: `docs/backlog/phase-4-kafka-event-foundation.json`

---

## How to verify locally

```bash
# Start Postgres + Kafka + Schema Registry + Consul
docker compose -f infra/docker/docker-compose.yml up -d

# Run onboarding-service integration tests (Testcontainers — Docker must be running)
cd backend/onboarding-service && ./mvnw test -Dtest=OnboardingEventServiceTest

# Run provisioning-service integration tests
cd backend/provisioning-service && ./mvnw test -Dtest=ProvisioningEventServiceTest

# Boot all services
cd backend/user-service       && ./mvnw spring-boot:run &
cd backend/onboarding-service && ./mvnw spring-boot:run &
cd backend/provisioning-service && ./mvnw spring-boot:run &

# Trigger the full event chain via the registration endpoint
curl -s -X POST http://localhost:8080/api/v1/registrations \
  -H "Content-Type: application/json" \
  -d '{
    "githubId": "gh-123",
    "githubLogin": "testuser",
    "email": "test@example.com",
    "displayName": "Test User",
    "roles": ["STUDENT"]
  }' | jq .

# Watch Kafka topics for events
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic edu.provisioning.github.v1 \
  --from-beginning

# Check outbox_event rows in each service's DB
psql -h localhost -U default -d onboarding_service \
  -c "SELECT event_type, topic, published, published_at FROM outbox_event ORDER BY created_at;"
```

Testcontainers spins up isolated Postgres and Kafka per test run — no local infrastructure required for the integration test suites.