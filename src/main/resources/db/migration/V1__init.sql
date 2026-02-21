-- DochiBot initial schema (PostgreSQL + pgvector + FTS)

-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;

-- users
CREATE TABLE users (
  id uuid PRIMARY KEY,
  username varchar(64) NOT NULL,
  password_hash varchar(100) NULL,
  role varchar(16) NOT NULL DEFAULT 'USER',
  provider varchar(16) NOT NULL DEFAULT 'CREDENTIALS',
  provider_id varchar(128) NULL,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_username ON users (username);
CREATE UNIQUE INDEX ux_users_provider_provider_id ON users (provider, provider_id) WHERE provider_id IS NOT NULL;

-- documents
CREATE TABLE documents (
  id uuid PRIMARY KEY,
  title varchar(255) NOT NULL,
  source_type varchar(16) NOT NULL,
  original_filename varchar(512) NULL,
  storage_uri text NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  error_message text NULL,
  created_by_user_id uuid NULL,
  language varchar(16) NOT NULL DEFAULT 'UNKNOWN',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_documents_created_by_user
    FOREIGN KEY (created_by_user_id)
    REFERENCES users(id)
);

CREATE INDEX ix_documents_status ON documents (status);

-- document_ingestion_jobs
CREATE TABLE document_ingestion_jobs (
  id uuid PRIMARY KEY,
  document_id uuid NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'QUEUED',
  chunk_count int NULL,
  embedding_model varchar(128) NULL,
  embedding_dims int NULL,
  attempt_count int NOT NULL DEFAULT 0,
  max_attempts int NOT NULL DEFAULT 3,
  next_run_at timestamptz NULL,
  started_at timestamptz NULL,
  finished_at timestamptz NULL,
  error_message text NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_document_ingestion_jobs_document
    FOREIGN KEY (document_id)
    REFERENCES documents(id)
    ON DELETE CASCADE
);

CREATE INDEX ix_document_ingestion_jobs_document_id ON document_ingestion_jobs (document_id);
CREATE INDEX ix_document_ingestion_jobs_status ON document_ingestion_jobs (status);
CREATE INDEX ix_document_ingestion_jobs_next_run_at ON document_ingestion_jobs (next_run_at);

-- sections (gate)
CREATE TABLE sections (
  id uuid PRIMARY KEY,
  document_id uuid NOT NULL,
  parent_id uuid NULL,
  level int NOT NULL,
  heading text NOT NULL,
  section_path text NOT NULL,
  start_offset int NULL,
  end_offset int NULL,
  summary text NULL,
  section_text text NULL,
  section_tsv tsvector GENERATED ALWAYS AS (
    to_tsvector('simple', coalesce(heading, '') || ' ' || coalesce(summary, '') || ' ' || coalesce(section_text, ''))
  ) STORED,
  section_embedding vector(1024) NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_sections_document
    FOREIGN KEY (document_id)
    REFERENCES documents(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_sections_parent
    FOREIGN KEY (parent_id)
    REFERENCES sections(id)
    ON DELETE CASCADE
);

CREATE INDEX ix_sections_document_id ON sections (document_id);
CREATE INDEX ix_sections_parent_id ON sections (parent_id);
CREATE INDEX ix_sections_tsv ON sections USING GIN (section_tsv);
CREATE INDEX ix_sections_embedding_hnsw ON sections USING hnsw (section_embedding vector_cosine_ops);

-- chunks (context)
CREATE TABLE chunks (
  id uuid PRIMARY KEY,
  document_id uuid NOT NULL,
  section_id uuid NULL,
  chunk_index int NOT NULL,
  text text NOT NULL,
  page int NULL,
  start_offset int NULL,
  end_offset int NULL,
  chunk_tsv tsvector GENERATED ALWAYS AS (
    to_tsvector('simple', text)
  ) STORED,
  chunk_embedding vector(1024) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_chunks_document
    FOREIGN KEY (document_id)
    REFERENCES documents(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_chunks_section
    FOREIGN KEY (section_id)
    REFERENCES sections(id)
    ON DELETE SET NULL
);

CREATE UNIQUE INDEX ux_chunks_document_chunk_index ON chunks (document_id, chunk_index);
CREATE INDEX ix_chunks_document_id ON chunks (document_id);
CREATE INDEX ix_chunks_section_id ON chunks (section_id);
CREATE INDEX ix_chunks_tsv ON chunks USING GIN (chunk_tsv);
CREATE INDEX ix_chunks_embedding_hnsw ON chunks USING hnsw (chunk_embedding vector_cosine_ops);

-- chat_sessions
CREATE TABLE chat_sessions (
  id uuid PRIMARY KEY,
  external_session_key varchar(128) NOT NULL,
  owner_user_id uuid NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_chat_sessions_owner_user
    FOREIGN KEY (owner_user_id)
    REFERENCES users(id)
);

CREATE UNIQUE INDEX ux_chat_sessions_external_session_key ON chat_sessions (external_session_key);

-- chat_messages
CREATE TABLE chat_messages (
  id uuid PRIMARY KEY,
  chat_session_id uuid NOT NULL,
  role varchar(16) NOT NULL,
  content text NOT NULL,
  citations_json jsonb NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_chat_messages_chat_session
    FOREIGN KEY (chat_session_id)
    REFERENCES chat_sessions(id)
    ON DELETE CASCADE
);

CREATE INDEX ix_chat_messages_session_created_at ON chat_messages (chat_session_id, created_at);
