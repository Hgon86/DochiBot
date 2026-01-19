# PostgreSQL 스키마 명세 (Phase 1)

## 0. 목적
- Phase 1에서 필요한 **트랜잭션성 데이터**를 PostgreSQL에 저장한다.
- 검색/근거 스니펫은 Elasticsearch에 저장한다.

## 1. 네이밍/타입 규칙
- 스키마: 기본 `public`
- 테이블/컬럼: `snake_case`
- PK: `uuid`
- 시간: `timestamptz`

## 2. ENUM 정의
- `user_role`: `ADMIN`, `USER`
- `document_source_type`: `PDF`, `TEXT`
- `document_status`: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
- `ingestion_job_status`: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`
- `chat_role`: `USER`, `ASSISTANT`

## 3. 테이블 명세

### 3.1 `users`
로그인 사용자.
- `id` uuid PK
- `username` varchar(64) UNIQUE NOT NULL
- `password_hash` varchar(100) NOT NULL
- `role` user_role NOT NULL DEFAULT `USER`
- `is_active` boolean NOT NULL DEFAULT true
- `created_at` timestamptz NOT NULL
- `updated_at` timestamptz NOT NULL

인덱스/제약:
- `unique (username)`


### 3.2 `documents`
업로드된 문서 메타데이터/상태.
- `id` uuid PK
- `title` varchar(255) NOT NULL
- `source_type` document_source_type NOT NULL
- `original_filename` varchar(512) NULL
- `storage_uri` text NULL (S3 URI; 형식: `s3://dochi-bot/{year}/{month}/{uuid}_{filename}`)
- `content_sha256` char(64) UNIQUE NOT NULL (중복 업로드 방지)
- `status` document_status NOT NULL
- `error_message` text NULL
- `created_by_user_id` uuid NULL (FK -> users.id) — 추후 감사로그 용도
- `created_at` timestamptz NOT NULL
- `updated_at` timestamptz NOT NULL

인덱스/제약:
- `unique (content_sha256)`
- `index (status)`


### 3.3 `document_ingestion_jobs`
문서 인덱싱 작업 단위(재시도/실패 추적).
- `id` uuid PK
- `document_id` uuid NOT NULL (FK -> documents.id)
- `status` ingestion_job_status NOT NULL
- `chunk_count` int NULL
- `embedding_model` varchar(128) NULL
- `es_index_name` varchar(255) NOT NULL
- `started_at` timestamptz NULL
- `finished_at` timestamptz NULL
- `error_message` text NULL
- `created_at` timestamptz NOT NULL

인덱스/제약:
- `index (document_id)`
- `index (status)`


### 3.4 (선택) `chat_sessions`
UI가 유지하는 세션 키를 서버에서 추적.
- `id` uuid PK
- `external_session_key` varchar(128) UNIQUE NOT NULL
- `owner_user_id` uuid NULL (FK -> users.id)
- `created_at` timestamptz NOT NULL


### 3.5 (선택) `chat_messages`
Phase 1에서는 저장을 끄고 시작해도 됨(운영 편의상 있으면 좋음).
- `id` uuid PK
- `chat_session_id` uuid NOT NULL (FK -> chat_sessions.id)
- `role` chat_role NOT NULL
- `content` text NOT NULL
- `citations_json` jsonb NULL
- `created_at` timestamptz NOT NULL

인덱스:
- `index (chat_session_id, created_at)`

## 4. DDL 위치
- 실제 `CREATE TABLE` 문은 `db/ddl/001_init.sql`에 있다.
