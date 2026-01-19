# DochiBot 명세서 (Phase 1 우선)

## 0. 문서 목적
- **목표**: RPi 환경에서 구동되는 **내부 문서 기반 RAG 운영 백엔드(인입/인덱싱/검색/관측/재처리)**의 Phase 1(1주 MVP) 요구사항/아키텍처/인터페이스를 명확히 정의한다.
- **원칙**: Phase 2~3 확장 가능성을 고려하되, **Phase 1 범위를 초과한 구현/복잡도는 배제**한다.

## 1. 프로젝트 개요
| 항목 | 내용 |
|---|---|
| 프로젝트명 | DochiBot |
| 최종 목표 | 내부 매뉴얼 → 고객 자동 응대 봇 (장기적으로 채널톡 대체) |
| MVP 범위 | Phase 1: 내부 매뉴얼 챗봇 (1주) |
| 배포 타겟 | Raspberry Pi (Docker Compose) |
| 백엔드 | JDK 25 + Kotlin + Spring Boot(WebFlux) + Coroutines + Spring AI |
| 검색/저장 | Elasticsearch (키워드 + 벡터 검색 기반) |
| LLM 런타임 | Ollama (로컬) |
| 프론트엔드(Phase 1) | Next.js (간단한 채팅 UI) |

## 2. Phase 정의

### Phase 1 (1주 MVP) — 내부 매뉴얼 챗봇
- **업로드(인덱싱)**: PDF/텍스트 → 텍스트 추출 → 청킹 → 임베딩 → Elasticsearch 인덱싱
- **챗(RAG)**: Next.js UI → Spring AI API → 하이브리드 검색(우선은 벡터+키워드 중 최소 1개로 시작 가능) → 답변 생성
- **배포**: RPi Docker Compose로 `Spring Boot + Elasticsearch + Ollama` 구동

### Phase 2 (2주) — 채널톡 스타일 고객 지원
- WebSocket 실시간 채팅(Next.js)
- 하이브리드 검색 고도화(ES 강점)
- 에스컬레이션: “모르겠음/불확실” → 인간 상담원 라우팅

### Phase 3 (고급) — 고객 데이터 학습 자동화
- 문의 로그 → 익명화/임베딩 → ES 추가(자기 학습)
- 과거 Q&A로 프롬프트 강화/파인튜닝 시뮬
- (추후) 인증 강화/운영 자동화

## 3. Phase 1 범위(Scope)

### 3.1 In Scope (반드시 구현)
1. **문서 업로드/인덱싱**
   - PDF 업로드(파일) 및 텍스트 업로드(텍스트/파일)
   - 텍스트 추출(UTF-8 보장) + 메타데이터(문서명/업로드일/버전 등)
   - 청킹 정책 적용
   - 임베딩 생성
   - Elasticsearch 인덱싱(문서 본문/메타데이터 + 벡터)

2. **RAG 채팅 API**
   - 질문 입력 → 검색(TopK) → 컨텍스트 구성 → LLM 답변
   - 답변에 **근거 문서**(문서명 + 스니펫 필수, 페이지/섹션은 가능하면 포함) 최소 1개 이상 포함

3. **간단한 UI(Phase 1)**
   - 채팅 화면(질문/답변)
   - “근거 보기”(출처 리스트/스니펫 표시)

4. **RPi 배포**
   - Docker Compose로 서비스 구동
   - 환경변수로 ES/Ollama 주소 등 설정

### 3.2 Out of Scope (Phase 1에서는 하지 않음)
- 실시간 WebSocket 채팅(Phase 2)
- 인간 상담원 큐/라우팅(Phase 2)
- 자동 익명화 파이프라인(Phase 3)
- 고급 재랭킹/평가 자동화(Phase 2~3에서 고려)

## 4. 사용자 시나리오 (Phase 1)

### 4.1 관리자(운영자)
- 내부 매뉴얼 PDF를 업로드한다.
- 업로드된 문서 목록을 확인한다.
- 인덱싱 상태(대기/처리중/완료/실패)를 확인한다.

### 4.2 사용자(내부 직원)
- 질문을 입력한다.
- 봇의 답변과 함께 근거(출처)를 확인한다.
- 답변이 부족하면 질문을 이어서 한다.

## 5. 비기능 요구사항 (Phase 1)
- **응답 시간**: 단일 질의 기준(검색+생성) 5~15초 내(로컬 LLM 성능에 따라 조정)
- **관측성**: 요청/응답 로그(PII 없는 범위), 인덱싱 실패 원인 로그
- **신뢰성**: 인덱싱 실패 시 재시도(최소 수동 재시도) 가능
- **보안**: Phase 1은 내부망 전제. JWT 기반 인증을 적용하고(Access Token), 추후 필요 시 인증/권한 정책을 강화한다.

## 6. 시스템 아키텍처 (Phase 1)

### 6.1 구성요소
- **Next.js UI**: 챗 화면 + 근거 표시
- **Spring Boot API (WebFlux + Kotlin Coroutines)**
  - 문서 업로드/인덱싱
  - 챗(RAG) 엔드포인트
  - S3 Presigned URL 발급
  - (선택) 문서 목록/상태 조회
- **Elasticsearch**
  - 텍스트 필드(키워드 검색)
  - dense_vector(벡터 검색)
- **Ollama**
  - Chat 모델(예: `llama3`, `mistral`, `qwen` 등)
  - Embedding 모델(예: `nomic-embed-text` 등)
- **S3 (Presigned URL 방식)**
  - 파일 저장소 (AWS S3 또는 MinIO 호환)
  - 업로드/다운로드 Presigned URL 발급

### 6.2 데이터 흐름
1) 업로드 (Presigned URL 방식)
- 클라이언트 → 서버: 업로드 요청 (파일명, 콘텐츠 타입)
- 서버 → S3: Presigned URL 발급 요청
- 서버 → 클라이언트: Presigned URL 반환
- 클라이언트 → S3: 파일 직접 업로드 (Presigned URL 사용)
- 클라이언트 → 서버: 업로드 완료通知
- 서버: 메타데이터 저장 + 인덱싱 시작

2) 채팅
- 질문 → (키워드/벡터) 검색 → TopK 컨텍스트 → 프롬프트 구성 → LLM 답변 → 출처 포함 응답

## 7. 데이터 모델 (Phase 1)

Phase 1에서는 **Elasticsearch(검색/근거)** + **PostgreSQL(업로드/상태/운영 데이터)** 2계층으로 간다.
- Elasticsearch: 청크 단위 인덱스(검색/출처 스니펫)
- PostgreSQL: 문서/인덱싱 작업/챗 세션(옵션)/메시지(옵션) 같은 **트랜잭션성 데이터**

### 7.1 PostgreSQL 스키마 (권장)

> 네이밍: snake_case, PK는 UUID 권장, 시간은 `timestamptz`.

#### 7.1.1 `documents`
업로드된 원문 문서(파일/텍스트) 메타데이터.

- `id` (uuid, pk)
- `title` (varchar(255), not null) — 표시용 문서명
- `source_type` (varchar(16), not null) — `PDF` | `TEXT`
- `original_filename` (varchar(512), null) — 파일 업로드인 경우
- `content_sha256` (char(64), not null) — 동일 문서 중복 업로드 방지/재사용
- `status` (varchar(32), not null) — `PENDING` | `PROCESSING` | `COMPLETED` | `FAILED`
- `error_message` (text, null)
- `created_at` (timestamptz, not null)
- `updated_at` (timestamptz, not null)

인덱스:
- unique(`content_sha256`)
- index(`status`)

#### 7.1.2 `document_ingestion_jobs`
문서별 인덱싱 실행 단위(재시도/실패 추적).

- `id` (uuid, pk)
- `document_id` (uuid, not null, fk -> documents.id)
- `status` (varchar(32), not null) — `QUEUED` | `RUNNING` | `SUCCEEDED` | `FAILED`
- `chunk_count` (int, null)
- `embedding_model` (varchar(128), null)
- `chat_model` (varchar(128), null) — (참고) 인덱싱엔 불필요하지만 운영정보로 남길 수 있음
- `es_index_name` (varchar(255), not null) — 예: `dochi_docs_v1`
- `started_at` (timestamptz, null)
- `finished_at` (timestamptz, null)
- `error_message` (text, null)
- `created_at` (timestamptz, not null)

인덱스:
- index(`document_id`)
- index(`status`)

#### 7.1.3 (선택) `chat_sessions`
Phase 1에서는 `sessionId`를 클라이언트가 주면 그대로 사용해도 되지만, 운영/추적을 위해 세션 테이블을 두는 것을 권장.

- `id` (uuid, pk)
- `external_session_key` (varchar(128), not null) — UI에서 유지하는 세션 키
- `created_at` (timestamptz, not null)

인덱스:
- unique(`external_session_key`)

#### 7.1.4 (선택) `chat_messages`
최소한의 대화 로그(추후 Phase 2~3 확장 시 유용). Phase 1에서는 저장 OFF도 가능.

- `id` (uuid, pk)
- `chat_session_id` (uuid, not null, fk -> chat_sessions.id)
- `role` (varchar(16), not null) — `USER` | `ASSISTANT`
- `content` (text, not null)
- `citations_json` (jsonb, null) — assistant 응답 근거(옵션)
- `created_at` (timestamptz, not null)

인덱스:
- index(`chat_session_id`, `created_at`)

### 7.2 Elasticsearch 인덱스(권장)
- 인덱스명: `dochi_docs_v1` (버전으로 마이그레이션 가능)

필드 예시:
- `id`: chunk id (UUID)
- `documentId`: 원문 문서 id (PostgreSQL documents.id)
- `documentTitle`: 문서명
- `sourceType`: `PDF|TEXT`
- `storageUri`: S3 URI (예: `s3://dochi-bot/2026/01/{uuid}_{filename}`)
- `chunkIndex`: 청크 순번
- `content`: 청크 텍스트(검색/출처 스니펫)
- `contentVector`: dense_vector (dims=embedding dims)
- `page`: PDF 페이지(가능한 경우)
- `section`: 섹션/헤더(가능한 경우)
- `createdAt`: 인덱싱 시각

> NOTE: embedding dims는 선택한 임베딩 모델에 종속.

## 8. 청킹/임베딩 정책 (Phase 1 기본값)

### 8.1 청킹 기본값(초안)
- **청크 크기**: 400~800 토큰 수준(초기값 600)
- **오버랩**: 10~20%(초기값 15%)
- **분할 전략**: 문단/헤더 우선 → 줄바꿈 → 공백 기준 fallback

### 8.2 임베딩
- Ollama embedding 모델 사용(로컬)
- 인덱싱 시 배치 처리(대량 업로드 대비)

## 9. 검색 전략 (Phase 1)

### 9.1 최소 구현
- **Option A (가장 단순)**: 벡터 검색만으로 TopK
- **Option B (권장)**: 키워드(BM25) + 벡터(kNN) 혼합

Phase 1 권장안:
- 먼저 Option A로 MVP를 안정화하고,
- 데이터가 쌓이면 Option B(하이브리드)로 전환.

### 9.2 결과 구성
- TopK = 3~8
- 결과마다 `documentTitle`, `page/section`, `snippet` 포함

## 10. 프롬프트/응답 포맷 (Phase 1)

### 10.1 시스템 프롬프트 원칙
- 근거 문서 외 추측 금지(“문서에 없다면 모른다”) 룰을 기본으로 둔다.
- 내부 매뉴얼 기반 답변임을 명시.

### 10.2 응답 JSON(권장)
- `answer`: 최종 답변(마크다운)
- `citations`: 근거 리스트
  - `documentTitle`
  - `page`(옵션)
  - `snippet`
  - `score`(옵션)

## 11. API 명세 (Phase 1)

> URL prefix 예시: `/api/v1`

### 11.1 문서 업로드 (Presigned URL 방식)
Phase 1에서는 **Presigned URL** 방식으로 S3에 직접 업로드한다.

1. `GET /api/v1/documents/upload-url` — 업로드용 Presigned URL 발급
2. 클라이언트 → S3: 파일 직접 업로드(PUT)
3. `POST /api/v1/documents` — 문서 메타데이터 저장 + 인덱싱 시작

응답은 `docs/phase1-api.md`의 상세 스펙을 따른다.

### 11.2 문서 목록/상태
- `GET /api/v1/documents`
  - response: 문서 리스트(상태 포함)

- `GET /api/v1/documents/{documentId}`
  - response: 단건 상태/메타데이터

### 11.3 챗(RAG)
- `POST /api/v1/chat`
  - request: `message`, `sessionId`(옵션)
  - response: `answer`, `citations[]`

## 12. 에러/예외 정책 (Phase 1)
- 업로드 실패: 파일 형식/크기 제한 위반 → 4xx
- 인덱싱 실패: 처리 실패 사유 반환 + 재시도 가능 상태로 유지
- 챗 실패: 검색 실패/LLM 실패에 대해 사용자 친화 메시지 제공

## 13. 배포 (RPi) — 목표 상태
- `docker-compose.yml`로 다음 서비스 구동
  - `api` (Spring Boot)
  - `elasticsearch`
  - `ollama`
- 환경변수 예시
  - `ELASTICSEARCH_URL`
  - `OLLAMA_BASE_URL`
  - `S3_ENDPOINT` (S3/MinIO 엔드포인트)
  - `S3_BUCKET` (버킷명)
  - `S3_ACCESS_KEY`
  - `S3_SECRET_KEY`
  - `S3_REGION`
  - `SPRING_PROFILES_ACTIVE=local|rpi`

## 14. 수용 기준(Acceptance Criteria) — Phase 1
1. PDF 1개 업로드 시 인덱싱 완료(상태 `COMPLETED`)
2. 문서 내용을 근거로 한 질문에 대해 **근거 포함 답변** 반환(문서명 + 스니펫 필수)
3. 근거가 없는 질문에 대해 “문서에서 찾을 수 없음” 형태로 응답 가능
4. RPi Docker Compose로 1회 명령으로 서비스 기동 가능
5. (운영 범위) Phase 1 기준으로 문서 N개/총 용량 MMB 내에서 동작을 검증한다(예: N=10, M=50MB)
6. (관측성) 요청 단위로 `request_id`를 기준으로 검색/생성 주요 지표(TopK 개수/latency/모델)를 로그로 확인 가능

## 15. Phase 2~3 확장 고려사항(요약)

### 15.1 Phase 2
- WebSocket 기반 실시간 채팅(세션/룸)
- 하이브리드 검색 기본값 전환 + 검색 품질 튜닝
- “불확실/모르겠음” 감지 → 상담원 채널로 전환(큐/티켓 시스템 연동)

### 15.2 Phase 3
- 문의 로그 수집 → **익명화(PII 마스킹)** → 임베딩 → ES 누적
- (추후) 인증/권한 정책 강화
- Q&A 기반 프롬프트 강화/평가 자동화

## 16. 실행/검증 커맨드(참고)
> 사용자가 직접 실행
- 테스트: `./gradlew test`
- 빌드: `./gradlew build`
- (도커) 기동: `docker compose up -d`
