# Implementation Roadmap

This roadmap outlines a practical, AI-assisted delivery plan for the onboarding platform. It is organized so a coding assistant can work in small, reviewable increments and validate each phase before moving forward.

## Goals

- Deliver a GitHub-first onboarding flow.
- Keep external provisioning asynchronous.
- Preserve a normalized data model and event-driven design.
- Produce documentation and code together so the repo stays agent-friendly.

## Phase 0: Project setup

### Deliverables

- Repository structure aligned with `xyz.catuns.onboarding`.
- Baseline documentation in place:
  - `design-spec.md`
  - `request-flow.md`
  - `rest-api-reference.md`
  - `normalized-database-schema.md`
  - `kafka-event-schema.md`
- Local development setup:
  - Spring Boot backend modules.
  - Next.js frontend skeleton.
  - PostgreSQL container.
  - Kafka local broker or test container.
- Terraform scaffolding for GCP resources.

### Exit criteria

- App boots locally.
- Docs reflect the current architecture.
- Core package and naming conventions are fixed.

## Phase 1: Domain and persistence

### Deliverables

- JPA entities and migrations for:
  - `user_account`
  - `identity_link`
  - `onboarding_request`
  - `onboarding_step`
  - `external_group_mapping`
  - `provisioning_audit_log`
- Repository layer for each aggregate.
- Seed data for provider targets and mapping rules.
- Basic domain services for onboarding state transitions.

### Exit criteria

- Database schema matches documentation.
- CRUD and lookup operations pass tests.
- Onboarding request and step entities are persisted consistently.

## Phase 2: Registration API

### Deliverables

- `POST /api/v1/registrations`.
- `GET /api/v1/me`.
- DTOs and validation.
- Internal user creation and GitHub identity linking.
- Initial onboarding request creation.
- Correlation ID propagation.

### Exit criteria

- A GitHub-authenticated frontend can create a user and receive onboarding status.
- API responses match `rest-api-reference.md`.
- Validation and error envelopes are consistent.

## Phase 3: Onboarding orchestration

### Deliverables

- Onboarding request lifecycle service.
- Step state machine.
- Status API: `GET /api/v1/onboarding/{requestId}`.
- Retry API: `POST /api/v1/onboarding/{requestId}/retry`.
- Provider target resolution from mapping rules.

### Exit criteria

- Step transitions are deterministic and idempotent.
- Frontend can poll onboarding state.
- Retry logic works for failed or stuck steps.

## Phase 4: Kafka event foundation

### Deliverables

- Avro schemas and Schema Registry subject naming.
- Kafka producer infrastructure.
- Kafka consumer infrastructure.
- Outbox or equivalent reliable event publication.
- Event handlers for:
  - `UserRegisteredV1`
  - `IdentityCorrelationRequestedV1`
  - `GithubProvisioningRequestedV1`
  - `AtlassianProvisioningRequestedV1`
  - corresponding completion events.

### Exit criteria

- Events serialize and deserialize correctly.
- Event compatibility rules are enforced.
- DB writes and event publication remain consistent.

## Phase 5: GitHub provisioning

### Deliverables

- GitHub adapter/service.
- Add or update team membership calls.
- Pending membership state handling.
- Provider audit logging.
- Completion event publication.

### Exit criteria

- Users can be provisioned into configured GitHub teams.
- Pending acceptance is reflected in onboarding state.
- Provider failures are visible in audit logs and status APIs.

## Phase 6: Atlassian/Jira provisioning

### Deliverables

- Atlassian adapter/service.
- Group membership provisioning logic.
- Identity correlation support for email/account matching.
- Provider audit logging.
- Completion event publication.

### Exit criteria

- Users can be provisioned into configured Atlassian groups.
- Identity correlation outcomes are persisted.
- Failed provisioning can be retried.

## Phase 7: Frontend onboarding UX

### Deliverables

- GitHub-only sign-in entry point.
- Onboarding status page.
- Step-by-step progress UI.
- Pending/error/action-required states.
- Support correlation ID display.

### Exit criteria

- User can sign in, register, and watch onboarding progress.
- UI reflects partial success and provider-specific pending states.
- No hard refresh is required for normal onboarding visibility.

## Phase 8: Admin and support tooling

### Deliverables

- Admin onboarding list endpoint.
- Audit trail endpoint.
- Manual identity link override.
- Reprovision endpoint.
- Provider health endpoint.
- Admin UI or simple support console if needed.

### Exit criteria

- Support can resolve failed onboarding without database editing.
- Admins can inspect and retry provider issues.
- Manual repair paths are safe and auditable.

## Phase 9: Cloud and delivery

### Deliverables

- Terraform for:
  - Cloud Run or equivalent compute.
  - Cloud SQL.
  - Kafka infrastructure.
  - Secret Manager.
  - Service accounts and IAM.
- CI/CD pipeline.
- Environment-specific config.
- Observability:
  - structured logs
  - metrics
  - traces

### Exit criteria

- Staging deployment works end to end.
- Production rollout is repeatable.
- Secrets are not committed to source control.

## Recommended implementation order

1. Data model and migrations.
2. Registration API.
3. Onboarding state machine.
4. Kafka contracts and event plumbing.
5. GitHub provisioning.
6. Atlassian provisioning.
7. Frontend onboarding flow.
8. Admin support features.
9. Terraform and deployment.

## Milestone structure for AI-assisted work

Each milestone should be small enough for an agent to complete and validate independently.

### Milestone size rules

- One API endpoint or one domain capability per task.
- One event family or one consumer per task.
- One integration adapter per task.
- One documentation file update per task.

### Suggested review checklist

- Does the change preserve existing contracts?
- Are tests included?
- Are API examples still accurate?
- Are event schemas backward compatible?
- Does the change align with the design spec?

## Definition of done

A feature is done only when:

- Code is implemented.
- Tests pass.
- Documentation is updated.
- Event and schema changes are compatible.
- The flow can be exercised locally or in staging.
