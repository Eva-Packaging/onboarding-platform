# Request Flow

This document summarizes the end-to-end request flow for the onboarding platform. It is intended for repository documentation and focuses on how the frontend, backend services, and external providers interact during registration, onboarding, support, and reprovisioning.

## Overview

The platform uses GitHub-first authentication on the frontend and starts backend onboarding after authentication has already succeeded. The backend entry point is `POST /api/v1/registrations`, which creates the internal user, stores the GitHub identity snapshot, creates an onboarding request, and starts asynchronous onboarding work.

The onboarding workflow is asynchronous because external provider steps may finish at different times. GitHub team membership may remain pending until the user accepts an organization or team invitation, and Atlassian or Jira provisioning may complete independently.

## Actors

- **User**: signs in with GitHub and waits for onboarding to complete.
- **Next.js frontend**: collects GitHub-authenticated user data and calls backend APIs.
- **User service**: creates the internal user profile and persists identity information.
- **Onboarding service**: creates onboarding requests, tracks step status, and coordinates retries.
- **Provisioning services**: call GitHub and Atlassian provider APIs.
- **Support or admin users**: inspect failures, retry steps, fix identity matches, and reprovision access.

## Primary flow

### 1. GitHub authentication succeeds

The user authenticates with GitHub in the frontend. The frontend now has enough authenticated identity data to start backend registration.

Typical fields passed to the backend include:
- `githubUserId`
- `githubLogin`
- `primaryEmail`
- `displayName`
- `avatarUrl`
- `roleKeys`

### 2. Frontend starts registration

The frontend calls:

```http
POST /api/v1/registrations
```

The backend performs these actions:
- Creates or resolves the internal user profile.
- Stores the GitHub identity snapshot.
- Creates a new onboarding request.
- Creates initial onboarding steps.
- Starts asynchronous processing for downstream provisioning.

Example response shape:

```json
{
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "onboardingRequestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
  "status": "IN_PROGRESS",
  "correlationId": "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77",
  "steps": [
    { "type": "IDENTITY_CORRELATION", "state": "PENDING" },
    { "type": "GITHUB_TEAM_PROVISIONING", "state": "PENDING" },
    { "type": "JIRA_GROUP_PROVISIONING", "state": "PENDING" }
  ]
}
```

### 3. Frontend fetches the current user

The frontend can fetch the current internal profile and onboarding summary through:

```http
GET /api/v1/me?include=identities,onboarding
```

This supports:
- showing the logged-in user profile,
- exposing onboarding state in the UI,
- rendering linked GitHub and Atlassian identity information when available.

### 4. Frontend polls onboarding status

The frontend polls:

```http
GET /api/v1/onboarding/{requestId}
```

This endpoint returns the full onboarding request status and step-level progress. The UI uses it to render states such as:
- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `PENDING_EXTERNAL_ACCEPTANCE`
- `FAILED`
- `ACTION_REQUIRED`

## Onboarding step flow

The initial onboarding request contains three main business steps.

### Identity correlation

This step attempts to match the GitHub-authenticated user to the corresponding Atlassian identity. It exists because downstream Jira visibility and attribution may depend on having a valid GitHub-to-Atlassian correlation.

Possible outcomes:
- identity match found,
- no identity match found,
- manual review required.

### GitHub team provisioning

This step adds the user to the configured GitHub organization team or teams. A provisioning attempt may succeed immediately or remain in a pending external state until GitHub invitation acceptance is complete.

Possible outcomes:
- membership active,
- membership pending external acceptance,
- failed provisioning,
- retryable provider error.

### Jira or Atlassian group provisioning

This step provisions the user into the required Atlassian or Jira group. It runs independently from GitHub provisioning so each external provider can complete on its own timeline.

Possible outcomes:
- group membership provisioned,
- provider processing in progress,
- failed provisioning,
- retryable provider error.

## User-visible state transitions

From the frontend perspective, the user usually sees this lifecycle:

1. GitHub login succeeds.
2. Registration starts.
3. Onboarding enters `IN_PROGRESS`.
4. One or more steps succeed.
5. A step may remain pending due to provider-side acceptance or processing.
6. Final state becomes one of:
   - `COMPLETED`
   - `PARTIAL_SUCCESS`
   - `FAILED`
   - `ACTION_REQUIRED`

## Support and retry flow

If onboarding fails or stalls, support users and admins use the operational APIs.

### List onboarding requests

```http
GET /api/v1/admin/onboarding
```

This endpoint is used to find requests by state, user, or correlation ID.

### Review audit trail

```http
GET /api/v1/admin/onboarding/{requestId}/audit
```

This endpoint shows provider-call history and response summaries. It is useful for understanding whether GitHub returned a pending membership state or whether Atlassian group provisioning failed.

### Retry failed steps

```http
POST /api/v1/onboarding/{requestId}/retry
```

This endpoint requeues one or more retryable onboarding steps without forcing the user to register again.

Example request:

```json
{
  "steps": ["JIRA_GROUP_PROVISIONING"],
  "reason": "Atlassian group sync recovered"
}
```

## Identity-repair flow

When automatic identity correlation is not successful, admins can inspect or override the match.

### View identity links

```http
GET /api/v1/users/{userId}/identity-links
```

This endpoint returns the current GitHub-to-Atlassian identity relationship, match strategy, and confidence score.

### Create or override identity link

```http
POST /api/v1/admin/users/{userId}/identity-links
```

This endpoint is used for manual repair when the automatic identity match is missing or incorrect.

## Configuration flow

Admins configure which external targets should be assigned during onboarding.

### View provider targets

```http
GET /api/v1/admin/provider-targets
```

This endpoint returns configured external targets such as GitHub teams and Atlassian groups.

### Create role-to-target mappings

```http
POST /api/v1/admin/group-mappings
```

This endpoint maps internal roles or cohorts to provider targets. For example:
- `STUDENT` -> GitHub `learners` team
- `STUDENT` -> Atlassian `jira-software-users` group

These rules determine which onboarding steps and targets are created during registration.

## Reprovisioning flow

Access can be reprovisioned later without creating a brand-new user account.

### Reprovision access

```http
POST /api/v1/admin/users/{userId}/reprovision
```

This creates a new onboarding request for an existing user after:
- a role change,
- a cohort change,
- provider recovery,
- a support-driven manual fix.

This flow reuses the same onboarding engine instead of inventing a second provisioning path.

## Provider-health flow

Admins can inspect provider readiness through:

```http
GET /api/v1/admin/provider-health
```

This helps determine whether onboarding issues are caused by internal logic or by external GitHub or Atlassian provider degradation.

## Sequence summary

```text
User
  -> Frontend GitHub login
  -> Frontend POST /api/v1/registrations
  -> Backend creates user + onboarding request
  -> Backend starts identity correlation
  -> Backend starts GitHub team provisioning
  -> Backend starts Jira/Atlassian group provisioning
  -> Frontend GET /api/v1/me
  -> Frontend polls GET /api/v1/onboarding/{requestId}
  -> User sees COMPLETED, PENDING, or ACTION_REQUIRED state

Support/Admin
  -> GET /api/v1/admin/onboarding
  -> GET /api/v1/admin/onboarding/{requestId}/audit
  -> POST /api/v1/onboarding/{requestId}/retry
  -> POST /api/v1/admin/users/{userId}/identity-links
  -> POST /api/v1/admin/users/{userId}/reprovision
```

## Key implementation note

The documented backend flow starts at `POST /api/v1/registrations`, not at `/auth/exchange`. That means authentication is assumed to be completed by the frontend auth layer before the backend onboarding flow begins.
