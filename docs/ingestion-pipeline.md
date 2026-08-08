# DochiBot 인제션 파이프라인 (Ingestion Pipeline)

> 마지막 갱신: 2026-08-08, 코드 기준
>
> 문서 업로드부터 벡터 임베딩 저장까지의 전체 흐름을 코드 근거로 설명한다.

## 1. 개요

DochiBot의 인제션 파이프라인은 사용자가 문서를 업로드하면 자동으로 텍스트를 추출하고, 섹션을 파싱하며, 청크로 분할한 뒤 임베딩을 생성하여 PostgreSQL(pgvector)에 저장하는 일련의 비동기 처리 과정이다.

**핵심 설계 결정:**

- Presigned URL 패턴: 백엔드는 파일을 직접 받지 않고, S3(SeaweedFS) Presigned PUT URL을 발급하여 클라이언트가 S3로 직접 업로드
- 폴링 기반 Worker: `@Scheduled`로 주기적으로 QUEUED 상태의 잡을 폴링하여 처리
- 재시도 메커니즘: 재시도 가능한 오류는 백오프 후 자동 재시도, 불가능한 오류는 즉시 FAILED

**관련 파일:**

| 파일 | 역할 |
|------|------|
| `CreateDocumentUploadUrlUseCase.kt` | Presigned PUT URL 발급 |
| `FinalizeDocumentUploadUseCase.kt` | 업로드 완료 처리 및 잡 생성 |
| `IngestionJobWorker.kt` | 주기적 폴링 스케줄러 |
| `IngestionJobService.kt` | 잡 claim / 상태 전이 |
| `DocumentIngestionProcessor.kt` | 메인 처리 오케스트레이터 |
| `DocumentContentLoader.kt` | S3에서 원본 바이트 로드 |
| `DocumentTextExtractor.kt` | PDF / Markdown 텍스트 추출 |
| `MarkdownSectionParser.kt` | Markdown heading 기반 섹션 분할 |
| `TextChunkingService.kt` | 텍스트 → 청크 분할 |
| `DocumentIndexWriter.kt` | sections / chunks DB 저장 |
| `DochibotIngestionProperties.kt` | 인제션 설정값 |
| `DochibotAiProperties.kt` | 임베딩 모델 설정 |

---

## 2. 전체 흐름

```
[클라이언트]                    [백엔드]                      [S3/SeaweedFS]           [Worker]
    |                              |                              |                      |
    |-- POST /upload-url --------->|                              |                      |
    |   (파일명, Content-Type)     |-- Presigned PUT URL 생성 -->|                      |
    |<-- {uploadUrl, documentId} --|                              |                      |
    |                              |                              |                      |
    |-- PUT uploadUrl ------------>|                              |                      |
    |   (native fetch, S3 직접)   |                              |                      |
    |                              |                              |                      |
    |-- POST /documents (finalize)>|                              |                      |
    |   {documentId, storageUri}   |-- 문서 레코드 생성 --------->|                      |
    |                              |-- IngestionJob 생성 (QUEUED) |                      |
    |<-- {documentId, jobId} ------|                              |                      |
    |                              |                              |                      |
    |                              |                              |     [3초 주기 폴링]    |
    |                              |                              |<-- findNextQueuedJob() |
    |                              |                              |-- claimJob(RUNNING) -->|
    |                              |                              |     [문서 처리...]     |
    |                              |                              |                      |
    |                              |                              |     markSucceeded()    |
    |                              |                              |     or markFailed()    |
    |                              |                              |                      |
```

---

## 3. 단계별 상세

### 3.1 업로드 URL 발급 (`CreateDocumentUploadUrlUseCase`)

**진입점:** `POST /api/v1/documents/upload-url`

1. 클라이언트가 `{originalFilename, contentType}`을 전송
2. `DocumentUploadPolicy.isSupportedUpload()`로 확장자 + MIME 타입 검증
   - 허용: `.pdf` (`application/pdf`), `.md`/`.markdown` (`text/markdown`, `text/x-markdown`, `text/plain`)
   - 불허 시 `400 BAD_REQUEST` 반환
3. UUID v7로 `documentId` 사전 생성
4. S3 객체 키 생성: `{yyyy}/{MM}/{documentId}_{originalFilename}`
5. Presigned PUT URL 발급 (기본 만료: 600초, `s3.presigned-url-expiration-seconds`)

**응답 필드:**

| 필드 | 설명 |
|------|------|
| `documentId` | 사전 생성된 문서 UUID |
| `uploadUrl` | Presigned PUT URL |
| `storageUri` | `s3://{bucket}/{key}` 형식의 스토리지 URI |
| `expiresInSeconds` | URL 만료 시간 (기본 600초) |
| `requiredHeaders` | 업로드 시 필요한 헤더 (`Content-Type`) |

---

### 3.2 S3 직접 업로드 (클라이언트)

- 클라이언트는 `native fetch`를 사용하여 Presigned URL로 PUT 요청
- `ky`가 아닌 `native fetch`를 사용하는 이유: base URL이 S3 엔드포인트로 다르기 때문
- `Content-Type` 헤더를 Presigned URL 발급 시 지정한 값과 일치시켜야 함

---

### 3.3 업로드 완료 처리 (`FinalizeDocumentUploadUseCase`)

**진입점:** `POST /api/v1/documents` (finalize)

1. `storageUri`의 bucket이 설정된 `s3.bucket`과 일치하는지 검증
2. 객체 키 규칙 검증: `{yyyy}/{MM}/{documentId}_{filename}` 패턴 확인
3. `sourceType`과 파일 확장자 일치 검증 (`.pdf` → `PDF`, `.md` → `TEXT`)
4. `createdByUserId` 추출: JWT subject → UUID 파싱
5. 트랜잭션 내에서:
   - `Document` 엔티티 생성 (status=`PENDING`, sourceType, storageUri 등 기록)
   - `DocumentIngestionJob` 생성 (status=`QUEUED`, embeddingModel/dims 기록)
   - 중복 문서 감지: `DuplicateKeyException` 발생 시 `409 CONFLICT` 반환

**응답:**

```json
{
  "documentId": "...",
  "status": "PENDING",
  "ingestionJobId": "..."
}
```

---

### 3.4 Worker 폴링 (`IngestionJobWorker`)

- `@Scheduled(fixedDelayString = "${dochibot.ingestion.worker.fixed-delay-ms:3000}")`로 3초 주기 폴링
- `dochibot.ingestion.worker.enabled=false`로 비활성화 가능
- 매 폴링마다 `maxJobsPerRun` (기본 1) 개의 잡을 처리

---

### 3.5 잡 Claim (`IngestionJobService.claimNextQueuedJob()`)

```sql
-- 1) QUEUED 상태이며 next_run_at이 null이거나 현재 시각 이하인 잡 중 가장 오래된 1건 조회
SELECT * FROM document_ingestion_jobs
WHERE status = 'QUEUED'
  AND (next_run_at IS NULL OR next_run_at <= now())
ORDER BY created_at ASC LIMIT 1;

-- 2) 낙관적 락(Optimistic Lock)으로 RUNNING으로 상태 변경
UPDATE document_ingestion_jobs
SET status = 'RUNNING', started_at = :startedAt, updated_at = now()
WHERE id = :id AND status = 'QUEUED';

-- 3) claim 확인: 실제로 RUNNING + started_at 일치 시에만 처리 진행
```

`WHERE status = 'QUEUED'` 조건을 통한 낙관적 동시성 제어로, 여러 Worker 인스턴스가 같은 잡을 중복 처리하지 않는다.

---

### 3.6 메인 처리 (`DocumentIngestionProcessor.process()`)

`processBatch()`가 루프를 돌며 `claimNextQueuedJob()` → `process()` → 예외 처리 순으로 진행한다.

#### 3.6.1 사전 검증

- 문서의 `storageUri`가 존재하는지 확인
- 잡의 `embeddingModel`이 현재 설정과 일치하는지 확인 (불일치 시 `NonRetryableIngestionException`)
- 잡의 `embeddingDims`가 현재 설정과 일치하는지 확인 (불일치 시 `NonRetryableIngestionException`)
- 문서 상태를 `PROCESSING`으로 갱신

#### 3.6.2 문서 로드 (`DocumentContentLoader`)

- `S3DocumentContentLoader`가 `storageUri`를 파싱하여 bucket/key 추출
- `HeadObject`로 파일 크기 확인 후 `maxBytes`(기본 50MB) 초과 시 `NonRetryableIngestionException`
- `GetObject`로 바이트 배열 전체 로드, 2차 크기 검증

#### 3.6.3 텍스트 추출 (`DocumentTextExtractor`)

- **TEXT (Markdown):** UTF-8 디코딩 → `MarkdownSectionParser.parse()`
- **PDF:** Apache PDFBox로 페이지별 텍스트 추출
  - `sortByPosition = true`로 레이아웃 순서 유지
  - 각 페이지를 level=1 섹션으로 생성 (`heading = "Page {N}"`, `sectionPath = "{문서명} > Page {N}"`)
  - `canExtractContent()` 권한 확인, 불가 시 예외

**MarkdownSectionParser 동작:**

1. heading (`#`~`######`) 기준으로 계층적 섹션 트리 구축
2. heading 이전 텍스트는 intro 섹션(문서명과 동일한 heading)으로 처리
3. 인라인 마크다운 제거: 링크, 이미지, 인라인 코드, 강조(`*_~`), HTML 태그, 테이블 구분선
4. 블록 요소 제거: `> ` (인용), `- `/`* ` (순서 없는 리스트), `1. ` (순서 리스트)
5. 코드 펜스(` ``` `) 내부는 공백 정규화만 적용
6. 최종 섹션이 하나도 없으면 전체 텍스트를 단일 섹션으로 처리

`ExtractedSection` 구조:

| 필드 | 설명 |
|------|------|
| `index` | 문서 내 섹션 순번 (0부터) |
| `parentIndex` | 부모 섹션 index (nullable) |
| `level` | heading 레벨 (1~6) |
| `heading` | 섹션 제목 |
| `sectionPath` | `문서명 > 상위섹션 > 현재섹션` 경로 |
| `page` | PDF 페이지 번호 (PDF만, Markdown은 null) |
| `text` | 정규화된 섹션 본문 |

#### 3.6.4 청킹 (`TextChunkingService`)

**파라미터 (기본값):**

| 파라미터 | 기본값 | 설정 키 |
|----------|--------|---------|
| `chunkSize` | 1200자 | `dochibot.ingestion.chunking.chunk-size` |
| `chunkOverlap` | 200자 | `dochibot.ingestion.chunking.chunk-overlap` |
| `maxEmbeddingInputChars` | 400자 | `dochibot.ingestion.chunking.max-embedding-input-chars` |

**청크 크기 계산 로직:**

```
baseChunkSize = min(chunkSize, maxEmbeddingInputChars)   // 400
prefixLength = "[D] {title}\n[S] {sectionPath}\n[B] ".length
reservedChars = min(prefixLength, baseChunkSize - 50)     // 인코딩 오버헤드 예약
effectiveChunkSize = max(1, baseChunkSize - reservedChars)
overlap = min(chunkOverlap, effectiveChunkSize - 1)
```

즉, 실제 청크 텍스트는 prefix를 제외한 `effectiveChunkSize` 이내로 슬라이딩 윈도우 방식으로 분할된다.

**후처리:** 마지막 청크가 `chunkSize/2` 미만이고 이전 청크와 합쳐도 `chunkSize` 이내면 병합 (꼬리 조각 방지).

**청크 텍스트 인코딩 (`SearchableChunkTextCodec`):**

```
[D] 문서 제목
[S] 문서명 > Section > SubSection
[B] 본문 텍스트...
```

이 형식으로 `chunks.text` 컬럼에 저장되어, 전문 검색(tsvector) 시 문서명과 섹션 경로까지 매칭된다.

#### 3.6.5 임베딩 생성

- 인코딩된 청크 텍스트를 64개씩 배치로 나누어 `EmbeddingModel.embed()` 호출 (IO Dispatcher에서 실행)
- 임베딩 차원(dims)이 `dochibot.ai.embedding.dims`(기본 1024)와 일치하는지 검증
- 청크 수와 임베딩 수 일치 검증

**임베딩 모델 설정:**

| 설정 | 기본값 | 비고 |
|------|--------|------|
| `dochibot.ai.embedding.model` | `bge-m3` | `AI_EMBEDDING_MODEL` 환경변수로 재정의 (fallback: `OLLAMA_EMBEDDING_MODEL`) |
| `dochibot.ai.embedding.dims` | `1024` | `AI_EMBEDDING_DIMS` 환경변수로 재정의 |

> Spring AI의 Ollama `embedding.options.model`도 `bge-m3`로 설정되어 있으나, DochiBot은 `dochibot.ai.embedding.model`을 우선 사용한다. 양쪽이 일치해야 하므로 환경변수 연동(`AI_EMBEDDING_MODEL` → `OLLAMA_EMBEDDING_MODEL`)이 적용되어 있다.

#### 3.6.6 DB 저장 (`DocumentIndexWriter`)

**순서:**

1. **기존 데이터 삭제:** 재처리(reindex) 시 중복을 방지하기 위해 `chunks` → `sections` 순서로 기존 데이터를 먼저 삭제
2. **섹션 저장:** `insertSections()`로 섹션을 개별 INSERT (parent_id는 이미 저장된 섹션의 ID를 참조하므로 순차 처리)
3. **섹션 임베딩 계산:** 각 섹션에 속한 청크 임베딩의 평균을 계산하여 `updateSectionEmbedding()`으로 갱신
4. **청크 저장:** `insertChunks()`로 청크 개별 INSERT (각 청크에 pgvector 리터럴 `[0.1,0.2,...]` 형식으로 임베딩 저장)

---

### 3.7 상태 전이 다이어그램

```
문서 상태 (documents.status):
  PENDING → PROCESSING → COMPLETED
              ↓
            FAILED

인제션 잡 상태 (document_ingestion_jobs.status):
  QUEUED → RUNNING → SUCCEEDED
              ↓
           (재시도 가능) → QUEUED (attempt_count++, next_run_at 설정)
              ↓
           (재시도 불가) → FAILED
```

---

### 3.8 재시도 및 실패 처리

**`processBatch()` 예외 핸들링:**

```
try { process(job) } catch (ex) {
    message = ex.message.take(500)
    shouldRetry = ex !is NonRetryableIngestionException  // 재시도 불가 예외가 아니고
               && canRetry(job)                          // attemptCount < maxAttempts

    if (shouldRetry) {
        scheduleRetry(job, message, nextRunAt)  // QUEUED로 복귀, 백오프 설정
        document.status = PROCESSING             // 문서 상태는 PROCESSING 유지
    } else {
        markFailed(job.id, message)              // FAILED로 전환
        document.status = FAILED
    }
}
```

**재시도 불가 예외 (`NonRetryableIngestionException`):**

- 파일 크기 초과 (50MB)
- 임베딩 모델/차원 불일치
- 임베딩 차원과 DB 스키마 불일치

**백오프 전략 (`calculateBackoff()`):**

| 시도 횟수 | 대기 시간 |
|-----------|-----------|
| 1회차 | 60초 |
| 2회차 | 300초 (5분) |
| 3회차 이상 | 1800초 (30분) |

`scheduleRetry()`는 `next_run_at`을 `now + backoff`로 설정하고, `started_at`, `finished_at`, `chunk_count`, `embedding_model`, `embedding_dims`를 초기화하여 QUEUED로 복귀시킨다.

**최대 재시도:** `max_attempts` (기본 3회). 초과 시 `FAILED`로 최종 전환.

---

### 3.9 Reindex (`ReindexDocumentUseCase`)

문서 재처리가 요청되면:
- 기존 `DocumentIngestionJob`을 새로 생성 (QUEUED)
- 문서 상태를 `PENDING`으로 재설정
- 이후 일반 인제션 파이프라인과 동일하게 처리

---

## 4. 설정값 요약

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `dochibot.ingestion.worker.enabled` | `true` | Worker 활성화 여부 |
| `dochibot.ingestion.worker.fixed-delay-ms` | `3000` | 폴링 주기 (ms) |
| `dochibot.ingestion.worker.max-jobs-per-run` | `1` | 1회 폴링당 최대 처리 잡 수 |
| `dochibot.ingestion.chunking.chunk-size` | `1200` | 청크 최대 길이 (문자) |
| `dochibot.ingestion.chunking.chunk-overlap` | `200` | 청크 간 중복 길이 (문자) |
| `dochibot.ingestion.chunking.max-embedding-input-chars` | `400` | 임베딩 모델 입력 제한을 고려한 최대 청크 길이 (문자) |
| `dochibot.ingestion.content.max-bytes` | `50000000` | 최대 파일 크기 (50MB) |
| `dochibot.ai.embedding.model` | `bge-m3` | 임베딩 모델명 |
| `dochibot.ai.embedding.dims` | `1024` | 임베딩 차원 |
| `s3.presigned-url-expiration-seconds` | `600` | Presigned URL 만료 시간 (초) |
