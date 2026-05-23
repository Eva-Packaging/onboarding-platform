-- Fixed seed UUIDs — referenced cross-service in provisioning-service V2 seed migration.
-- external_provider: GITHUB    = a0000000-0000-0000-0000-000000000001
--                   ATLASSIAN = a0000000-0000-0000-0000-000000000002
-- app_role:          STUDENT    = b0000000-0000-0000-0000-000000000001
--                   INSTRUCTOR = b0000000-0000-0000-0000-000000000002
--                   ADMIN      = b0000000-0000-0000-0000-000000000003

insert into external_provider (id, provider_key, display_name, created_at) values
    ('a0000000-0000-0000-0000-000000000001', 'GITHUB',    'GitHub',    now()),
    ('a0000000-0000-0000-0000-000000000002', 'ATLASSIAN', 'Atlassian', now())
on conflict (provider_key) do nothing;

insert into app_role (id, role_key, display_name, created_at) values
    ('b0000000-0000-0000-0000-000000000001', 'STUDENT',    'Student',    now()),
    ('b0000000-0000-0000-0000-000000000002', 'INSTRUCTOR', 'Instructor', now()),
    ('b0000000-0000-0000-0000-000000000003', 'ADMIN',      'Admin',      now())
on conflict (role_key) do nothing;