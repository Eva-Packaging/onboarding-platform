alter table outbox_event add column if not exists topic varchar(200);
alter table outbox_event add column if not exists correlation_id varchar(128);
