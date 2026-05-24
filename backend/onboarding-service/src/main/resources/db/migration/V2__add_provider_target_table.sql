create table provider_target (
    id           uuid         primary key,
    provider_id  uuid         not null,
    target_type  varchar(50)  not null,
    external_key varchar(255) not null,
    display_name varchar(255) not null,
    enabled      boolean      not null default true,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    unique (provider_id, target_type, external_key)
);

create index idx_provider_target_provider on provider_target (provider_id);