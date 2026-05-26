-- user_profile_id was typed as uuid, but the JWT user claim carries a
-- numeric GitHub ID (e.g. 123214008) which is not a valid UUID.
-- Cast existing rows to text so no data is lost.

alter table onboarding_request
    alter column user_profile_id type varchar(255)
        using user_profile_id::text;