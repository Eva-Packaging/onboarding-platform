# REST API Reference

This document defines the initial REST API surface for the education platform onboarding and provisioning system. The API is designed around GitHub-first registration, asynchronous onboarding orchestration, GitHub team provisioning, Jira or Atlassian group provisioning, and identity-correlation workflows needed to improve GitHub-to-Jira attribution.

## Design principles

- Use REST for request initiation, status lookup, admin operations, and support workflows.
- Keep long-running provisioning asynchronous and expose status resources for polling.
- Return onboarding request identifiers and correlation identifiers so frontend and support tooling can trace work across services.
- Treat external provider failures and pending states as normal outcomes rather than exceptional transport failures, because GitHub team membership can remain pending and Atlassian provisioning may complete separately.

## Conventions

- Base path: `/api/v1`
- Content type: `application/json`
- Authentication: bearer token from the frontend session
- IDs: UUID strings unless noted otherwise
- Time format: ISO-8601 UTC timestamps

## Error model

All error responses use the same envelope.

```json
{
  "code": "IDENTITY_MATCH_NOT_FOUND",
  "message": "No Atlassian identity could be matched to the GitHub identity.",
  "correlationId": "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77",
  "details": {
    "field": "primaryEmail",
    "step": "IDENTITY_CORRELATION"
  }
}
```

### Common HTTP statuses

| Status | Meaning | Typical causes |
|---|---|---|
| `200 OK` | Successful read or synchronous update | Fetching user or onboarding status |
| `201 Created` | Resource created | Registration or admin mapping creation |
| `202 Accepted` | Async work accepted | Requeue or provisioning request accepted |
| `400 Bad Request` | Validation failure | Missing email, invalid role, malformed request |
| `401 Unauthorized` | Missing or invalid auth | Expired token |
| `403 Forbidden` | Caller lacks access | Non-admin hitting admin endpoints |
| `404 Not Found` | Resource missing | Unknown onboarding request |
| `409 Conflict` | State conflict | Retry requested for already-completed step |
| `422 Unprocessable Entity` | Business rule failure | Identity correlation impossible with current data |
| `500 Internal Server Error` | Unexpected failure | Unhandled server-side exception |
| `503 Service Unavailable` | Dependency unavailable | Provider API or downstream system outage |

## Main workflows

The API covers these project workflows

1. Register a user after GitHub authentication.
2. View current user profile and onboarding state.
3. Poll onboarding progress from the frontend.
4. Retry failed onboarding steps.
5. Manage role-to-target provisioning mappings.
6. Review failed or manual-review onboarding requests.
7. Inspect identity correlation results for GitHub and Atlassian matching.

---

## 1. Start registration

### `POST /api/v1/registrations`

Creates the internal user profile, stores the GitHub identity snapshot, creates an onboarding request, and starts asynchronous onboarding processing.

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `githubUserId` | `string` | Yes | GitHub user ID |
| `githubLogin` | `string` | Yes | GitHub login |
| `primaryEmail` | `string` | No | Resolved GitHub email if available |
| `displayName` | `string` | Yes | Display name for internal profile |
| `avatarUrl` | `string` | No | GitHub avatar |
| `roleKeys` | `string[]` | No | Initial roles such as `STUDENT` |

### Example request

```json
{
  "githubUserId": "12345678",
  "githubLogin": "student-dev",
  "primaryEmail": "student@example.com",
  "displayName": "Student Dev",
  "avatarUrl": "https://avatars.githubusercontent.com/u/12345678",
  "roleKeys": ["STUDENT"]
}
```

### Example response

**HTTP 201**

```json
{
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "onboardingRequestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
  "status": "IN_PROGRESS",
  "correlationId": "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77",
  "steps": [
    {
      "type": "IDENTITY_CORRELATION",
      "state": "PENDING"
    },
    {
      "type": "GITHUB_TEAM_PROVISIONING",
      "state": "PENDING"
    },
    {
      "type": "JIRA_GROUP_PROVISIONING",
      "state": "PENDING"
    }
  ]
}
```

### Error handling

- `400` when required fields are missing.
- `409` when a duplicate registration is attempted for an already-active user.
- `503` when the onboarding pipeline cannot enqueue follow-up work.

---

## 2. Get current user

### `GET /api/v1/me`

Returns the current internal user profile, linked external identities, and the active onboarding summary used by the frontend application.

### Path/query/body

- No path parameters
- No request body
- Optional query: `include=identities,onboarding`

### Example request

```http
GET /api/v1/me?include=identities,onboarding
Authorization: Bearer <token>
```

### Example response

**HTTP 200**

```json
{
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "displayName": "Student Dev",
  "primaryEmail": "student@example.com",
  "status": "PENDING_ONBOARDING",
  "roles": ["STUDENT"],
  "github": {
    "userId": "12345678",
    "login": "student-dev",
    "email": "student@example.com"
  },
  "atlassian": {
    "accountId": null,
    "email": null,
    "matchState": "PENDING"
  },
  "onboarding": {
    "requestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
    "state": "IN_PROGRESS"
  }
}
```

### Error handling

- `401` when the bearer token is missing or invalid.
- `404` when the authenticated principal has no matching internal profile.

---

## 3. Get onboarding status

### `GET /api/v1/onboarding/{requestId}`

Returns full onboarding status so the frontend can poll for progress and render pending, failed, or action-required states.

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `requestId` | `string` | Yes | Onboarding request UUID |

### Example request

```http
GET /api/v1/onboarding/4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd
Authorization: Bearer <token>
```

### Example response

**HTTP 200**

```json
{
  "requestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "state": "IN_PROGRESS",
  "correlationId": "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77",
  "startedAt": "2026-05-22T22:05:00Z",
  "steps": [
    {
      "type": "IDENTITY_CORRELATION",
      "state": "SUCCEEDED",
      "startedAt": "2026-05-22T22:05:03Z",
      "completedAt": "2026-05-22T22:05:04Z"
    },
    {
      "type": "GITHUB_TEAM_PROVISIONING",
      "state": "PENDING_EXTERNAL_ACCEPTANCE",
      "target": {
        "provider": "GITHUB",
        "targetType": "GITHUB_TEAM",
        "externalKey": "learners"
      },
      "attemptCount": 1,
      "lastErrorCode": null
    },
    {
      "type": "JIRA_GROUP_PROVISIONING",
      "state": "PROCESSING",
      "target": {
        "provider": "ATLASSIAN",
        "targetType": "ATLASSIAN_GROUP",
        "externalKey": "jira-software-users"
      },
      "attemptCount": 1,
      "lastErrorCode": null
    }
  ]
}
```

### Error handling

- `404` when the onboarding request does not exist.
- `403` when the caller is not allowed to view the request.

---

## 4. Retry onboarding steps

### `POST /api/v1/onboarding/{requestId}/retry`

Requeues failed or manual-review onboarding steps and returns the updated request state.

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `requestId` | `string` | Yes | Onboarding request UUID |

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `steps` | `string[]` | Yes | Step keys to retry |
| `reason` | `string` | No | Optional support note |

### Example request

```json
{
  "steps": ["JIRA_GROUP_PROVISIONING"],
  "reason": "Atlassian group sync recovered"
}
```

### Example response

**HTTP 202**

```json
{
  "requestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
  "state": "IN_PROGRESS",
  "requeuedSteps": ["JIRA_GROUP_PROVISIONING"],
  "correlationId": "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77"
}
```

### Error handling

- `400` when no steps are provided.
- `409` when a requested step is not retryable because it is already complete or still running.
- `404` when the onboarding request does not exist.

---

## 5. Get identity correlation status

### `GET /api/v1/users/{userId}/identity-links`

Returns the identity-correlation result between GitHub and Atlassian accounts for a user. This is important because Jira-linked GitHub user attribution may depend on successful identity matching.

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `userId` | `string` | Yes | Internal user UUID |

### Example response

**HTTP 200**

```json
{
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "githubIdentity": {
    "identityId": "3f33b281-a7e2-4f67-9f76-6af3f3d067dd",
    "login": "student-dev",
    "email": "student@example.com"
  },
  "atlassianIdentity": {
    "identityId": "7dc1b6cb-a8a2-4a2c-bc8d-d08740d4da17",
    "accountId": "557058:abcd-1234",
    "email": "student@example.com"
  },
  "matchStrategy": "EMAIL_EXACT",
  "confidenceScore": 1.0,
  "verifiedAt": "2026-05-22T22:05:04Z"
}
```

### Error handling

- `404` when no correlation record exists.
- `422` when the user has identities but no valid correlation can be established.

---

## 6. List provider targets

### `GET /api/v1/admin/provider-targets`

Returns configured external provisioning targets such as GitHub teams and Atlassian groups.

### Query parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `provider` | `string` | No | `GITHUB` or `ATLASSIAN` |
| `enabled` | `boolean` | No | Filter active targets |
| `targetType` | `string` | No | `GITHUB_TEAM` or `ATLASSIAN_GROUP` |

### Example response

**HTTP 200**

```json
{
  "items": [
    {
      "providerTargetId": "d304b901-3a2f-46d5-8a71-938f8792f39f",
      "provider": "GITHUB",
      "targetType": "GITHUB_TEAM",
      "externalKey": "learners",
      "displayName": "Learners Team",
      "enabled": true
    },
    {
      "providerTargetId": "80bb3896-0c97-468e-8c10-807d8b66ec17",
      "provider": "ATLASSIAN",
      "targetType": "ATLASSIAN_GROUP",
      "externalKey": "jira-software-users",
      "displayName": "Jira Software Users",
      "enabled": true
    }
  ]
}
```

### Error handling

- `403` when a non-admin user calls the endpoint.

---

## 7. Create group mapping rule

### `POST /api/v1/admin/group-mappings`

Creates a mapping from an internal role or segment to an external GitHub team or Atlassian group.

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `roleKey` | `string` | Yes | Internal role |
| `providerTargetId` | `string` | Yes | External target UUID |
| `cohortKey` | `string` | No | Optional cohort-specific filter |
| `priorityOrder` | `integer` | No | Rule precedence |
| `enabled` | `boolean` | No | Defaults to true |

### Example request

```json
{
  "roleKey": "STUDENT",
  "providerTargetId": "d304b901-3a2f-46d5-8a71-938f8792f39f",
  "cohortKey": "cohort-2026-summer",
  "priorityOrder": 10,
  "enabled": true
}
```

### Example response

**HTTP 201**

```json
{
  "mappingId": "099916da-7b9a-4d49-bf29-d9eb97d7033b",
  "roleKey": "STUDENT",
  "providerTargetId": "d304b901-3a2f-46d5-8a71-938f8792f39f",
  "cohortKey": "cohort-2026-summer",
  "priorityOrder": 10,
  "enabled": true
}
```

### Error handling

- `400` when role or target is invalid.
- `409` when the same mapping rule already exists.
- `403` when the caller lacks admin access.

---

## 8. List onboarding requests for support

### `GET /api/v1/admin/onboarding`

Lists onboarding requests filtered by state for support, admin review, and operational dashboards.
### Query parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `state` | `string` | No | `FAILED`, `IN_PROGRESS`, `PARTIAL_SUCCESS`, `ACTION_REQUIRED` |
| `userId` | `string` | No | Filter by internal user |
| `correlationId` | `string` | No | Filter by trace ID |
| `page` | `integer` | No | Default `0` |
| `size` | `integer` | No | Default `20` |

### Example response

**HTTP 200**

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "items": [
    {
      "requestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
      "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
      "state": "ACTION_REQUIRED",
      "correlationId": "cb3f9c3a-9f38-47d4-8fa4-2d9a6a6d8b77",
      "failedSteps": ["IDENTITY_CORRELATION"],
      "startedAt": "2026-05-22T22:05:00Z"
    }
  ]
}
```

### Error handling

- `403` when the caller lacks support or admin access.

---

## 9. Get onboarding audit trail

### `GET /api/v1/admin/onboarding/{requestId}/audit`

Returns provider-call history for debugging retries, pending states, and downstream provisioning failures.

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `requestId` | `string` | Yes | Onboarding request UUID |

### Example response

**HTTP 200**

```json
{
  "requestId": "4168d9ca-e6dc-4e17-8d8a-d38b5487f2dd",
  "entries": [
    {
      "provider": "GITHUB",
      "action": "ADD_TEAM_MEMBER",
      "resultState": "PENDING",
      "createdAt": "2026-05-22T22:05:05Z",
      "responseSummary": {
        "team": "learners",
        "membershipState": "pending"
      }
    },
    {
      "provider": "ATLASSIAN",
      "action": "ADD_GROUP_MEMBER",
      "resultState": "SUCCESS",
      "createdAt": "2026-05-22T22:05:07Z",
      "responseSummary": {
        "group": "jira-software-users"
      }
    }
  ]
}
```

### Error handling

- `404` when the onboarding request does not exist.
- `403` when the caller lacks admin access.

---

## 10. Manually resolve identity correlation

### `POST /api/v1/admin/users/{userId}/identity-links`

Creates or overrides the GitHub-to-Atlassian correlation record when automatic matching fails.

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `userId` | `string` | Yes | Internal user UUID |

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `githubIdentityId` | `string` | Yes | GitHub external identity UUID |
| `atlassianIdentityId` | `string` | Yes | Atlassian external identity UUID |
| `matchStrategy` | `string` | Yes | Usually `MANUAL_ADMIN_CONFIRMATION` |
| `confidenceScore` | `number` | No | Optional support-entered confidence |

### Example request

```json
{
  "githubIdentityId": "3f33b281-a7e2-4f67-9f76-6af3f3d067dd",
  "atlassianIdentityId": "7dc1b6cb-a8a2-4a2c-bc8d-d08740d4da17",
  "matchStrategy": "MANUAL_ADMIN_CONFIRMATION",
  "confidenceScore": 1.0
}
```

### Example response

**HTTP 201**

```json
{
  "identityLinkId": "8f4e654d-f641-40cf-b615-d2ef45df67a4",
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "matchStrategy": "MANUAL_ADMIN_CONFIRMATION",
  "verifiedAt": "2026-05-22T22:11:00Z"
}
```

### Error handling

- `400` when supplied identities belong to different users or providers.
- `404` when the user or identities do not exist.
- `409` when an active correlation already exists and override is not allowed.

---

## 11. Reprovision user access

### `POST /api/v1/admin/users/{userId}/reprovision`

Creates a new onboarding request for reprovisioning access after role changes, provider recovery, or support intervention.

### Path parameters

| Name | Type | Required | Notes |
|---|---|---|---|
| `userId` | `string` | Yes | Internal user UUID |

### Request body

| Field | Type | Required | Notes |
|---|---|---|---|
| `reason` | `string` | Yes | Why reprovisioning is needed |
| `includeIdentityCorrelation` | `boolean` | No | Whether to rerun correlation |
| `steps` | `string[]` | No | Optional specific steps |

### Example request

```json
{
  "reason": "Student moved into instructor cohort",
  "includeIdentityCorrelation": false,
  "steps": [
    "GITHUB_TEAM_PROVISIONING",
    "JIRA_GROUP_PROVISIONING"
  ]
}
```

### Example response

**HTTP 202**

```json
{
  "userId": "5dc487e4-f7e0-4f68-9cf6-4f58ef03f4c8",
  "onboardingRequestId": "4b5eabde-c5ba-49f4-8398-0fbde6ee0246",
  "state": "IN_PROGRESS",
  "source": "REPROVISION"
}
```

### Error handling

- `404` when the user does not exist.
- `409` when reprovisioning is already in progress.
- `403` when the caller is not an admin.

---

## 12. Health-style provider check

### `GET /api/v1/admin/provider-health`

Returns summarized provider integration status for GitHub and Atlassian dependencies used during onboarding.

### Example response

**HTTP 200**

```json
{
  "providers": [
    {
      "provider": "GITHUB",
      "status": "UP",
      "notes": "Team membership API reachable"
    },
    {
      "provider": "ATLASSIAN",
      "status": "DEGRADED",
      "notes": "Provisioning latency elevated"
    }
  ]
}
```

### Error handling

- `403` when the caller lacks admin access.

## Workflow coverage summary

| Workflow | Endpoints |
|---|---|
| Registration after GitHub auth | `POST /registrations`, `GET /me`, `GET /onboarding/{requestId}` |
| Frontend onboarding polling | `GET /onboarding/{requestId}` |
| Identity-correlation inspection | `GET /users/{userId}/identity-links` |
| Admin/manual identity repair | `POST /admin/users/{userId}/identity-links` |
| Support retry flow | `POST /onboarding/{requestId}/retry`, `GET /admin/onboarding/{requestId}/audit` |
| Role-to-target configuration | `GET /admin/provider-targets`, `POST /admin/group-mappings` |
| Reprovisioning after changes | `POST /admin/users/{userId}/reprovision` |
