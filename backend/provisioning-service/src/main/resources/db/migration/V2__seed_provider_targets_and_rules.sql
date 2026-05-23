-- Shared seed UUID constants (cross-service references from user-service V2 seed)
-- external_provider: GITHUB    = a0000000-0000-0000-0000-000000000001
--                   ATLASSIAN = a0000000-0000-0000-0000-000000000002
-- app_role:          STUDENT    = b0000000-0000-0000-0000-000000000001
--                   INSTRUCTOR = b0000000-0000-0000-0000-000000000002
--                   ADMIN      = b0000000-0000-0000-0000-000000000003
--
-- provider_target:  GITHUB_TEAM    = c0000000-0000-0000-0000-000000000001
--                   ATLASSIAN_GROUP = c0000000-0000-0000-0000-000000000002

insert into provider_target (id, provider_id, target_type, external_key, display_name, enabled, created_at, updated_at)
values
    ('c0000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-000000000001',
     'GITHUB_TEAM',
     'evaitcs-org/students',
     'EVAITCS Students GitHub Team',
     true,
     now(), now()),
    ('c0000000-0000-0000-0000-000000000002',
     'a0000000-0000-0000-0000-000000000002',
     'ATLASSIAN_GROUP',
     'jira-students',
     'EVAITCS Students Jira Group',
     true,
     now(), now())
on conflict (id) do nothing;

insert into group_mapping_rule (id, app_role_id, provider_target_id, priority_order, enabled, created_at, updated_at)
values
    -- STUDENT -> GitHub team
    ('d0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000001',
     'c0000000-0000-0000-0000-000000000001',
     1, true, now(), now()),
    -- INSTRUCTOR -> GitHub team
    ('d0000000-0000-0000-0000-000000000002',
     'b0000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000001',
     1, true, now(), now()),
    -- STUDENT -> Atlassian group
    ('d0000000-0000-0000-0000-000000000003',
     'b0000000-0000-0000-0000-000000000001',
     'c0000000-0000-0000-0000-000000000002',
     1, true, now(), now()),
    -- INSTRUCTOR -> Atlassian group
    ('d0000000-0000-0000-0000-000000000004',
     'b0000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000002',
     1, true, now(), now())
on conflict (id) do nothing;
