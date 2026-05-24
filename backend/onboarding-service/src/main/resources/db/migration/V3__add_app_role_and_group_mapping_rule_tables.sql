create table app_role (
    id           uuid         primary key,
    role_key     varchar(100) not null unique,
    display_name varchar(100) not null,
    created_at   timestamptz  not null default now()
);

insert into app_role (id, role_key, display_name) values
    ('00000000-0000-0000-0000-000000000001', 'STUDENT',    'Student'),
    ('00000000-0000-0000-0000-000000000002', 'INSTRUCTOR',  'Instructor'),
    ('00000000-0000-0000-0000-000000000003', 'ADMIN',       'Administrator');

create table group_mapping_rule (
    id                 uuid        primary key,
    app_role_id        uuid        not null references app_role(id),
    provider_target_id uuid        not null references provider_target(id),
    cohort_key         varchar(100),
    priority_order     integer     not null default 0,
    enabled            boolean     not null default true,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create index idx_group_mapping_rule_role on group_mapping_rule (app_role_id);
