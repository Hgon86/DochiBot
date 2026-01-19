# Phase 1 API 상세 명세 (v1)

> Base URL: `/api/v1`

## 0. 공통
### 0.1 인증
- 인증 필요 API는 헤더로 JWT 전달
  - `Authorization: Bearer <token>`

### 0.2 요청 추적/관측성 (권장)
- 모든 API 응답 헤더에 `X-Request-Id`를 포함한다.
- 클라이언트가 `X-Request-Id`를 전달하면 서버는 해당 값을 재사용하고, 없으면 서버가 생성한다.
- 서버 로그는 최소한 아래를 포함한다.
  - `request_id`, `path`, `latency_ms`
  - (chat) `topK`, 검색 결과 개수, 모델명
  - (ingestion) 문서 id, 청크 수, 임베딩 모델, 실패 사유

### 0.3 공통 에러 응답(권장)
```json
{
  "code": "STRING_CODE",
  "message": "사람이 읽는 메시지",
  "detail": "옵션(디버그용)"
}
```

권장 코드:
- `AUTH_REQUIRED` (401)
- `AUTH_INVALID_TOKEN` (401)
- `AUTH_FORBIDDEN` (403)
- `VALIDATION_ERROR` (400)
- `NOT_FOUND` (404)
- `CONFLICT` (409)
- `INTERNAL_ERROR` (500)


## 1. Auth

### 1.1 로그인
- `POST /auth/login`
- Auth: 없음

Request:
```json
{
  "username": "string",
  "password": "string"
}
```

Response 200:
```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresInSeconds": 43200,
  "user": {
    "id": "uuid",
    "username": "string",
    "role": "ADMIN|USER"
  }
}
```

Response 401:
- `AUTH_INVALID_CREDENTIALS`


### 1.2 내 정보
- `GET /auth/me`
- Auth: 필요

Response 200:
```json
{
  "id": "uuid",
  "username": "string",
  "role": "ADMIN|USER"
}
```


### 1.3 사용자 생성(관리자)
- `POST /auth/users`
- Auth: 필요(ADMIN)

Request:
```json
{
  "username": "string",
  "password": "string",
  "role": "ADMIN|USER"
}
```

Response 201:
```json
{
  "id": "uuid",
  "username": "string",
  "role": "ADMIN|USER",
  "createdAt": "timestamptz"
}
```

Response 409:
- `CONFLICT_USERNAME`


## 2. Documents (업로드/인덱싱)

> ⚠️ **업로드 방식 변경 (Phase 1)**: 파일은 **Presigned URL** 방식으로 S3에 직접 업로드됩니다.

### 2.1 Presigned URL 발급 (업로드용)
- `GET /documents/upload-url`
- Auth: 필요(ADMIN)

Query:
- `filename`: 원본 파일명 (예: `manual.pdf`)
- `contentType`: MIME 타입 (예: `application/pdf`)

Response 200:
```json
{
  "uploadUrl": "https://s3.amazonaws.com/dochi-bot/2026/01/...?X-Amz-Algorithm=...",
  "storageUri": "s3://dochi-bot/2026/01/{uuid}_manual.pdf",
  "expiresInSeconds": 900,
  "documentId": "uuid"
}
```

비고:
1. 클라이언트는 `uploadUrl`로 파일을 S3에 직접 PUT
2. 업로드 완료 후 `POST /documents`로 메타데이터 저장 요청

---

### 2.2 문서 메타데이터 저장
- `POST /documents`
- Auth: 필요(ADMIN)

Request:
```json
{
  "title": "string",
  "filename": "string",
  "storageUri": "s3://dochi-bot/2026/01/{uuid}_filename.pdf",
  "contentType": "application/pdf",
  "contentSha256": "string"
}
```

Response 201:
```json
{
  "documentId": "uuid",
  "status": "PENDING|PROCESSING|COMPLETED|FAILED"
}
```

비고:
- 인덱싱은 비동기로 진행 (`PENDING` → `PROCESSING` → `COMPLETED`)

---

### 2.3 Presigned URL 발급 (다운로드용)
- `GET /documents/{documentId}/download-url`
- Auth: 필요(ADMIN 또는 USER)

Response 200:
```json
{
  "downloadUrl": "https://s3.amazonaws.com/dochi-bot/2026/01/...?X-Amz-Algorithm=...",
  "expiresInSeconds": 900
}
```

---

### 2.4 문서 목록
- `GET /documents`
- Auth: 필요(ADMIN)

Query(옵션):
- `status`: `PENDING|PROCESSING|COMPLETED|FAILED`

Response 200:
```json
{
  "items": [
    {
      "id": "uuid",
      "title": "string",
      "sourceType": "PDF|TEXT",
      "status": "PENDING|PROCESSING|COMPLETED|FAILED",
      "createdAt": "timestamptz",
      "updatedAt": "timestamptz"
    }
  ]
}
```


### 2.5 문서 단건
- `GET /documents/{documentId}`
- Auth: 필요(ADMIN)

Response 200:
```json
{
  "id": "uuid",
  "title": "string",
  "sourceType": "PDF|TEXT",
  "originalFilename": "string|null",
  "status": "PENDING|PROCESSING|COMPLETED|FAILED",
  "errorMessage": "string|null",
  "createdAt": "timestamptz",
  "updatedAt": "timestamptz"
}
```


### 2.6 재인덱싱 트리거
- `POST /documents/{documentId}/reindex`
- Auth: 필요(ADMIN)

Response 202:
```json
{
  "jobId": "uuid",
  "status": "QUEUED|RUNNING|SUCCEEDED|FAILED"
}
```


## 3. Ingestion Jobs

### 3.1 작업 목록
- `GET /ingestion-jobs`
- Auth: 필요(ADMIN)

Query(옵션):
- `documentId`: uuid

Response 200:
```json
{
  "items": [
    {
      "id": "uuid",
      "documentId": "uuid",
      "status": "QUEUED|RUNNING|SUCCEEDED|FAILED",
      "chunkCount": 123,
      "embeddingModel": "string|null",
      "esIndexName": "dochi_docs_v1",
      "startedAt": "timestamptz|null",
      "finishedAt": "timestamptz|null",
      "errorMessage": "string|null",
      "createdAt": "timestamptz"
    }
  ]
}
```


## 4. Chat (RAG)

### 4.1 질의
- `POST /chat`
- Auth: 필요(USER 이상)

Request:
```json
{
  "message": "string",
  "sessionId": "string|null",
  "topK": 5
}
```

Response 200:
```json
{
  "answer": "string (markdown)",
  "citations": [
    {
      "documentId": "uuid",
      "documentTitle": "string",
      "snippet": "string",
      "page": 3,
      "section": "string|null",
      "score": 0.123
    }
  ]
}
```

비고:
- `snippet`은 필수, `page`/`section`은 가능하면 포함

비고:
- 근거가 없으면 `citations`를 빈 배열로 반환하고, 답변은 “문서에서 찾을 수 없음” 정책을 따른다.


## 5. Health (옵션)
- `GET /health`
- Auth: 없음 또는 내부망 기준 허용

Response 200:
```json
{
  "status": "UP"
}
```
