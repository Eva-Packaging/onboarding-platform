create table onboarding_step_type (
    id smallint primary key,
    step_key varchar(100) not null unique,
    description varchar(255) not null
);

insert into onboarding_step_type (id, step_key, description) values
    (1, 'IDENTITY_CORRELATION', 'Correlate GitHub and Atlassian identities for a user'),
    (2, 'GITHUB_TEAM_PROVISIONING', 'Add user to the appropriate GitHub team'),
    (3, 'JIRA_GROUP_PROVISIONING', 'Add user to the appropriate Jira group');

create table onboarding_request (
    id uuid primary key,
    user_profile_id uuid not null,
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
    step_type_id smallint not null references onboarding_step_type(id),
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

create index idx_onboarding_request_user on onboarding_request (user_profile_id);
create index idx_onboarding_step_request on onboarding_step (onboarding_request_id);
create index idx_outbox_event_unpublished on outbox_event (created_at) where published = false;