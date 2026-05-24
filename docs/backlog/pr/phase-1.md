## EDP-101 · Phase 1: Domain and Persistence

Establishes the complete JPA entity model, Flyway migration-managed schema, Spring Data JPA repository layer, development seed data, and onboarding state-transition domain service across all three Spring Boot microservices. All subsequent API and event-driven phases build on top of this foundation.

---

## What's in this PR

### EDP-102 · JPA entities & Flyway migrations — user-service
- `UserProfile`, `ExternalProvider`, `ExternalIdentity`, `IdentityLink`, `AppRole`, `UserRoleAssignment`, `OutboxEvent` entities under `xyz.catuns.onboarding.user.domain`
- `V1__create_user_service_schema.sql` — full schema DDL matching `docs/schemas/normalized-database-schema.md`
- `V2__seed_user_service_data.sql` — stable-UUID seed rows for `external_provider` (GITHUB, ATLASSIAN) and `app_role` (STUDENT, INSTRUCTOR, ADMIN)
- `ExternalIdentity` unique constraint on `(provider_id, external_user_id)`; `profile_json` mapped as `jsonb`

### EDP-103 · JPA entities & Flyway migrations — onboarding-service
- `OnboardingRequest`, `OnboardingStep`, `OnboardingStepType` (read-only / `@Immutable`), `OutboxEvent` entities under `xyz.catuns.onboarding.service.domain`
- `V1__create_onboarding_service_schema.sql` — schema + inline `onboarding_step_type` seed (IDENTITY_CORRELATION, GITHUB_TEAM_PROVISIONING, JIRA_GROUP_PROVISIONING)
- `userProfileId` on `OnboardingRequest` and `providerTargetId` on `OnboardingStep` are bare UUID columns — no foreign key to other services' databases

### EDP-104 · JPA entities & Flyway migrations — provisioning-service
- `ProviderTarget`, `GroupMappingRule`, `ProvisioningAuditLog`, `OutboxEvent` entities under `xyz.catuns.onboarding.provisioning.domain`
- `V1__create_provisioning_service_schema.sql` — schema with unique constraint on `(provider_id, target_type, external_key)`; `request_payload` / `response_payload` mapped as `jsonb`
- `appRoleId` and `providerId` on audit log are bare UUIDs (cross-service references, no FK constraint)
- Added `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, and `postgresql` runtime to provisioning-service `pom.xml`

### EDP-105 · Spring Data JPA repository layer — all services
| Service | Repositories |
|---|---|
| user-service | `UserProfileRepository` · `ExternalProviderRepository` · `ExternalIdentityRepository` · `IdentityLinkRepository` · `AppRoleRepository` · `OutboxEventRepository` |
| onboarding-service | `OnboardingRequestRepository` · `OnboardingStepRepository` · `OnboardingStepTypeRepository` · `OutboxEventRepository` |
| provisioning-service | `ProviderTargetRepository` · `GroupMappingRuleRepository` · `ProvisioningAuditLogRepository` · `OutboxEventRepository` |

Notable finders: `findByPrimaryEmail`, `findByProviderIdAndExternalUserId`, `findByProvider_ProviderKeyAndExternalUserId`, `findByUserProfileId`, `findByOnboardingRequest_Id`, `findByTargetTypeAndEnabledTrue`, `findByPublishedFalseOrderByCreatedAtAsc` (outbox poller, used in phase 4).

### EDP-106 · Seed data — provider targets & mapping rules
- `V2__seed_provider_targets_and_rules.sql` in provisioning-service — stable-UUID `provider_target` rows for GITHUB_TEAM (`evaitcs-org/students`) and ATLASSIAN_GROUP (`jira-students`); four `group_mapping_rule` rows wiring STUDENT and INSTRUCTOR roles to both targets
- Seed UUIDs are documented in a comment block in each V2 file to allow safe cross-service references without runtime joins

### EDP-107 · Onboarding state-transition domain service
- `StepStateTransitions` / `RequestStateTransitions` — unmodifiable `EnumMap` encoding the complete state graphs
- `OnboardingStepStateMachine` — validates and applies step transitions; throws `IllegalStateTransitionException` on illegal moves
- `OnboardingRequestStateResolver` — derives aggregate request state from step terminal states (all SUCCEEDED → COMPLETED; any FAILED + some SUCCEEDED → PARTIAL_SUCCESS; all FAILED → FAILED; any non-terminal → IN_PROGRESS)
- `OnboardingDomainService` — `@Service @Transactional` coordinator: `startRequest`, `transitionStep` (with `attemptCount` increment on each PROCESSING entry), `recalculateRequestState`

---

## Test coverage

| Service | Test type | Classes | Tests |
|---|---|---|---|
| user-service | `@DataJpaTest` + Testcontainers | `UserProfileRepositoryTest`, `ExternalIdentityRepositoryTest` | 14 |
| onboarding-service | `@DataJpaTest` + Testcontainers | `OnboardingRequestRepositoryTest`, `OnboardingStepRepositoryTest`, `OutboxEventRepositoryTest` | 17 |
| onboarding-service | Unit | `OnboardingStepStateMachineTest`, `OnboardingRequestStateResolverTest` | 26 |
| onboarding-service | `@SpringBootTest` + Testcontainers | `OnboardingDomainServiceTest`, `SeedDataVerificationTest`* | 8 |
| provisioning-service | `@DataJpaTest` + Testcontainers | `ProviderTargetRepositoryTest`, `GroupMappingRuleRepositoryTest`, `ProvisioningAuditLogRepositoryTest` | ~12 |
| provisioning-service | `@SpringBootTest` + Testcontainers | `SeedDataVerificationTest` | 5 |

\* Seed verification confirms Flyway V2 data is queryable via repositories after a full application start.

---

## Key design decisions

**Cross-service FK references are bare UUIDs.** `user_profile_id` (onboarding-service), `provider_id` / `onboarding_step_id` (provisioning-service), and `app_role_id` (provisioning-service) are plain UUID columns with no database FK constraint. Each service owns its own database; referential integrity across services is enforced at the application and event level.

**Seed data in isolated V2 migrations.** V1 scripts contain only schema DDL. V2 scripts contain only reference/seed data, allowing production deployments to skip or override V2 without re-running schema migrations.

**`onboarding_step_type` is a read-only reference table.** Mapped with Hibernate `@Immutable`; seeded in V1 (not V2) because the step type set is part of the schema contract, not environment-specific configuration.

**`TestcontainersConfiguration` made public.** Required to allow `@SpringBootTest` integration tests in sub-packages (e.g., `xyz.catuns.onboarding.service.domain`) to `@Import` the shared Postgres + Kafka container configuration.

---

## Docs & schema references

- Schema: `docs/schemas/normalized-database-schema.md`
- Design constraints: `docs/design-spec.md` (outbox pattern, async provisioning, cross-service ownership)
- Roadmap exit criteria: `docs/roadmap.md` — Phase 1

---

## How to verify locally

```bash
# Start Postgres + Kafka
docker compose -f infra/docker/docker-compose.yml up -d

# Run all tests (each service)
cd backend/user-service        && ./mvnw test
cd backend/onboarding-service  && ./mvnw test
cd backend/provisioning-service && ./mvnw test
```

Testcontainers spins up isolated Postgres (and Kafka for `@SpringBootTest`) per test run — no local database required for the test suite.