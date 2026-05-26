# Kafka Event Schema

This document defines the initial Kafka event contracts for the education platform onboarding system using Avro serialization and Schema Registry. The event model supports GitHub-first registration, identity correlation with Atlassian, GitHub team provisioning, Jira group provisioning, and completion tracking across asynchronous services.

## Assumptions

- Events are serialized as Avro and registered in Schema Registry.
- Schema evolution is managed through subject compatibility rules, which Schema Registry supports centrally for Avro subjects.
- Topics carry domain events rather than mixing unrelated payload types in the same topic when avoidable, which aligns with common Schema Registry best practice for governance and compatibility management.
- Event payloads include enough identifiers to correlate records across services and database tables, especially onboarding requests, steps, and users.

## Naming conventions

### Topic naming

Use a stable topic naming convention with domain and event family semantics.

```text
edu.user.registered.v1
edu.identity.correlation.v1
edu.provisioning.github.v1
edu.provisioning.atlassian.v1
edu.onboarding.lifecycle.v1
```

### Subject naming

Use TopicRecordNameStrategy or RecordNameStrategy so Avro record evolution stays manageable as multiple event types grow. Schema Registry supports centralized schema registration and compatibility checks for Avro subjects, so the naming strategy should be standardized early.

Recommended subject examples:

```text
edu.user.registered.v1-value
xyz.catuns.onboarding.events.UserRegisteredV1
xyz.catuns.onboarding.events.IdentityCorrelationRequestedV1
```

### Event naming

Use business-event names with explicit version suffixes in the Avro record name.

Examples:
- `UserRegisteredV1`
- `IdentityCorrelationRequestedV1`
- `IdentityCorrelationCompletedV1`
- `GithubProvisioningRequestedV1`
- `GithubProvisioningCompletedV1`
- `AtlassianProvisioningRequestedV1`
- `AtlassianProvisioningCompletedV1`
- `OnboardingCompletedV1`
- `OnboardingFailedV1`

## Common envelope pattern

Each event should carry a common metadata envelope so consumers can trace, deduplicate, and debug messages consistently. Schema Registry supports schema reuse and compatibility management, but the business payload still needs stable cross-service metadata design.

Common fields for every event:

| Field                 | Avro Type          | Notes                                  |
|-----------------------|--------------------|----------------------------------------|
| `eventId`             | `string`           | Unique event UUID                      |
| `eventType`           | `string`           | Logical event name                     |
| `eventVersion`        | `int`              | Payload version, usually `1` initially |
| `occurredAt`          | `string`           | ISO-8601 timestamp                     |
| `correlationId`       | `string`           | Distributed trace ID                   |
| `producer`            | `string`           | Producing service name                 |
| `userId`              | `string`           | Internal user UUID                     |
| `onboardingRequestId` | `null` or `string` | Nullable when event is pre-onboarding  |

Recommended metadata rules:
- Use UUID strings for IDs to keep cross-language interoperability simple.
- Keep timestamps as ISO-8601 strings unless your Avro conventions already standardize logical timestamp types.
- Treat `eventType` as immutable once published.

## Topics and event families

| Topic                           | Purpose                                     | Example event types                                                                               |
|---------------------------------|---------------------------------------------|---------------------------------------------------------------------------------------------------|
| `edu.user.registered.v1`        | Initial registration and user bootstrap     | `UserRegisteredV1`                                                                                |
| `edu.identity.correlation.v1`   | GitHub-to-Atlassian matching lifecycle      | `IdentityCorrelationRequestedV1`, `IdentityCorrelationCompletedV1`, `IdentityCorrelationFailedV1` |
| `edu.provisioning.github.v1`    | GitHub org/team provisioning lifecycle      | `GithubProvisioningRequestedV1`, `GithubProvisioningCompletedV1`                                  |
| `edu.provisioning.atlassian.v1` | Atlassian/Jira group provisioning lifecycle | `AtlassianProvisioningRequestedV1`, `AtlassianProvisioningCompletedV1`                            |
| `edu.onboarding.lifecycle.v1`   | Final onboarding outcomes                   | `OnboardingCompletedV1`, `OnboardingFailedV1`                                                     |

This split matches the asynchronous boundaries in the system design: GitHub membership may remain pending until invitation acceptance, Atlassian provisioning may progress independently, and identity correlation is its own business step because Jira-linked developer attribution depends on that match.

## Avro schemas

### 1. `UserRegisteredV1`

**Topic:** `edu.user.registered.v1`

```json
{
  "type": "record",
  "name": "UserRegisteredV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "UserRegisteredV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "displayName", "type": "string" },
    { "name": "primaryEmail", "type": "string" },
    { "name": "githubUserId", "type": "string" },
    { "name": "githubLogin", "type": "string" },
    { "name": "roleKeys", "type": { "type": "array", "items": "string" }, "default": [] }
  ]
}
```

**Produced by:** `user-service`

**Consumed by:** `onboarding-service`, `provisioning-service`

### 2. `IdentityCorrelationRequestedV1`

**Topic:** `edu.identity.correlation.v1`

```json
{
  "type": "record",
  "name": "IdentityCorrelationRequestedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "IdentityCorrelationRequestedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "githubIdentityId", "type": "string" },
    { "name": "githubLogin", "type": "string" },
    { "name": "primaryEmail", "type": ["null", "string"], "default": null }
  ]
}
```

**Produced by:** `onboarding-service`

**Consumed by:** `provisioning-service` or dedicated `identity-service`

### 3. `IdentityCorrelationCompletedV1`

**Topic:** `edu.identity.correlation.v1`

```json
{
  "type": "record",
  "name": "IdentityCorrelationCompletedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "IdentityCorrelationCompletedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "githubIdentityId", "type": "string" },
    { "name": "atlassianIdentityId", "type": ["null", "string"], "default": null },
    { "name": "matchStrategy", "type": ["null", "string"], "default": null },
    { "name": "confidenceScore", "type": ["null", "double"], "default": null },
    { "name": "matched", "type": "boolean" }
  ]
}
```

### 4. `IdentityCorrelationFailedV1`

**Topic:** `edu.identity.correlation.v1`

```json
{
  "type": "record",
  "name": "IdentityCorrelationFailedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "IdentityCorrelationFailedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "reasonCode", "type": "string" },
    { "name": "reasonMessage", "type": ["null", "string"], "default": null }
  ]
}
```

### 5. `GithubProvisioningRequestedV1`

**Topic:** `edu.provisioning.github.v1`

```json
{
  "type": "record",
  "name": "GithubProvisioningRequestedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "GithubProvisioningRequestedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "githubLogin", "type": "string" },
    { "name": "githubOrg", "type": "string" },
    { "name": "githubTeamSlug", "type": "string" },
    { "name": "providerTargetId", "type": "string" }
  ]
}
```

This event supports GitHub team-assignment work, which is important because GitHub team membership calls may complete immediately or remain pending until a user accepts an invitation.

### 6. `GithubProvisioningCompletedV1`

**Topic:** `edu.provisioning.github.v1`

```json
{
  "type": "record",
  "name": "GithubProvisioningCompletedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "GithubProvisioningCompletedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "providerTargetId", "type": "string" },
    { "name": "membershipState", "type": "string" },
    { "name": "success", "type": "boolean" },
    { "name": "errorCode", "type": ["null", "string"], "default": null },
    { "name": "errorMessage", "type": ["null", "string"], "default": null }
  ]
}
```

Expected `membershipState` values may include `ACTIVE`, `PENDING`, or `FAILED`, reflecting the GitHub membership lifecycle.

### 7. `AtlassianProvisioningRequestedV1`

**Topic:** `edu.provisioning.atlassian.v1`

```json
{
  "type": "record",
  "name": "AtlassianProvisioningRequestedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "AtlassianProvisioningRequestedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "atlassianIdentityId", "type": ["null", "string"], "default": null },
    { "name": "atlassianEmail", "type": ["null", "string"], "default": null },
    { "name": "groupName", "type": "string" },
    { "name": "providerTargetId", "type": "string" }
  ]
}
```

This event models Atlassian group provisioning, which fits Atlassian's documented provisioning and SCIM-oriented user-management approach.

### 8. `AtlassianProvisioningCompletedV1`

**Topic:** `edu.provisioning.atlassian.v1`

```json
{
  "type": "record",
  "name": "AtlassianProvisioningCompletedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "AtlassianProvisioningCompletedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "providerTargetId", "type": "string" },
    { "name": "success", "type": "boolean" },
    { "name": "errorCode", "type": ["null", "string"], "default": null },
    { "name": "errorMessage", "type": ["null", "string"], "default": null }
  ]
}
```

### 9. `OnboardingCompletedV1`

**Topic:** `edu.onboarding.lifecycle.v1`

```json
{
  "type": "record",
  "name": "OnboardingCompletedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "OnboardingCompletedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "finalState", "type": "string" }
  ]
}
```

### 10. `OnboardingFailedV1`

**Topic:** `edu.onboarding.lifecycle.v1`

```json
{
  "type": "record",
  "name": "OnboardingFailedV1",
  "namespace": "xyz.catuns.onboarding.events",
  "fields": [
    { "name": "eventId", "type": "string" },
    { "name": "eventType", "type": "string", "default": "OnboardingFailedV1" },
    { "name": "eventVersion", "type": "int", "default": 1 },
    { "name": "occurredAt", "type": "string" },
    { "name": "correlationId", "type": "string" },
    { "name": "producer", "type": "string" },
    { "name": "userId", "type": "string" },
    { "name": "onboardingRequestId", "type": "string" },
    { "name": "failureStep", "type": "string" },
    { "name": "failureCode", "type": ["null", "string"], "default": null },
    { "name": "failureMessage", "type": ["null", "string"], "default": null }
  ]
}
```

## Producer and consumer map

| Event | Producer | Primary consumers |
|---|---|---|
| `UserRegisteredV1` | `user-service` | `onboarding-service`, `provisioning-service` |
| `IdentityCorrelationRequestedV1` | `onboarding-service` | `identity-service` or `provisioning-service` |
| `IdentityCorrelationCompletedV1` | `identity-service` | `onboarding-service`, `provisioning-service` |
| `IdentityCorrelationFailedV1` | `identity-service` | `onboarding-service` |
| `GithubProvisioningRequestedV1` | `onboarding-service` | `provisioning-service` |
| `GithubProvisioningCompletedV1` | `provisioning-service` | `onboarding-service` |
| `AtlassianProvisioningRequestedV1` | `onboarding-service` | `provisioning-service` |
| `AtlassianProvisioningCompletedV1` | `provisioning-service` | `onboarding-service` |
| `OnboardingCompletedV1` | `onboarding-service` | analytics, notification, audit consumers |
| `OnboardingFailedV1` | `onboarding-service` | analytics, notification, support tooling |

## Compatibility and evolution rules

Schema Registry supports Avro compatibility validation and schema version management, so compatibility should be enforced per subject from the start.

Recommended rules:
- Use `BACKWARD` compatibility for event subjects so newer consumers can read older data while producers evolve safely.
- Add optional fields with defaults rather than removing or renaming fields abruptly, because Avro evolution works best when changes are additive and planned.
- Do not reuse a field name for a different meaning.
- Treat event record names as immutable contracts once published.
- Introduce `V2` record names only when a breaking contract change is unavoidable.

Examples of safe evolution:
- Add `manualReviewRequired` as `['null','boolean']` with default `null`.
- Add `providerSite` with default `null`.

Examples of unsafe evolution:
- Renaming `githubLogin` to `username` without a coordinated compatibility strategy.
- Changing `confidenceScore` from `double` to `string`.

## Key design notes

### Why identity correlation is its own event family

GitHub-to-Jira attribution is not guaranteed by basic registration alone. Since visibility issues can arise when Jira cannot correctly associate a GitHub user, correlation should be treated as a first-class asynchronous step with its own success and failure events.

### Why GitHub and Atlassian provisioning are separate topics

GitHub team membership and Atlassian group provisioning have different operational semantics and failure modes. GitHub membership can remain pending due to invitation acceptance, while Atlassian provisioning aligns more closely with managed user/group administration patterns.

### Why event metadata is repeated

Stable event-level metadata simplifies observability, replay analysis, dead-letter handling, and database correlation across services. Schema Registry manages schema governance, but traceability still depends on the payload design chosen by the platform.
