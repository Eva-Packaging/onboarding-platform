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
    constraint uq_external_identity_provider_user unique (provider_id, external_user_id)
);

create table app_role (
    id uuid primary key,
    role_key varchar(100) not null unique,
    display_name varchar(100) not null,
    created_at timestamptz not null default now()
);

create table user_role_assignment (
    user_profile_id uuid not null references user_profile(id),
    app_role_id uuid not null references app_role(id),
    assigned_at timestamptz not null default now(),
    primary key (user_profile_id, app_role_id)
);

create table identity_link (
    id uuid primary key,
    user_profile_id uuid not null references user_profile(id),
    github_identity_id uuid not null references external_identity(id),
    atlassian_identity_id uuid not null references external_identity(id),
    match_strategy varchar(50) not null,
    confidence_score numeric(5, 2),
    verified_at timestamptz,
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

create index idx_outbox_event_unpublished on outbox_event (created_at) where published = false;
create index idx_external_identity_user on external_identity (user_profile_id);
create index idx_identity_link_user on identity_link (user_profile_id);