-- h2 doesn't have a JSON type, but for integration/unit tests we can simulate one as a TEXT type
CREATE DOMAIN IF NOT EXISTS json AS TEXT;

-- and we have to go all in and take over schema creation from Hibernate
CREATE TABLE event_engine_task
(
  id          varchar(255) not null,
  measurement varchar(255) not null,
  scenario    json         not null,
  task_id     varchar(255) not null,
  tenant_id   varchar(255) not null,
  primary key (id)
);