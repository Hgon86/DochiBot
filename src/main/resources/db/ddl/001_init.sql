create extension if not exists pgcrypto;

do $$ begin
  create type user_role as enum ('ADMIN', 'USER');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type document_source_type as enum ('PDF', 'TEXT');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type document_status as enum ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type ingestion_job_status as enum ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED');
exception when duplicate_object then null;
end $$;

do $$ begin
  create type chat_role as enum ('USER', 'ASSISTANT');
exception when duplicate_object then null;
end $$;

create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  username varchar(64) not null,
  password_hash varchar(100) not null,
  role user_role not null default 'USER',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists ux_users_username on users (username);

create table if not exists documents (
  id uuid primary key default gen_random_uuid(),
  title varchar(255) not null,
  source_type document_source_type not null,
  original_filename varchar(512) null,
  storage_uri text null,
  content_sha256 char(64) not null,
  status document_status not null default 'PENDING',
  error_message text null,
  created_by_user_id uuid null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint fk_documents_created_by_user
    foreign key (created_by_user_id)
    references users(id)
);

create unique index if not exists ux_documents_content_sha256 on documents (content_sha256);
create index if not exists ix_documents_status on documents (status);

create table if not exists document_ingestion_jobs (
  id uuid primary key default gen_random_uuid(),
  document_id uuid not null,
  status ingestion_job_status not null default 'QUEUED',
  chunk_count int null,
  embedding_model varchar(128) null,
  es_index_name varchar(255) not null default 'dochi_docs_v1',
  started_at timestamptz null,
  finished_at timestamptz null,
  error_message text null,
  created_at timestamptz not null default now(),
  constraint fk_document_ingestion_jobs_document
    foreign key (document_id)
    references documents(id)
    on delete cascade
);

create index if not exists ix_document_ingestion_jobs_document_id on document_ingestion_jobs (document_id);
create index if not exists ix_document_ingestion_jobs_status on document_ingestion_jobs (status);

create table if not exists chat_sessions (
  id uuid primary key default gen_random_uuid(),
  external_session_key varchar(128) not null,
  owner_user_id uuid null,
  created_at timestamptz not null default now(),
  constraint fk_chat_sessions_owner_user
    foreign key (owner_user_id)
    references users(id)
);

create unique index if not exists ux_chat_sessions_external_session_key on chat_sessions (external_session_key);

create table if not exists chat_messages (
  id uuid primary key default gen_random_uuid(),
  chat_session_id uuid not null,
  role chat_role not null,
  content text not null,
  citations_json jsonb null,
  created_at timestamptz not null default now(),
  constraint fk_chat_messages_chat_session
    foreign key (chat_session_id)
    references chat_sessions(id)
    on delete cascade
);

create index if not exists ix_chat_messages_session_created_at on chat_messages (chat_session_id, created_at);
