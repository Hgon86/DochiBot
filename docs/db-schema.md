# DochiBot DB 스키마 상세

> 마지막 갱신: 2026-08-08, 코드 기준
>
> **단일 진실 소스 (SoT): `src/main/resources/db/migration/V1__init.sql`**
> 이 문서는 DDL 기반 2차 문서이며, DDL과 불일치 시 DDL이 우선한다.

## 0. 개요

- 데이터베이스: PostgreSQL 17 + pgvector 확장
- 스키마: `public`
- 식별자: 모든 PK는 UUID (UUID v7, `uuid-creator` 라이브러리로 생성)
- 타임스탬프: `timestamptz` (UTC)
- 컬럼 네이밍: `snake_case`
- 임베딩 차원: `vector(1024)` (기본값, `dochibot.ai.embedding.dims`로 제어)
- 마이그레이션: Flyway (JDBC 경로)

## 1. 확장 (Extensions)

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## 2. ENUM 정의

> DDL에서는 `varchar(16)`으로 저장되며, Kotlin `enum class`가 값을 제약한다.

| ENUM | 값 | 설명 |
|------|-----|------|
| `user_role` | `ADMIN`, `USER` | 사용자 권한 |
| `auth_provider` | `CREDENTIALS`, `GOOGLE`, `GITHUB` | 인증 제공자 (`users.provider`) |
| `document_source_type` | `PDF`, `TEXT` | 문서 소스 유형 |
| `document_status` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` | 문서 처리 상태 |
| `document_language` | `KO`, `EN`, `UNKNOWN` | 문서 언어 |
| `ingestion_job_status` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED` | 인제션 잡 상태 |
| `chat_role` | `USER`, `ASSISTANT` | 채팅 메시지 역할 |

## 3. 테이블 상세

### 3.1 `users`

사용자 계정 정보. 로컬 인증(CREDENTIALS)과 OAuth2(GOOGLE, GITHUB)를 모두 지원한다.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 사용자 UUID (UUIDv7) |
| `username` | `varchar(64)` | NOT NULL | - | 로그인 아이디 |
| `password_hash` | `varchar(100)` | NULL | - | BCrypt 해시 (CREDENTIALS 시 필수, OAuth 시 NULL) |
| `role` | `varchar(16)` | NOT NULL | `'USER'` | 권한: ADMIN / USER |
| `provider` | `varchar(16)` | NOT NULL | `'CREDENTIALS'` | 인증 제공자: CREDENTIALS / GOOGLE / GITHUB |
| `provider_id` | `varchar(128)` | NULL | - | OAuth 제공자 측 사용자 ID |
| `is_active` | `boolean` | NOT NULL | `true` | 계정 활성화 여부 |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |
| `updated_at` | `timestamptz` | NOT NULL | `now()` | 수정 시각 |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ux_users_username` | `username` | UNIQUE |
| `ux_users_provider_provider_id` | `provider, provider_id` | UNIQUE, WHERE `provider_id IS NOT NULL` (부분 인덱스) |

**대응 엔티티:** `com.dochibot.domain.entity.User`

---

### 3.2 `documents`

업로드된 문서 메타데이터. 실제 파일은 S3(SeaweedFS)에 저장되며, 이 테이블은 메타데이터와 처리 상태만 관리한다.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 문서 UUID (UUIDv7) |
| `title` | `varchar(255)` | NOT NULL | - | 문서 제목 |
| `source_type` | `varchar(16)` | NOT NULL | - | 소스 유형: PDF / TEXT |
| `original_filename` | `varchar(512)` | NULL | - | 원본 파일명 |
| `storage_uri` | `text` | NULL | - | S3 스토리지 URI (`s3://{bucket}/{key}`) |
| `status` | `varchar(16)` | NOT NULL | `'PENDING'` | 처리 상태: PENDING → PROCESSING → COMPLETED / FAILED |
| `error_message` | `text` | NULL | - | 처리 실패 시 오류 메시지 |
| `created_by_user_id` | `uuid` | NULL, FK → `users(id)` | - | 업로드한 사용자 ID |
| `language` | `varchar(16)` | NOT NULL | `'UNKNOWN'` | 문서 언어: KO / EN / UNKNOWN |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |
| `updated_at` | `timestamptz` | NOT NULL | `now()` | 수정 시각 |

**FK:**

| 이름 | 컬럼 | 참조 |
|------|------|------|
| `fk_documents_created_by_user` | `created_by_user_id` | `users(id)` |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ix_documents_status` | `status` | 일반 |

**대응 엔티티:** `com.dochibot.domain.entity.Document`

---

### 3.3 `document_ingestion_jobs`

문서 인제션 작업 단위. 문서가 finalize되면 자동 생성되며, Worker가 폴링하여 처리한다. 재시도 메커니즘을 갖춘다.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 잡 UUID (UUIDv7) |
| `document_id` | `uuid` | NOT NULL, FK → `documents(id)` ON DELETE CASCADE | - | 대상 문서 ID |
| `status` | `varchar(16)` | NOT NULL | `'QUEUED'` | 잡 상태: QUEUED → RUNNING → SUCCEEDED / FAILED |
| `chunk_count` | `int` | NULL | - | 생성된 청크 수 (완료 시 기록) |
| `embedding_model` | `varchar(128)` | NULL | - | 사용된 임베딩 모델명 |
| `embedding_dims` | `int` | NULL | - | 임베딩 차원 (dims) |
| `attempt_count` | `int` | NOT NULL | `0` | 현재 재시도 횟수 |
| `max_attempts` | `int` | NOT NULL | `3` | 최대 재시도 횟수 |
| `next_run_at` | `timestamptz` | NULL | - | 다음 실행 시각 (백오프 후 재시도 시 설정) |
| `started_at` | `timestamptz` | NULL | - | 작업 시작 시각 |
| `finished_at` | `timestamptz` | NULL | - | 작업 완료 시각 |
| `error_message` | `text` | NULL | - | 실패 시 오류 메시지 |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |
| `updated_at` | `timestamptz` | NOT NULL | `now()` | 수정 시각 |

**FK:**

| 이름 | 컬럼 | 참조 | 삭제 정책 |
|------|------|------|-----------|
| `fk_document_ingestion_jobs_document` | `document_id` | `documents(id)` | `CASCADE` |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ix_document_ingestion_jobs_document_id` | `document_id` | 일반 |
| `ix_document_ingestion_jobs_status` | `status` | 일반 |
| `ix_document_ingestion_jobs_next_run_at` | `next_run_at` | 일반 |

**대응 엔티티:** `com.dochibot.domain.entity.DocumentIngestionJob`

---

### 3.4 `sections`

문서의 계층적 섹션 구조. Markdown heading 파싱 또는 PDF 페이지 단위로 생성된다. **엔티티 클래스 없음** — `DocumentIndexWriter`가 raw SQL로 접근한다.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 섹션 UUID |
| `document_id` | `uuid` | NOT NULL, FK → `documents(id)` ON DELETE CASCADE | - | 소속 문서 ID |
| `parent_id` | `uuid` | NULL, FK → `sections(id)` ON DELETE CASCADE | - | 부모 섹션 ID (계층 구조) |
| `level` | `int` | NOT NULL | - | heading 레벨 (1~6, PDF 페이지는 1) |
| `heading` | `text` | NOT NULL | - | 섹션 제목 |
| `section_path` | `text` | NOT NULL | - | 문서 제목 포함 전체 경로 (예: `문서명 > Section A > Sub B`) |
| `start_offset` | `int` | NULL | - | (예약됨, 현재 미사용) |
| `end_offset` | `int` | NULL | - | (예약됨, 현재 미사용) |
| `summary` | `text` | NULL | - | (예약됨, 현재 미사용) |
| `section_text` | `text` | NULL | - | 섹션 본문 (정규화된 plain text) |
| `section_tsv` | `tsvector` | GENERATED ALWAYS AS ... STORED | 자동 | 전문 검색용 tsvector (heading + summary + section_text 결합, `simple` 사전) |
| `section_embedding` | `vector(1024)` | NULL | - | 섹션 임베딩 (소속 청크 임베딩의 평균) |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |
| `updated_at` | `timestamptz` | NOT NULL | `now()` | 수정 시각 |

**FK:**

| 이름 | 컬럼 | 참조 | 삭제 정책 |
|------|------|------|-----------|
| `fk_sections_document` | `document_id` | `documents(id)` | `CASCADE` |
| `fk_sections_parent` | `parent_id` | `sections(id)` | `CASCADE` |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ix_sections_document_id` | `document_id` | 일반 |
| `ix_sections_parent_id` | `parent_id` | 일반 |
| `ix_sections_tsv` | `section_tsv` | GIN (전문 검색) |
| `ix_sections_embedding_hnsw` | `section_embedding` | HNSW (`vector_cosine_ops`) |

---

### 3.5 `chunks`

문서를 작은 단위로 분할한 청크. 검색의 기본 단위로, 벡터 임베딩과 전문 검색(tsvector)을 모두 갖춘다. **엔티티 클래스 없음** — `DocumentIndexWriter`가 raw SQL로 접근한다.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 청크 UUID |
| `document_id` | `uuid` | NOT NULL, FK → `documents(id)` ON DELETE CASCADE | - | 소속 문서 ID |
| `section_id` | `uuid` | NULL, FK → `sections(id)` ON DELETE SET NULL | - | 소속 섹션 ID |
| `chunk_index` | `int` | NOT NULL | - | 문서 내 청크 순번 |
| `text` | `text` | NOT NULL | - | 청크 본문 (`[D] 문서명\n[S] 섹션경로\n[B] 본문` 형식으로 인코딩) |
| `page` | `int` | NULL | - | PDF 페이지 번호 (PDF만 사용) |
| `start_offset` | `int` | NULL | - | (예약됨, 현재 미사용) |
| `end_offset` | `int` | NULL | - | (예약됨, 현재 미사용) |
| `chunk_tsv` | `tsvector` | GENERATED ALWAYS AS ... STORED | 자동 | 전문 검색용 tsvector (`text` 컬럼, `simple` 사전) |
| `chunk_embedding` | `vector(1024)` | NOT NULL | - | 청크 벡터 임베딩 |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |

**FK:**

| 이름 | 컬럼 | 참조 | 삭제 정책 |
|------|------|------|-----------|
| `fk_chunks_document` | `document_id` | `documents(id)` | `CASCADE` |
| `fk_chunks_section` | `section_id` | `sections(id)` | `SET NULL` |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ux_chunks_document_chunk_index` | `document_id, chunk_index` | UNIQUE |
| `ix_chunks_document_id` | `document_id` | 일반 |
| `ix_chunks_section_id` | `section_id` | 일반 |
| `ix_chunks_tsv` | `chunk_tsv` | GIN (전문 검색) |
| `ix_chunks_embedding_hnsw` | `chunk_embedding` | HNSW (`vector_cosine_ops`) |

---

### 3.6 `chat_sessions`

채팅 세션 정보. UI가 직접 소유하는 세션 단위.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 세션 UUID (UUIDv7) |
| `external_session_key` | `varchar(128)` | NOT NULL | - | UI 측 외부 세션 키 (UNIQUE) |
| `owner_user_id` | `uuid` | NULL, FK → `users(id)` | - | 세션 소유 사용자 ID |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |

**FK:**

| 이름 | 컬럼 | 참조 |
|------|------|------|
| `fk_chat_sessions_owner_user` | `owner_user_id` | `users(id)` |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ux_chat_sessions_external_session_key` | `external_session_key` | UNIQUE |

**대응 엔티티:** `com.dochibot.domain.entity.ChatSession`

---

### 3.7 `chat_messages`

채팅 메시지. Phase 1에서는 단순 기록 용도.

| 컬럼 | 타입 | 제약 | 기본값 | 설명 |
|------|------|------|--------|------|
| `id` | `uuid` | PK | - | 메시지 UUID (UUIDv7) |
| `chat_session_id` | `uuid` | NOT NULL, FK → `chat_sessions(id)` ON DELETE CASCADE | - | 소속 세션 ID |
| `role` | `varchar(16)` | NOT NULL | - | 역할: USER / ASSISTANT |
| `content` | `text` | NOT NULL | - | 메시지 내용 |
| `citations_json` | `jsonb` | NULL | - | AI 응답의 근거 청크 정보 (JSON) |
| `created_at` | `timestamptz` | NOT NULL | `now()` | 생성 시각 |

**FK:**

| 이름 | 컬럼 | 참조 | 삭제 정책 |
|------|------|------|-----------|
| `fk_chat_messages_chat_session` | `chat_session_id` | `chat_sessions(id)` | `CASCADE` |

**인덱스:**

| 인덱스명 | 컬럼 | 유형 |
|----------|------|------|
| `ix_chat_messages_session_created_at` | `chat_session_id, created_at` | 복합 |

**대응 엔티티:** `com.dochibot.domain.entity.ChatMessage`

---

## 4. ER 관계 요약

```
users ──1:N──> documents (created_by_user_id)
users ──1:N──> chat_sessions (owner_user_id)
documents ──1:N──> document_ingestion_jobs (document_id, CASCADE)
documents ──1:N──> sections (document_id, CASCADE)
documents ──1:N──> chunks (document_id, CASCADE)
sections ──1:N──> sections (parent_id, CASCADE, 자기 참조)
sections ──1:N──> chunks (section_id, SET NULL)
chat_sessions ──1:N──> chat_messages (chat_session_id, CASCADE)
```

## 5. 엔티티 클래스 매핑 참고

| 테이블 | 엔티티 클래스 | 비고 |
|--------|--------------|------|
| `users` | `com.dochibot.domain.entity.User` | - |
| `documents` | `com.dochibot.domain.entity.Document` | - |
| `document_ingestion_jobs` | `com.dochibot.domain.entity.DocumentIngestionJob` | - |
| `sections` | **없음** | `DocumentIndexWriter`가 raw SQL로 직접 접근 |
| `chunks` | **없음** | `DocumentIndexWriter`가 raw SQL로 직접 접근 |
| `chat_sessions` | `com.dochibot.domain.entity.ChatSession` | - |
| `chat_messages` | `com.dochibot.domain.entity.ChatMessage` | - |
