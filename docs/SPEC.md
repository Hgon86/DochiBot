# DochiBot 기술 설계 문서 (SPEC)

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 기술 스택 및 선택 이유

### 1.1 백엔드

| 기술 | 버전 | 선택 이유 |
|------|------|-----------|
| Kotlin | 2.0.21 | 간결한 문법, 코루틴 네이티브 지원, Java 생태계 호환 |
| JDK | 21 | LTS, Virtual Thread 준비, 최신 GC 개선 |
| Spring Boot | 3.4.1 | WebFlux + R2DBC 리액티브 스택, Spring AI 통합 |
| Spring AI | 1.1.2 | Ollama/OpenAI 추상화, 벡터 스토어 통합 |
| PostgreSQL 17 + pgvector | 17 | 벡터 검색 + FTS를 단일 DB로 통합 (별도 검색 엔진 불필요) |
| R2DBC | reactive | Non-blocking DB 액세스 |
| Flyway (JDBC) | - | 스키마 마이그레이션 (R2DBC는 DDL 미지원) |
| Redis | 7 | 세션 캐시, 속도 향상 |
| Apache PDFBox | 2.0.35 | PDF 텍스트 추출 |

### 1.2 프론트엔드

| 기술 | 버전 | 선택 이유 |
|------|------|-----------|
| React | 19.2 | 컴포넌트 기반 UI |
| TypeScript | 5.9 | 정적 타입 안전성 |
| Vite | 7.2 | 빠른 HMR, ESBuild 기반 빌드 |
| React Router | 7.13 | SPA 라우팅 |
| TanStack Query | 5.90 | 서버 상태 관리, 캐싱, 자동 리페치 |
| Zustand | 5.0 | 경량 클라이언트 상태 (인증 토큰 등) |
| ky | 1.14 | fetch 래퍼, 인터셉터, 401→refresh 자동 재시도 |
| Tailwind CSS | 4.1 | 유틸리티 퍼스트 CSS |
| Radix UI | - | 접근성 갖춘 헤드리스 UI 프리미티브 |
| Biome | 2.3 | 린트/포맷팅 (ESLint+Prettier 대체) |

### 1.3 인프라

| 기술 | 용도 |
|------|------|
| SeaweedFS | S3 호환 경량 오브젝트 스토리지 |
| Ollama | 로컬 LLM (채팅 + 임베딩) |
| Cross-Encoder (Python) | 선택적 고정밀 리랭킹 |

## 2. 전체 아키텍처

### 2.1 백엔드: VSA (Vertical Slice Architecture)

코드는 기능(feature) 단위로 수직 분할되어 있다. 각 feature는 자체 controller, application(use case), dto, repository, exception, config를 포함한다.

```
src/main/kotlin/com/dochibot/
├── common/                          # 공통 인프라
│   ├── config/
│   │   ├── SecurityConfig.kt        # Spring Security + JWT 필터
│   │   ├── S3Config.kt              # S3 클라이언트 설정
│   │   └── CorsConfig.kt            # CORS 정책
│   ├── exception/
│   │   └── GlobalExceptionHandler.kt # 전역 예외 처리
│   ├── storage/
│   │   ├── config/                   # S3Config, S3Properties
│   │   ├── service/S3Service.kt      # Presigned URL 발급/삭제
│   │   └── util/S3StorageUtils.kt
│   └── util/                         # 공통 유틸리티
│
├── domain/                           # 공유 도메인
│   ├── entity/                       # User, Document, DocumentIngestionJob, ChatSession, ChatMessage
│   ├── enums/                        # DocumentStatus, IngestionJobStatus, DocumentSourceType, ChatRole, ...
│   └── repository/                   # R2DBC Repository 인터페이스 (User/Document/IngestionJob/ChatSession/ChatMessage)
│
├── feature/
│   ├── auth/                         # 인증/인가
│   │   ├── controller/               # AuthController, Oauth2Controller
│   │   ├── service/                  # AuthService, OAuth2UserService, RefreshTokenService
│   │   ├── config/                   # SecurityConfig, DochibotWebProperties, OAuth2ClientConfig, OAuth2Properties
│   │   ├── util/                     # JwtUtils, AuthCookies
│   │   ├── dto/
│   │   └── exception/
│   │
│   ├── chat/                         # RAG 채팅
│   │   ├── controller/ChatController.kt
│   │   ├── application/              # ChatUseCase, ChatAnswerFormatter
│   │   ├── repository/ChatMessageWriter.kt
│   │   ├── dto/
│   │   └── exception/
│   │
│   ├── document/                     # 문서 관리
│   │   ├── controller/DocumentController.kt
│   │   ├── application/              # CreateDocumentUploadUrlUseCase, FinalizeDocumentUploadUseCase,
│   │   │                             # DeleteDocumentUseCase, DocumentUploadPolicy, ListDocumentsUseCase,
│   │   │                             # GetDocumentUseCase, CreateDocumentDownloadUrlUseCase, ReindexDocumentUseCase
│   │   └── dto/
│   │
│   ├── ingestionjob/                 # 인제션 작업
│   │   ├── controller/IngestionJobController.kt
│   │   ├── application/              # IngestionJobWorker, IngestionJobService, DocumentIngestionProcessor,
│   │   │                             # DocumentContentLoader, DocumentTextExtractor, MarkdownSectionParser,
│   │   │                             # TextChunkingService, ListIngestionJobsUseCase
│   │   ├── repository/DocumentIndexWriter.kt
│   │   ├── dto/
│   │   └── exception/NonRetryableIngestionException.kt
│   │
│   ├── retrieval/                    # 검색 파이프라인
│   │   ├── application/              # HybridRetrievalService, RrfFusion
│   │   ├── application/rerank/       # Reranker, RerankerRouter, HeuristicReranker, CrossEncoderReranker, LlmJudgeReranker
│   │   ├── application/verify/       # EvidenceVerifier, DefaultEvidenceVerifier, QueryTypeClassifier, VerifyPolicy
│   │   ├── dto/                      # SectionCandidate, ChunkCandidate
│   │   ├── repository/               # SectionRetrievalRepository, ChunkRetrievalRepository
│   │   └── infrastructure/           # metrics/, log/
│   │
│   └── health/                       # 헬스 체크
│       ├── controller/HealthController.kt
│       └── dto/
```

### 2.2 프론트엔드 구조

```
frontend/src/
├── app/
│   ├── router.tsx                     # React Router 설정
│   └── routes/
│       ├── login.tsx                  # 로그인
│       ├── oauth-callback.tsx         # OAuth2 콜백
│       ├── dashboard.tsx              # 대시보드
│       ├── documents.tsx              # 문서 목록
│       ├── document-detail.tsx        # 문서 상세
│       ├── ingestion-jobs.tsx         # 인제션 작업
│       ├── chat.tsx                   # 채팅
│       └── monitoring.tsx             # 모니터링
│
├── shared/
│   ├── api/
│   │   ├── http.ts                    # ky 인스턴스 (Bearer 자동 주입, 401→refresh)
│   │   ├── admin.ts                   # 모든 API 함수
│   │   └── types.ts                   # 응답 타입
│   ├── auth/
│   │   ├── session.ts                 # Zustand 토큰 스토어
│   │   ├── require-auth.tsx           # 라우트 가드
│   │   ├── use-auth.ts               # 로그인 훅
│   │   └── use-current-user.ts       # Me 쿼리
│   └── lib/
│       └── utils.ts
│
└── components/
    ├── layout/
    │   └── admin-shell.tsx            # 관리자 레이아웃
    └── ui/                            # 공통 UI 컴포넌트
```

## 3. 인증 설계

### 3.1 인증 흐름

```
[로그인] POST /api/v1/auth/login (username/password)
    → Access Token (JWT, 12시간) + Refresh Token (HttpOnly 쿠키)

[OAuth2] GET /api/v1/auth/oauth2/authorize/google
    → Google 로그인 → callback → Access Token + Refresh Token

[API 호출] Authorization: Bearer <Access Token>
    → 만료 시 ky 인터셉터가 자동 refresh
    → Refresh Token은 HttpOnly 쿠키에서 읽음 (JS 직접 접근 불가)
```

### 3.2 토큰 관리

- **Access Token**: Zustand 메모리에만 저장 (localStorage/sessionStorage 사용 금지). 만료 12시간.
- **Refresh Token**: HttpOnly 쿠키로 관리. JS 접근 불가, XSS로부터 보호.
- **401 동시성 제어**: 여러 API 호출이 동시에 401을 받아도 refresh는 단일 Promise로 1회만 수행.

### 3.3 역할 기반 접근

| 역할 | 권한 |
|------|------|
| ADMIN | 문서 업로드·목록·재인덱싱, 인제션 작업 조회, 사용자 생성 |
| USER | 채팅(RAG) |

### 3.4 관련 파일

- [SecurityConfig.kt](src/main/kotlin/com/dochibot/feature/auth/config/SecurityConfig.kt)
- [JwtUtils.kt](src/main/kotlin/com/dochibot/feature/auth/util/JwtUtils.kt)
- [AuthService.kt](src/main/kotlin/com/dochibot/feature/auth/service/AuthService.kt)
- [session.ts](frontend/src/shared/auth/session.ts)
- [http.ts](frontend/src/shared/api/http.ts)
- 상세 설계: [auth-jwt.md](auth-jwt.md)

## 4. 문서 업로드 플로우 (Presigned URL)

```
1. 클라이언트: POST /api/v1/documents/upload-url {originalFilename, contentType}
2. 서버: S3Service가 PUT Presigned URL 생성 → {uploadUrl, storageUri, documentId, ...} 반환
3. 클라이언트: native fetch()로 uploadUrl에 파일 직접 PUT (ky 대신 사용 — base URL이 다름)
4. 클라이언트: POST /api/v1/documents {documentId, title, sourceType, originalFilename, storageUri}
5. 서버: Document 엔티티 생성 (status=PENDING) → IngestionJob 자동 생성 (QUEUED)
6. Worker: IngestionJobWorker가 비동기로 청킹·임베딩 수행 → status PROCESSING → COMPLETED
```

**프론트엔드 측 S3 PUT 주의사항**: S3 업로드는 `ky`가 아닌 native `fetch`를 사용해야 한다. `ky` 인스턴스는 `/api/v1` prefix가 설정되어 있어 S3 URL과 호환되지 않는다.

### 4.1 관련 파일

- [S3Service.kt](src/main/kotlin/com/dochibot/common/storage/service/S3Service.kt)
- [CreateDocumentUploadUrlUseCase.kt](src/main/kotlin/com/dochibot/feature/document/application/CreateDocumentUploadUrlUseCase.kt)
- [FinalizeDocumentUploadUseCase.kt](src/main/kotlin/com/dochibot/feature/document/application/FinalizeDocumentUploadUseCase.kt)
- [S3Config.kt](src/main/kotlin/com/dochibot/common/storage/config/S3Config.kt)
- 상세 설정: [s3-config.md](s3-config.md)

## 5. 인제션 파이프라인 개요

비동기 Job Worker가 문서 처리의 전체 생명주기를 관리한다.

```
[PDF/Markdown 파일]
    → PDFBox 텍스트 추출
    → 청킹 (토큰 기반 분할, 오버랩)
    → Ollama 임베딩 생성 (bge-m3, 1024차원)
    → pgvector + tsvector 저장
    → Document.status = COMPLETED
```

### 5.1 Job 상태 머신

```
QUEUED → RUNNING → SUCCEEDED
                 → FAILED (error_message 기록, 재시도 가능)
```

### 5.2 관련 파일

- [IngestionJobWorker.kt](src/main/kotlin/com/dochibot/feature/ingestionjob/application/IngestionJobWorker.kt)
- [ReindexDocumentUseCase.kt](src/main/kotlin/com/dochibot/feature/document/application/ReindexDocumentUseCase.kt)
- 상세 설계: [ingestion-pipeline.md](ingestion-pipeline.md) (작성 예정)

## 6. RAG 파이프라인 개요

### 6.1 전체 흐름

```
[사용자 질의]
    → Dense Retriever (pgvector, 코사인 유사도)
    → Sparse Retriever (tsvector, GIN 인덱스 FTS)
    → RRF Fusion (Reciprocal Rank Fusion)
    → Reranker Router
        ├── Cross-Encoder Reranker  (고정밀, 별도 Python 서비스)
        ├── LLM-as-a-Judge Reranker (추론 능력 활용)
        └── Heuristic Reranker      (경량, 빠른 응답)
    → Pre-Generation Verify (문서-질문 연관성 평가)
    → LLM Generation (Ollama, 출처 강제 프롬프트)
    → Post-Generation Verify (환각·근거 대조)
    → Final Answer + Citations
```

### 6.2 핵심 설계 결정

- **RRF (Reciprocal Rank Fusion)**: Dense/Sparse 검색 결과의 점수 스케일이 다르므로, 순위 기반 병합으로 공정하게 융합한다.
- **Reranker Router**: 질의 복잡도와 리소스 상황에 따라 리랭커를 동적 선택한다. Cross-Encoder는 정확도가 높지만 별도 Python 서비스가 필요하므로 optional profile로 분리했다.
- **Verify Pipeline**: LLM 응답 전후에 개입하여 근거 없는 답변을 차단하고, 모든 응답에 원문 출처를 포함시킨다.

### 6.3 관련 파일

- [HybridRetrievalService.kt](src/main/kotlin/com/dochibot/feature/retrieval/application/HybridRetrievalService.kt)
- [SectionRetrievalRepository.kt](src/main/kotlin/com/dochibot/feature/retrieval/repository/SectionRetrievalRepository.kt)
- [ChunkRetrievalRepository.kt](src/main/kotlin/com/dochibot/feature/retrieval/repository/ChunkRetrievalRepository.kt)
- [RrfFusion.kt](src/main/kotlin/com/dochibot/feature/retrieval/application/RrfFusion.kt)
- [CrossEncoderReranker.kt](src/main/kotlin/com/dochibot/feature/retrieval/application/rerank/CrossEncoderReranker.kt)
- [LlmJudgeReranker.kt](src/main/kotlin/com/dochibot/feature/retrieval/application/rerank/LlmJudgeReranker.kt)
- [ChatUseCase.kt](src/main/kotlin/com/dochibot/feature/chat/application/ChatUseCase.kt)
- [ChatController.kt](src/main/kotlin/com/dochibot/feature/chat/controller/ChatController.kt)
- 상세 설계: [retrieval.md](retrieval.md) (작성 예정)

## 7. 데이터 모델 개요

DDL 단일 진실 소스: [V1__init.sql](src/main/resources/db/migration/V1__init.sql)

### 7.1 핵심 엔티티

**users**: 로그인 사용자
- id (UUID PK), username, password_hash (BCrypt), role (ADMIN/USER), is_active, timestamps

**documents**: 업로드된 문서
- id (UUID PK), title, source_type (PDF/TEXT), original_filename, storage_uri (S3), content_sha256 (UNIQUE, 중복 방지), status (PENDING/PROCESSING/COMPLETED/FAILED), error_message, timestamps

**document_chunks**: 문서 조각 (벡터 검색 대상)
- id (UUID PK), document_id (FK), chunk_index, content (text), content_tsvector (GIN 인덱스, FTS용), embedding (vector(1024), ivfflat 인덱스), metadata (JSONB), timestamps

**document_ingestion_jobs**: 인제션 작업 추적
- id (UUID PK), document_id (FK), status (QUEUED/RUNNING/SUCCEEDED/FAILED), chunk_count, embedding_model, started_at, finished_at, error_message, timestamps

**chat_sessions**: 대화 세션
- id (UUID PK), external_session_key (UNIQUE), owner_user_id (FK), timestamps

**chat_messages**: 대화 메시지 (선택적)
- id (UUID PK), chat_session_id (FK), role (USER/ASSISTANT), content, citations_json (JSONB), timestamps

### 7.2 관련 파일

- [V1__init.sql](src/main/resources/db/migration/V1__init.sql)
- 엔티티: [domain/entity/](src/main/kotlin/com/dochibot/domain/entity/)
- Enum: [domain/enums/](src/main/kotlin/com/dochibot/domain/enums/)
- 상세 스키마: [db-schema.md](db-schema.md)

## 8. 인프라 구성

### 8.1 Docker Compose 서비스

[docker-dev/compose.yaml](docker-dev/compose.yaml) 기준:

| 서비스 | 이미지 | 포트 | 비고 |
|--------|--------|------|------|
| postgres | pgvector/pgvector:pg17 | 5432 | 벡터 + FTS 통합 |
| redis | redis:7-alpine | 6379 | AOF 지속성 활성화 |
| seaweedfs | chrislusf/seaweedfs:latest | 8333(S3), 8888(UI), 9333(Master) | S3 호환 스토리지 |
| ollama | ollama/ollama:latest | 11434 | 로컬 LLM |
| cross-encoder | (로컬 Dockerfile) | 8001 | `cross-encoder` profile, 선택적 |
| api | (로컬 Dockerfile) | 8080 | `api` profile, Spring Boot |

### 8.2 프로파일

- **기본 (로컬 개발)**: docker compose로 postgres, redis, seaweedfs, ollama 기동 → IntelliJ에서 백엔드(:8080) 실행 → `pnpm -C frontend dev`(:5173) 실행
- **api profile**: 백엔드까지 컨테이너화하여 전체 스택 도커 구동
- **cross-encoder profile**: Cross-Encoder 리랭커 컨테이너 추가 기동

## 9. 설정 관리

### 9.1 환경변수 (docker-dev/.env)

핵심 설정:

| 변수 | 기본값 | 설명 |
|------|--------|------|
| AI_CHAT_PROVIDER | ollama | 채팅 LLM 제공자 (ollama / openai 호환) |
| AI_EMBEDDING_PROVIDER | ollama | 임베딩 제공자 |
| OLLAMA_EMBEDDING_MODEL | bge-m3 | 임베딩 모델 (다국어 지원) |
| NVIDIA_API_KEY | - | NVIDIA NIM 사용 시 필요 |
| S3_ENDPOINT | http://seaweedfs:8333 | S3 호환 스토리지 |
| S3_BUCKET | dochi-bot | 버킷명 |
| CROSS_ENCODER_MODEL | cross-encoder/ms-marco-MiniLM-L6-v2 | 리랭킹 모델 |

### 9.2 프론트엔드 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| VITE_API_BASE_URL | /api/v1 | API Base URL |
| VITE_DEV_BYPASS_AUTH | true | 개발 시 인증 우회 |

### 9.3 관련 파일

- [application.yml](src/main/resources/application.yml)
- [docker-dev/.env](docker-dev/.env)
- [docker-dev/compose.yaml](docker-dev/compose.yaml)
- [frontend/vite.config.ts](frontend/vite.config.ts)

## 10. API 엔드포인트 개요

Base URL: `/api/v1`

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | /auth/login | Public | 로그인 → Access Token + Refresh Token |
| POST | /auth/refresh | Public | Refresh Token으로 Access Token 갱신 |
| POST | /auth/logout | Public | 로그아웃 (쿠키 삭제) |
| GET | /auth/me | Bearer | 현재 사용자 정보 |
| GET | /auth/oauth2/authorize/{provider} | Public | OAuth2 인증 시작 |
| GET | /auth/oauth2/callback/{provider} | Public | OAuth2 콜백 |
| POST | /auth/users | ADMIN | 사용자 생성 |
| GET | /documents/upload-url | ADMIN | 업로드용 Presigned URL |
| POST | /documents | ADMIN | 문서 메타데이터 저장 (finalize) |
| GET | /documents | ADMIN | 문서 목록 |
| GET | /documents/{id} | ADMIN | 문서 단건 |
| GET | /documents/{id}/download-url | 인증 | 다운로드용 Presigned URL |
| POST | /documents/{id}/reindex | ADMIN | 재인덱싱 트리거 |
| GET | /ingestion-jobs | ADMIN | 인제션 작업 목록 |
| POST | /chat | USER | RAG 질의 |
| GET | /health | Public | 헬스 체크 |

### 10.1 관련 파일

- [phase1-api.md](phase1-api.md)
- 각 feature/controller/ 디렉토리

## 11. 테스트

### 11.1 테스트 구조

```
src/test/kotlin/com/dochibot/
├── config/
│   └── application-test.yml         # 테스트 프로파일
├── feature/
│   ├── document/
│   │   └── DocumentUploadPolicyTest.kt
│   └── retrieval/
│       ├── RetrievalBenchmarkTest.kt
│       ├── RetrievalIntegrationTest.kt
│       ├── Phase2EvalRunnerTest.kt
│       ├── rerank/
│       │   ├── CrossEncoderRerankerTest.kt
│       │   └── LlmJudgeRerankerTest.kt
│       ├── verify/
│       │   ├── DefaultEvidenceVerifierTest.kt
│       │   └── QueryTypeClassifierTest.kt
│       └── eval/
│           ├── Phase2EvalScorerTest.kt
│           ├── Phase2EvalValidatorTest.kt
│           └── SyntheticEvalQueryGeneratorTest.kt
├── e2e/
│   ├── RagE2eSmokeTest.kt
│   ├── RagVerifyPassThroughE2eTest.kt
│   └── RagVerifyPolicyE2eTest.kt
└── mock/
    ├── MockCrossEncoderServer.kt
    └── MockDocumentStore.kt
```

### 11.2 테스트 네이밍 규칙

- 단위 테스트: `<Subject>Test.kt`
- E2E: `*E2eTest.kt`
- 벤치마크: `*BenchmarkTest.kt`

## 12. 관련 문서 인덱스

| 문서 | 경로 | 내용 |
|------|------|------|
| PRD | [PRD.md](PRD.md) | 제품 요구사항 (무엇/왜) |
| SPEC | [SPEC.md](SPEC.md) | 기술 설계 (어떻게) |
| API 명세 | [phase1-api.md](phase1-api.md) | 전체 API 상세 |
| DB 스키마 | [db-schema.md](db-schema.md) | 테이블 명세 |
| JWT 인증 | [auth-jwt.md](auth-jwt.md) | 인증 설계 |
| S3 설정 | [s3-config.md](s3-config.md) | 스토리지 설정 |
| DDL | [V1__init.sql](src/main/resources/db/migration/V1__init.sql) | DB 마이그레이션 |
| 애플리케이션 설정 | [application.yml](src/main/resources/application.yml) | 런타임 설정 |
| 인프라 | [docker-dev/compose.yaml](docker-dev/compose.yaml) | 서비스 구성 |
| 환경변수 | [docker-dev/.env](docker-dev/.env) | 설정값 |
