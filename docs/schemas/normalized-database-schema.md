# Normalized Database Schema

This schema models a GitHub-centered onboarding and access provisioning system for an education platform. It supports internal user registration, GitHub and Atlassian identity tracking, onboarding workflows, GitHub team assignment, Jira group provisioning, retryable async processing, and audit history.

## Design goals

The schema is normalized around distinct business concepts so identity data, onboarding state, provider configuration, and audit history do not get duplicated across tables. This is important because GitHub team membership can remain pending until an invitation is accepted, Atlassian provisioning is group-oriented through SCIM-based administration, and GitHub-to-Jira attribution may depend on matching user identity data such as email.

## Relationship overview

- One `user_profile` has one or many external identities over time.
- One `user_profile` can have many onboarding requests.
- One `onboarding_request` has many onboarding steps.
- One `onboarding_step` has many provisioning audit log entries.
- One `external_provider` has many `provider_target` rows.
- One `provider_target` can be referenced by many onboarding steps.
- One `user_profile` can be linked to zero or one active Atlassian identity for Jira correlation in the initial model.
- One `user_profile` can map to many role assignments over time, while one role can map to many users, so role membership is modeled as a many-to-many bridge.

## Tables

### 1. `user_profile`

Stores the internal application user record.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Internal user identifier |
| `display_name` | VARCHAR(255) | NOT NULL | User-facing name |
| `primary_email` | VARCHAR(320) | NOT NULL | Canonical internal email |
| `status` | VARCHAR(50) | NOT NULL | `PENDING_ONBOARDING`, `ACTIVE`, `ACTION_REQUIRED`, `SUSPENDED` |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

**Relationships**
- One-to-many with `external_identity`
- One-to-many with `onboarding_request`
- Many-to-many with `app_role` through `user_role_assignment`

## 2. `external_provider`

Defines supported external systems.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Provider identifier |
| `provider_key` | VARCHAR(50) | UNIQUE, NOT NULL | `GITHUB`, `ATLASSIAN` |
| `display_name` | VARCHAR(100) | NOT NULL | Human-readable provider name |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |

**Relationships**
- One-to-many with `external_identity`
- One-to-many with `provider_target`

## 3. `external_identity`

Stores provider-specific identity records without duplicating internal user data.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Identity row identifier |
| `user_profile_id` | UUID | FK -> `user_profile.id`, NOT NULL | Owning internal user |
| `provider_id` | UUID | FK -> `external_provider.id`, NOT NULL | Identity provider |
| `external_user_id` | VARCHAR(255) | NOT NULL | Provider user ID |
| `username` | VARCHAR(255) | NULL | GitHub login or Atlassian username-like value |
| `email` | VARCHAR(320) | NULL | Provider email |
| `display_name` | VARCHAR(255) | NULL | Provider display name |
| `avatar_url` | TEXT | NULL | Avatar image URL |
| `profile_json` | JSONB | NULL | Raw profile snapshot |
| `is_primary` | BOOLEAN | NOT NULL DEFAULT false | Primary identity for provider |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

**Suggested unique constraints**
- `UNIQUE(provider_id, external_user_id)`
- Optional `UNIQUE(provider_id, username)` where appropriate

**Relationships**
- Many-to-one with `user_profile`
- Many-to-one with `external_provider`
- One-to-many from `external_identity` to `identity_link` as source or target depending on provider role

## 4. `identity_link`

Represents correlation between a GitHub identity and an Atlassian identity used for Jira visibility and developer attribution.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Correlation identifier |
| `user_profile_id` | UUID | FK -> `user_profile.id`, NOT NULL | Internal user |
| `github_identity_id` | UUID | FK -> `external_identity.id`, NOT NULL | GitHub identity |
| `atlassian_identity_id` | UUID | FK -> `external_identity.id`, NOT NULL | Atlassian identity |
| `match_strategy` | VARCHAR(50) | NOT NULL | `EMAIL_EXACT`, `EMAIL_ALIAS_NORMALIZED`, `MANUAL_ADMIN_CONFIRMATION`, `UNVERIFIED` |
| `confidence_score` | NUMERIC(5,2) | NULL | Optional match score |
| `verified_at` | TIMESTAMPTZ | NULL | Time of confirmation |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |

**Relationships**
- Many-to-one with `user_profile`
- Many-to-one with `external_identity` for GitHub identity
- Many-to-one with `external_identity` for Atlassian identity

## 5. `app_role`

Defines internal platform roles.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Role identifier |
| `role_key` | VARCHAR(100) | UNIQUE, NOT NULL | `STUDENT`, `INSTRUCTOR`, `ADMIN` |
| `display_name` | VARCHAR(100) | NOT NULL | Role label |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |

**Relationships**
- Many-to-many with `user_profile` through `user_role_assignment`
- One-to-many with `group_mapping_rule`

## 6. `user_role_assignment`

Bridge table for many-to-many internal role assignments.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `user_profile_id` | UUID | PK, FK -> `user_profile.id` | Internal user |
| `app_role_id` | UUID | PK, FK -> `app_role.id` | Assigned role |
| `assigned_at` | TIMESTAMPTZ | NOT NULL | Assignment timestamp |

**Relationships**
- Many-to-one with `user_profile`
- Many-to-one with `app_role`

## 7. `provider_target`

Defines GitHub teams, Jira groups, or other external provisioning targets.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Target identifier |
| `provider_id` | UUID | FK -> `external_provider.id`, NOT NULL | Owning provider |
| `target_type` | VARCHAR(50) | NOT NULL | `GITHUB_TEAM`, `ATLASSIAN_GROUP` |
| `external_key` | VARCHAR(255) | NOT NULL | Team slug, group name, or external target key |
| `display_name` | VARCHAR(255) | NOT NULL | Human-readable name |
| `metadata_json` | JSONB | NULL | Org, site, app-access, or admin metadata |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true | Active/inactive flag |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

**Suggested unique constraint**
- `UNIQUE(provider_id, target_type, external_key)`

**Relationships**
- Many-to-one with `external_provider`
- One-to-many with `group_mapping_rule`
- One-to-many with `onboarding_step`

## 8. `group_mapping_rule`

Maps internal roles or cohort-like dimensions to external targets.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Mapping rule identifier |
| `app_role_id` | UUID | FK -> `app_role.id`, NOT NULL | Internal role |
| `provider_target_id` | UUID | FK -> `provider_target.id`, NOT NULL | External target |
| `cohort_key` | VARCHAR(100) | NULL | Optional cohort or segment key |
| `priority_order` | INTEGER | NOT NULL DEFAULT 0 | Rule precedence |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true | Active/inactive flag |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

**Relationships**
- Many-to-one with `app_role`
- Many-to-one with `provider_target`

## 9. `onboarding_request`

Top-level lifecycle record for each registration or reprovisioning attempt.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Onboarding request identifier |
| `user_profile_id` | UUID | FK -> `user_profile.id`, NOT NULL | Internal user |
| `state` | VARCHAR(50) | NOT NULL | `REQUESTED`, `IN_PROGRESS`, `COMPLETED`, `PARTIAL_SUCCESS`, `FAILED` |
| `source` | VARCHAR(50) | NOT NULL | `SELF_REGISTRATION`, `ADMIN_RETRY`, `REPROVISION` |
| `correlation_id` | UUID | NOT NULL | Cross-service trace ID |
| `started_at` | TIMESTAMPTZ | NOT NULL | Start time |
| `completed_at` | TIMESTAMPTZ | NULL | Completion time |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

**Relationships**
- Many-to-one with `user_profile`
- One-to-many with `onboarding_step`

## 10. `onboarding_step_type`

Reference table for step kinds.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | SMALLINT | PK | Step type identifier |
| `step_key` | VARCHAR(100) | UNIQUE, NOT NULL | `IDENTITY_CORRELATION`, `GITHUB_TEAM_PROVISIONING`, `JIRA_GROUP_PROVISIONING` |
| `description` | VARCHAR(255) | NOT NULL | Human-readable description |

**Relationships**
- One-to-many with `onboarding_step`

## 11. `onboarding_step`

Individual units of onboarding work.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Step identifier |
| `onboarding_request_id` | UUID | FK -> `onboarding_request.id`, NOT NULL | Parent onboarding request |
| `step_type_id` | SMALLINT | FK -> `onboarding_step_type.id`, NOT NULL | Step type |
| `provider_target_id` | UUID | FK -> `provider_target.id`, NULL | External target when applicable |
| `state` | VARCHAR(50) | NOT NULL | `PENDING`, `PROCESSING`, `SUCCEEDED`, `PENDING_EXTERNAL_ACCEPTANCE`, `FAILED`, `MANUAL_REVIEW` |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0 | Retry count |
| `last_error_code` | VARCHAR(100) | NULL | Last error code |
| `last_error_message` | TEXT | NULL | Last error message |
| `started_at` | TIMESTAMPTZ | NULL | Step start |
| `completed_at` | TIMESTAMPTZ | NULL | Step end |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last update timestamp |

**Relationships**
- Many-to-one with `onboarding_request`
- Many-to-one with `onboarding_step_type`
- Many-to-one with `provider_target`
- One-to-many with `provisioning_audit_log`

## 12. `provisioning_audit_log`

Stores provider interaction history for retries, debugging, and compliance.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Audit identifier |
| `onboarding_step_id` | UUID | FK -> `onboarding_step.id`, NOT NULL | Step being audited |
| `provider_id` | UUID | FK -> `external_provider.id`, NOT NULL | Provider used |
| `action_name` | VARCHAR(100) | NOT NULL | `ADD_TEAM_MEMBER`, `ADD_GROUP_MEMBER`, `LOOKUP_USER` |
| `request_payload` | JSONB | NULL | Sanitized outbound request |
| `response_payload` | JSONB | NULL | Sanitized provider response |
| `result_state` | VARCHAR(50) | NOT NULL | `SUCCESS`, `PENDING`, `FAILURE` |
| `correlation_id` | UUID | NOT NULL | Trace ID |
| `created_at` | TIMESTAMPTZ | NOT NULL | Audit timestamp |

**Relationships**
- Many-to-one with `onboarding_step`
- Many-to-one with `external_provider`

## 13. `outbox_event`

Supports transactional event publication for Kafka.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Event identifier |
| `aggregate_type` | VARCHAR(100) | NOT NULL | Aggregate root type |
| `aggregate_id` | UUID | NOT NULL | Aggregate root ID |
| `event_type` | VARCHAR(150) | NOT NULL | Domain event type |
| `payload` | JSONB | NOT NULL | Event payload |
| `published` | BOOLEAN | NOT NULL DEFAULT false | Publication flag |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation timestamp |
| `published_at` | TIMESTAMPTZ | NULL | Publish timestamp |

## Cardinality summary

| Parent | Child | Relationship | Why |
|---|---|---|---|
| `user_profile` | `external_identity` | One-to-many | A user can have GitHub and Atlassian identities, plus future providers. |
| `user_profile` | `onboarding_request` | One-to-many | A user may be reprovisioned later. |
| `user_profile` | `app_role` | Many-to-many via `user_role_assignment` | Users can hold multiple roles; roles apply to many users. |
| `external_provider` | `provider_target` | One-to-many | Each provider owns multiple teams or groups. |
| `app_role` | `provider_target` | Many-to-many via `group_mapping_rule` | A role may provision several targets; a target may be reused across rules. |
| `onboarding_request` | `onboarding_step` | One-to-many | Each request expands into multiple tracked tasks. |
| `onboarding_step` | `provisioning_audit_log` | One-to-many | A step may require multiple provider calls and retries. |

## Sample DDL

```sql
create table user_profile (
  id uuid primary key,
  display_name varchar(255) not null,
  primary_email varchar(320) not null,
  status varchar(50) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table external_provider (
  id uuid primary key,
  provider_key varchar(50) not null unique,
  display_name varchar(100) not null,
  created_at timestamptz not null default now()
);

create table external_identity (
  id uuid primary key,
  user_profile_id uuid not null references user_profile(id),
  provider_id uuid not null references external_provider(id),
  external_user_id varchar(255) not null,
  username varchar(255),
  email varchar(320),
  display_name varchar(255),
  avatar_url text,
  profile_json jsonb,
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (provider_id, external_user_id)
);

create table onboarding_request (
  id uuid primary key,
  user_profile_id uuid not null references user_profile(id),
  state varchar(50) not null,
  source varchar(50) not null,
  correlation_id uuid not null,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table onboarding_step (
  id uuid primary key,
  onboarding_request_id uuid not null references onboarding_request(id),
  step_type_id smallint not null,
  provider_target_id uuid,
  state varchar(50) not null,
  attempt_count integer not null default 0,
  last_error_code varchar(100),
  last_error_message text,
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

## How the schema supports project features

### 1. GitHub-first registration

`user_profile` stores the internal user, while `external_identity` stores the GitHub identity without coupling application data to provider-specific fields. This supports GitHub-first onboarding while keeping room for future providers.

### 2. Jira identity correlation

`identity_link` explicitly tracks the relationship between GitHub and Atlassian identities. That supports the requirement to improve Jira attribution for GitHub users when matching identity data is needed.

### 3. Role-based provisioning

`app_role`, `user_role_assignment`, `provider_target`, and `group_mapping_rule` let the system map business roles such as student or instructor to GitHub teams and Jira groups without hard-coding those rules into user rows. This keeps the design normalized and extensible.

### 4. Async onboarding and retries

`onboarding_request`, `onboarding_step`, `provisioning_audit_log`, and `outbox_event` together support long-running workflows, Kafka publishing, external retries, and support visibility. This is important because GitHub memberships may remain pending and Atlassian provisioning may complete separately from the initial registration request.

### 5. Support and troubleshooting

`provisioning_audit_log` preserves each external call and result, so admins can inspect failures, retries, and pending states. That is especially useful when a GitHub invitation is not yet accepted or a Jira account cannot be matched.
