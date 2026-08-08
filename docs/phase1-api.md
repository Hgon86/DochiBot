# Phase 1 API 상세 명세 (v1)

> 마지막 갱신: 2026-08-08, 코드 기준
> Base URL: `/api/v1`

## 0. 공통

### 0.1 인증
- 인증 필요 API는 헤더로 JWT 전달
  - `Authorization: Bearer <token>`
- 인증 필요 여부는 각 엔드포인트별로 명시

### 0.2 공통 에러 응답

모든 API는 오류 발생 시 아래 형식으로 응답한다.

```json
{
  "timestamp": "2026-08-08T12:00:00Z",
  "status": 400,
  "code": "STRING_CODE",
  "message": "사람이 읽는 메시지 (영문)",
  "i18nKey": "error.string_code",
  "i18nArgs": [],
  "detail": "디버그용 상세 (local/docker 프로파일에서만 노출)",
  "errors": [
    {
      "field": "username",
      "value": "",
      "reason": "must not be blank"
    }
  ],
  "traceId": "uuid"
}
```

필드 설명:
- `timestamp`: 오류 발생 시각 (ISO 8601)
- `status`: HTTP 상태 코드
- `code`: 클라이언트가 분기 처리할 문자열 코드
- `message`: 영문 기본 메시지
- `i18nKey` / `i18nArgs`: 다국어 지원 키와 인자 (null 가능)
- `detail`: 디버그용 상세 (local/docker 프로파일에서만 포함, 운영에서는 null)
- `errors`: Bean Validation 실패 시 필드별 오류 목록 (그 외에는 빈 배열)
- `traceId`: 요청 추적 ID (`X-Request-Id` 헤더 값 또는 서버 생성)

공통 에러 코드:
- `AUTH_REQUIRED` (401) — Authorization 헤더 누락
- `AUTH_INVALID_TOKEN` (401) — JWT 만료 또는 서명 불일치
- `AUTH_FORBIDDEN` (403) — 역할 부족 (예: USER가 ADMIN 전용 엔드포인트 호출)
- `VALIDATION_ERROR` (400) — 요청 바디/파라미터 검증 실패
- `NOT_FOUND` (404) — 리소스 없음
- `CONFLICT` (409) — 중복 등 충돌
- `BAD_REQUEST` (400) — 일반적인 잘못된 요청
- `INTERNAL_ERROR` (500) — 예기치 못한 서버 오류

그 외 feature별 에러 코드는 각 섹션에서 명시.


## 1. Auth

### 1.1 로그인
- `POST /auth/login`
- Auth: 불필요 (public)

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
    "role": "ADMIN|USER",
    "provider": "CREDENTIALS|GOOGLE|GITHUB"
  }
}
```

- `Set-Cookie` 헤더로 `refresh_token` 쿠키(HttpOnly, Secure, SameSite=Lax)도 함께 발급된다.
- `expiresInSeconds`는 Access Token 만료 시간(초).

Response 401:
- `AUTH_INVALID_CREDENTIALS` — 아이디 또는 비밀번호 불일치
- `AUTH_USER_INACTIVE` — 비활성화된 계정


### 1.2 토큰 갱신
- `POST /auth/refresh`
- Auth: 불필요 (public, Cookie 기반)

Request: body 없음. `refresh_token` 쿠키를 함께 전송한다.

Response 200:
```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresInSeconds": 43200
}
```

- `Set-Cookie` 헤더로 새 `refresh_token` 쿠키도 함께 발급된다.

Response 401:
- `AUTH_REFRESH_TOKEN_INVALID` — refresh_token 쿠키 누락 또는 무효
- `AUTH_REFRESH_TOKEN_EXPIRED` — refresh_token 만료


### 1.3 로그아웃
- `POST /auth/logout`
- Auth: 불필요 (public, Cookie 기반)

Request: body 없음. `refresh_token` 쿠키를 함께 전송한다.

Response 200:
```json
{
  "success": true
}
```

- `Set-Cookie` 헤더로 `refresh_token` 쿠키를 만료시킨다(삭제).
- 쿠키가 없어도 200을 반환한다.


### 1.4 내 정보 조회
- `GET /auth/me`
- Auth: 필요 (Bearer)

Response 200:
```json
{
  "id": "uuid",
  "username": "string",
  "role": "ADMIN|USER",
  "provider": "CREDENTIALS|GOOGLE|GITHUB",
  "createdAt": "2026-08-08T12:00:00Z"
}
```

- `createdAt`은 계정 생성 시각. OAuth2 사용자 중 생성 시각 정보가 없으면 null.


## 2. OAuth2

### 2.1 OAuth2 인증 시작 (Authorization URL 리디렉션)
- `GET /auth/oauth2/authorize/{provider}`
- Auth: 불필요 (public)

Path:
- `provider`: `"google"` (현재 Google만 지원)

Query (선택):
- `redirectUri`: 프론트엔드 리디렉션 URI. state 파라미터로 인코딩되어 전달됨.

Response: 302 Redirect → Google OAuth2 Authorization URL

오류: `"google"` 이외의 provider → 400 `BAD_REQUEST`


### 2.2 OAuth2 콜백 처리
- `GET /auth/oauth2/callback/{provider}`
- Auth: 불필요 (public)

Path:
- `provider`: `"google"` (현재 Google만 지원)

Query:
- `code` (필수): Google에서 전달한 Authorization Code
- `state` (선택): CSRF 방지용 state 파라미터

Response: 302 Redirect → 프론트엔드 `/oauth/callback` 경로

- `Set-Cookie` 헤더로 `refresh_token` 쿠키를 발급한다.

오류: `"google"` 이외의 provider → 400 `BAD_REQUEST`


## 3. Documents (파일 업로드 & 관리)

> 파일은 **Presigned URL 방식**으로 S3(SeaweedFS)에 직접 업로드한다.

### 3.1 Presigned URL 발급 (업로드용)
- `POST /documents/upload-url`
- Auth: 필요 (ADMIN)

Request:
```json
{
  "originalFilename": "manual.pdf",
  "contentType": "application/pdf"
}
```

Response 200:
```json
{
  "documentId": "uuid",
  "bucket": "dochi-bot",
  "key": "2026/08/{uuid}_manual.pdf",
  "storageUri": "s3://dochi-bot/2026/08/{uuid}_manual.pdf",
  "uploadUrl": "https://s3.amazonaws.com/dochi-bot/2026/08/...?X-Amz-Algorithm=...",
  "method": "PUT",
  "expiresInSeconds": 900,
  "requiredHeaders": {}
}
```

- `method`: Presigned URL에 사용할 HTTP 메서드 (PUT)
- `requiredHeaders`: S3 업로드 시 필요한 추가 헤더 (일반적으로 빈 맵)
- `expiresInSeconds`: URL 유효 시간

흐름:
1. 클라이언트는 `uploadUrl`로 파일을 S3에 직접 PUT
2. 업로드 완료 후 `POST /documents`로 메타데이터 등록


### 3.2 업로드 확정 (메타데이터 등록)
- `POST /documents`
- Auth: 필요 (ADMIN)

Request:
```json
{
  "documentId": "uuid",
  "title": "사용자 매뉴얼",
  "sourceType": "PDF|TEXT",
  "originalFilename": "manual.pdf",
  "storageUri": "s3://dochi-bot/2026/08/{uuid}_manual.pdf"
}
```

- `documentId`: 3.1에서 발급받은 문서 ID
- `sourceType`: `PDF` 또는 `TEXT`
- `originalFilename`: 선택. 원본 파일명
- `storageUri`: 3.1에서 발급받은 storageUri

Response 200:
```json
{
  "documentId": "uuid",
  "status": "PENDING",
  "ingestionJobId": "uuid"
}
```

- `status`: `PENDING` | `PROCESSING` | `COMPLETED` | `FAILED`
- `ingestionJobId`: 자동 생성된 인제스천 잡 ID
- 등록 후 비동기로 인제스천 잡이 실행된다 (`PENDING` → `PROCESSING` → `COMPLETED`).


### 3.3 문서 목록
- `GET /documents`
- Auth: 필요 (ADMIN)

Query (선택):
- `status`: `PENDING` | `PROCESSING` | `COMPLETED` | `FAILED` (필터)
- `limit`: 조회 개수 (기본값 50, 최대 100)
- `offset`: 오프셋 (기본값 0)

Response 200:
```json
{
  "items": [
    {
      "id": "uuid",
      "title": "사용자 매뉴얼",
      "sourceType": "PDF",
      "originalFilename": "manual.pdf",
      "storageUri": "s3://dochi-bot/2026/08/{uuid}_manual.pdf",
      "status": "COMPLETED",
      "errorMessage": null,
      "createdByUserId": "uuid",
      "createdAt": "2026-08-08T12:00:00Z",
      "updatedAt": "2026-08-08T12:05:00Z"
    }
  ]
}
```


### 3.4 문서 상세
- `GET /documents/{documentId}`
- Auth: 필요 (ADMIN)

Response 200:
```json
{
  "id": "uuid",
  "title": "사용자 매뉴얼",
  "sourceType": "PDF",
  "originalFilename": "manual.pdf",
  "storageUri": "s3://dochi-bot/2026/08/{uuid}_manual.pdf",
  "status": "COMPLETED",
  "errorMessage": null,
  "createdByUserId": "uuid",
  "createdAt": "2026-08-08T12:00:00Z",
  "updatedAt": "2026-08-08T12:05:00Z"
}
```

Response 404: `NOT_FOUND`


### 3.5 다운로드 Presigned URL 발급
- `GET /documents/{documentId}/download-url`
- Auth: 필요 (ADMIN)

Query (선택):
- `filename`: 다운로드 시 사용할 파일명

Response 200:
```json
{
  "downloadUrl": "https://s3.amazonaws.com/dochi-bot/2026/08/...?X-Amz-Algorithm=...",
  "expiresInSeconds": 900
}
```


### 3.6 문서 삭제
- `DELETE /documents/{documentId}`
- Auth: 필요 (ADMIN)

Response: 204 No Content

- 문서와 연관된 인제스천 잡, 청크 데이터도 함께 삭제된다.

Response 404: `NOT_FOUND`


### 3.7 문서 재인덱싱
- `POST /documents/{documentId}/reindex`
- Auth: 필요 (ADMIN)

Request: body 없음.

Response 200:
```json
{
  "jobId": "uuid",
  "status": "QUEUED|RUNNING|SUCCEEDED|FAILED"
}
```


## 4. Ingestion Jobs

### 4.1 인제스천 잡 목록
- `GET /ingestion-jobs`
- Auth: 필요 (ADMIN)

Query (선택):
- `documentId`: uuid (특정 문서의 잡만 필터)
- `limit`: 조회 개수 (기본값 50, 최대 100)
- `offset`: 오프셋 (기본값 0)

Response 200:
```json
{
  "items": [
    {
      "id": "uuid",
      "documentId": "uuid",
      "status": "SUCCEEDED",
      "chunkCount": 123,
      "embeddingModel": "nomic-embed-text",
      "embeddingDims": 768,
      "attemptCount": 1,
      "maxAttempts": 3,
      "nextRunAt": null,
      "startedAt": "2026-08-08T12:00:00Z",
      "finishedAt": "2026-08-08T12:05:00Z",
      "errorMessage": null,
      "createdAt": "2026-08-08T12:00:00Z"
    }
  ]
}
```

- `status`: `QUEUED` | `RUNNING` | `SUCCEEDED` | `FAILED`
- `attemptCount` / `maxAttempts`: 재시도 횟수와 최대 재시도 횟수
- `nextRunAt`: 실패 시 다음 재시도 예정 시각 (null이면 재시도 없음)
- `embeddingDims`: 임베딩 차원 수
- 보안상 `errorMessage`는 항상 null로 반환된다 (내부 오류 메시지는 노출하지 않음).


## 5. Chat (RAG)

### 5.1 채팅 (SSE 스트리밍)
- `POST /chat/stream`
- Auth: 필요 (USER 또는 ADMIN)
- Content-Type: `application/json`
- Accept: `text/event-stream`

Request:
```json
{
  "message": "이 문서의 요약을 알려줘",
  "sessionId": "optional-session-uuid",
  "topK": 5
}
```

- `message`: 필수. 최대 4000자.
- `sessionId`: 선택. 최대 128자. 이전 대화 세션을 이어갈 때 사용.
- `topK`: 선택. 1~50 범위, 기본값 5.

SSE 이벤트 스트림:

각 이벤트는 `data:` 라인에 JSON이 포함된 표준 SSE 형식이다.

```text
event: delta
data: {"sessionId":"uuid","delta":"안녕"}

event: delta
data: {"sessionId":"uuid","delta":"하세요"}

event: final
data: {"sessionId":"uuid","answer":"안녕하세요! 이 문서는...","citations":[...]}

event: error
data: {"message":"처리 중 오류가 발생했습니다."}
```

`ChatStreamEvent` 필드:
- `sessionId`: 세션 ID
- `delta`: 스트리밍 중인 토큰 조각 (스트리밍 중, delta 이벤트)
- `answer`: 최종 완성된 답변 (final 이벤트)
- `citations`: 인용 출처 목록 (final 이벤트)
- `message`: 오류/안내 메시지 (error, info 이벤트)

`Citation` 객체:
```json
{
  "documentId": "uuid",
  "documentTitle": "사용자 매뉴얼",
  "snippet": "이 제품은 ...",
  "page": 3,
  "section": "2.1 설치 방법",
  "score": 0.92
}
```

- `page`: 선택. PDF 페이지 번호.
- `section`: 선택. 섹션/제목.
- `score`: 선택. 유사도 점수.

에러:
- `CHAT_SESSION_FORBIDDEN` (403) — 다른 사용자의 세션에 접근 시도
- `AUTH_FORBIDDEN` (403) — USER/ADMIN이 아닌 역할


## 6. Health (선택)

- `GET /health`
- Auth: 불필요 (public)

Response 200:
```json
{
  "status": "UP"
}
```
