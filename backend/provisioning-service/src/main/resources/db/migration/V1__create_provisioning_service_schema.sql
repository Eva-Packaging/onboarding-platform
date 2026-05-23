create table provider_target (
    id uuid primary key,
    provider_id uuid not null,
    target_type varchar(50) not null,
    external_key varchar(255) not null,
    display_name varchar(255) not null,
    metadata_json jsonb,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_provider_target_provider_type_key unique (provider_id, target_type, external_key)
);

create table group_mapping_rule (
    id uuid primary key,
    app_role_id uuid not null,
    provider_target_id uuid not null references provider_target(id),
    cohort_key varchar(100),
    priority_order integer not null default 0,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table provisioning_audit_log (
    id uuid primary key,
    onboarding_step_id uuid not null,
    provider_id uuid not null,
    action_name varchar(100) not null,
    request_payload jsonb,
    response_payload jsonb,
    result_state varchar(50) not null,
    correlation_id uuid not null,
    created_at timestamptz not null default now()
);

create table outbox_event (
    id uuid primary key,
    aggregate_type varchar(100) not null,
    aggregate_id uuid not null,
    event_type varchar(150) not null,
    payload jsonb not null,
    published boolean not null default false,
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create index idx_provider_target_type_enabled on provider_target (target_type) where enabled = true;
create index idx_group_mapping_rule_target on group_mapping_rule (provider_target_id);
create index idx_audit_log_step on provisioning_audit_log (onboarding_step_id);
create index idx_outbox_event_unpublished on outbox_event (created_at) where published = false;