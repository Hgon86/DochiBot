# Phase 1 개발 로드맵

> Phase 1 목표: 내부 문서 기반 RAG 운영 백엔드(인입/인덱싱/검색/관측/재처리) + 얇은 데모 UI (1주 MVP)

## 📊 전체 일정 개요

| 순서 | 단계 | 핵심 산출물 | 예상 시간 |
|------|------|------------|----------|
| 1 | 프로젝트 설정 | 빌드/설정 파일 | 1-2시간 |
| 2 | DB 마이그레이션 | Flyway 스크립트 | 30분 |
| 3 | 도메인/리포지토리 | R2DBC Entity/Repository | 1시간 |
| 4 | 인증/인가 | JWT Security | 2시간 |
| 5 | S3 서비스 | Presigned URL | 1시간 |
| 6 | 문서 API | 업로드/목록/다운로드 | 2시간 |
| 7 | 인덱싱 파이프라인 | PDF → ES | 3시간 |
| 8 | Chat/RAG API | 질의/응답 | 2시간 |
| 9 | 테스트/버그수정 | 검증 | 2시간 |
| 10 | 배포 설정 | Docker Compose | 1시간 |

**총 예상: 약 15-16시간 (2일 작업 분량)**

---

## 📋 상세 작업 순서

### Step 1: 프로젝트 설정 (1-2시간)

**목표**: Spring Boot 프로젝트 기본 구조 완성

**작업 내용**:
- [ ] `build.gradle.kts` 의존성 확인 (WebFlux, R2DBC, Spring AI, S3)
- [ ] `application.yml` 환경변수 설정
- [ ] Main Application Class (`DochiBotApplication.kt`)
- [ ] Kotlin Coroutines 설정 확인

**파일**:
```
src/main/kotlin/com/dochi-bot/DochiBotApplication.kt
src/main/resources/application.yml
```

**검증**:
```bash
./gradlew build
./gradlew bootRun
```

---

### Step 2: DB 마이그레이션 (30분)

**목표**: PostgreSQL 테이블 생성

**작업 내용**:
- [ ] `src/main/resources/db/migration/V1__init.sql` 생성
- [ ] 기존 `db/ddl/001_init.sql` 참고하여 마이그레이션 스크립트 작성
- [ ] Flyway 설정 확인

**파일**:
```
src/main/resources/db/migration/V1__init.sql
```

**SQL 예시** (참고):
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    original_filename VARCHAR(512),
    storage_uri TEXT,
    content_sha256 CHAR(64) UNIQUE NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

---

### Step 3: 도메인/리포지토리(R2DBC) (1시간)

**목표**: Kotlin 데이터 모델 + R2DBC Repository 생성

**작업 내용**:
- [ ] 도메인 패키지 구조 생성
- [ ] `User` 모델 + `UserRepository` (ReactiveCrudRepository)
- [ ] `Document` 모델 + `DocumentRepository`
- [ ] `DocumentIngestionJob` 모델 + `DocumentIngestionJobRepository`
- [ ] (선택) `ChatSession`, `ChatMessage`

**패키지 구조**:
```
src/main/kotlin/com/dochi-bot/
└── domain/
    ├── entity/
    │   ├── User.kt
    │   └── Document.kt
    └── enums/
        ├── UserRole.kt
        ├── DocumentStatus.kt
        └── SourceType.kt
```

**모델 예시**:
```kotlin
data class Document(
    val id: UUID,
    val title: String,
    val sourceType: SourceType,
    val originalFilename: String?,
    val storageUri: String?,
    val contentSha256: String,
    val status: DocumentStatus,
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
```

---

### Step 4: 인증/인가 - JWT Security (2시간)

**목표**: JWT 기반 인증 구현

**작업 내용**:
- [ ] Security 설정 (`SecurityConfig.kt`)
- [ ] JWT Utility (`JwtUtils.kt`)
- [ ] Authentication Filter (`JwtAuthenticationFilter.kt`)
- [ ] UserDetailsService 구현 (`UserDetailsServiceImpl.kt`)
- [ ] Auth Controller (`AuthController.kt`)

**엔드포인트**:
- `POST /api/v1/auth/login` - 로그인
- `GET /api/v1/auth/me` - 내 정보

**패키지**:
```
src/main/kotlin/com/dochi-bot/
└── auth/
    ├── config/
    │   └── SecurityConfig.kt
    ├── filter/
    │   └── JwtAuthenticationFilter.kt
    ├── service/
    │   └── UserDetailsServiceImpl.kt
    ├── util/
    │   └── JwtUtils.kt
    └── controller/
        └── AuthController.kt
```

**순서**:
1. JWT Utility (서명/검증/만료)
2. UserDetailsService (DB에서 사용자 조회)
3. Filter (요청에서 JWT 추출→인증)
4. Security Config (인가 규칙)
5. Controller (로그인 API)

---

### Step 5: S3 서비스 - Presigned URL (1시간)

**목표**: S3/MinIO Presigned URL 발급

**작업 내용**:
- [ ] S3 Configuration (`S3Config.kt`)
- [ ] S3 Service (`S3Service.kt`)

**패키지**:
```
src/main/kotlin/com/dochi-bot/
└── config/
    └── S3Config.kt
└── service/
    └── S3Service.kt
```

**S3Service 메서드**:
```kotlin
suspend fun generateUploadUrl(
    documentId: UUID,
    filename: String,
    contentType: String
): PresignedUploadUrl

suspend fun generateDownloadUrl(
    documentId: UUID,
    storageUri: String
): PresignedDownloadUrl

suspend fun uploadFile(
    documentId: UUID,
    file: File,
    contentType: String
): StorageUri
```

---

### Step 6: 문서 API - 업로드/목록 (2시간)

**목표**: Document CRUD API 구현

**작업 내용**:
- [ ] Document Repository
- [ ] Document Service
- [ ] Document Controller

**엔드포인트**:
- `GET /api/v1/documents/upload-url` - Presigned URL 발급 (업로드용)
- `POST /api/v1/documents` - 문서 메타데이터 저장
- `GET /api/v1/documents` - 문서 목록
- `GET /api/v1/documents/{id}` - 문서 단건
- `GET /api/v1/documents/{id}/download-url` - Presigned URL 발급 (다운로드용)

**패키지**:
```
src/main/kotlin/com/dochi-bot/
└── document/
    ├── repository/
    │   └── DocumentRepository.kt
    ├── service/
    │   └── DocumentService.kt
    ├── controller/
    │   └── DocumentController.kt
    └── dto/
        ├── CreateDocumentRequest.kt
        └── DocumentResponse.kt
```

**순서**:
1. Repository (R2DBC)
2. Service (업로드 URL 발급, 메타데이터 저장)
3. Controller (API 구현)

---

### Step 7: 인덱싱 파이프라인 (3시간)

**목표**: PDF → 텍스트 추출 → 청킹 → 임베딩 → Elasticsearch

**작업 내용**:
- [ ] PDF Text Extractor (`PdfExtractorService.kt`)
- [ ] Chunking Service (`ChunkingService.kt`)
- [ ] Embedding Service (`EmbeddingService.kt` - Spring AI Ollama)
- [ ] Elasticsearch Repository (`DocumentChunkRepository.kt`)
- [ ] Ingestion Job Service (`IngestionJobService.kt`)

**패키지**:
```
src/main/kotlin/com/dochi-bot/
└── ingestion/
    ├── extractor/
    │   └── PdfExtractorService.kt
    ├── chunking/
    │   └── ChunkingService.kt
    ├── embedding/
    │   └── EmbeddingService.kt
    ├── vectorstore/
    │   └── ElasticsearchVectorStoreConfig.kt
    └── service/
        └── IngestionJobService.kt
```

**데이터 흐름**:
```
1. S3에서 PDF 다운로드
2. PDF → 텍스트 추출 (Apache PDFBox)
3. 텍스트 → 청킹 (400-800 토큰, 15% 오버랩)
4. 청크 → 임베딩 생성 (Ollama nomic-embed-text)
5. Elasticsearch Vector Store에 저장
```

---

### Step 8: Chat/RAG API (2시간)

**목표**: 질의 → 검색 → LLM 답변 생성

**작업 내용**:
- [ ] Chat Controller (`ChatController.kt`)
- [ ] Chat Service (`ChatService.kt`)
- [ ] Retrieval Service (`RetrievalService.kt` - 하이브리드 검색)
- [ ] Prompt Template (`ChatPromptTemplates.kt`)

**엔드포인트**:
- `POST /api/v1/chat` - 질의

**패키지**:
```
src/main/kotlin/com/dochi-bot/
└── chat/
    ├── controller/
    │   └── ChatController.kt
    ├── service/
    │   ├── ChatService.kt
    │   └── RetrievalService.kt
    ├── model/
    │   ├── ChatRequest.kt
    │   ├── ChatResponse.kt
    │   └── Citation.kt
    └── prompt/
        └── ChatPromptTemplates.kt
```

**RAG 흐름**:
```
1. 사용자 질문 수신
2. 질문 → 임베딩 생성
3. Elasticsearch에서 Top-K 검색 (하이브리드: 벡터 + BM25)
4. 컨텍스트 구성 + 프롬프트 생성
5. Ollama (llama3.2)로 답변 생성
6. 근거(citations)와 함께 응답 반환
```

---

### Step 9: 테스트/버그수정 (2시간)

**목표**: 통합 테스트 및 버그 수정

**작업 내용**:
- [ ] 단위 테스트 (Service Layer)
- [ ] 통합 테스트 (Controller + DB)
- [ ] E2E 테스트 (전체 플로우)
- [ ] 버그 수정

**테스트 파일**:
```
src/test/kotlin/com/dochi-bot/
├── auth/
│   └── AuthControllerTest.kt
├── document/
│   └── DocumentServiceTest.kt
├── chat/
│   └── ChatServiceTest.kt
└── ingestion/
    └── IngestionJobServiceTest.kt
```

---

### Step 10: Docker Compose 배포 설정 (1시간)

**목표**: 로컬/RPi 배포용 Docker Compose 설정

**작업 내용**:
- [ ] `docker-compose.yml` 작성
- [ ] `Dockerfile` 작성 (Spring Boot)
- [ ] 환경변수 설정 확인

**docker-compose.yml**:
```yaml
services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=rpi
      - S3_ENDPOINT=http://minio:9000
    depends_on:
      - postgres
      - elasticsearch
      - minio

  postgres:
    image: postgres:16
    environment:
      - POSTGRES_USER=dochibot
      - POSTGRES_PASSWORD=password
      - POSTGRES_DB=dochi_bot

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - discovery.type=single-node

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"

  ollama:
    image: ollama/ollama
    ports:
      - "11434:11434"
```

---

## 🎯 완료 기준 (Acceptance Criteria)

1. [ ] `./gradlew build` 성공
2. [ ] `./gradlew test` 통과
3. [ ] `./gradlew bootRun` 으로 서버 기동 가능
4. [ ] Docker Compose로 전체 스택 기동 가능
5. [ ] JWT 로그인 → 토큰 발급
6. [ ] 문서 업로드 → S3 저장 → ES 인덱싱
7. [ ] Chat API → 근거 기반 답변 생성

---

## 📁 의존성 순서도

```
┌─────────────────────────────────────────────────────────────┐
│                        Phase 1 의존성 순서                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 프로젝트 설정 ──────────┐                                │
│  2. DB 마이그레이션 ────────┤                                │
│  3. 도메인 엔티티 ──────────┤                                │
│  4. JWT Security ──────────┤  인증 기반                      │
│                            ▼                                │
│  5. S3 Presigned URL ──────┤  파일 저장                      │
│                            ▼                                │
│  6. 문서 API ──────────────┤  CRUD                          │
│                            ▼                                │
│  7. 인덱싱 파이프라인 ──────┤  검색 준비                      │
│                            ▼                                │
│  8. Chat/RAG API ──────────┤  핵심 기능                      │
│                            ▼                                │
│  9. 테스트/버그수정 ────────┤  검증                          │
│                            ▼                                │
│  10. Docker 배포 ──────────┘  운영                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔗 관련 문서

- [SPEC.md](../SPEC.md) - 전체 프로젝트 명세
- [docs/phase1-api.md](phase1-api.md) - API 상세 명세
- [docs/db-schema.md](db-schema.md) - DB 스키마
- [docs/s3-config.md](s3-config.md) - S3 설정 가이드
- [docs/auth-jwt.md](auth-jwt.md) - JWT 인증 설계
