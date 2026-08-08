# S3(SeaweedFS) 스토리지 구성 가이드

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 개요

DochiBot은 **SeaweedFS S3 Gateway**를 파일 스토리지로 사용한다. AWS SDK(S3)와 호환되며, Presigned URL 패턴을 통해 클라이언트가 백엔드를 거치지 않고 스토리지와 직접 파일을 주고받는다.

로컬 개발과 Docker 환경 모두 SeaweedFS 기반이며, AWS S3로 전환하려면 엔드포인트/자격증명만 교체하면 된다.

## 2. SeaweedFS 구성

### 2.1 Docker Compose

```yaml
seaweedfs:
  image: chrislusf/seaweedfs:latest
  ports:
    - "8333:8333"   # S3 API
    - "8888:8888"   # Filer UI/API
    - "9333:9333"   # Master UI/API
  volumes:
    - seaweedfs_data:/data
    - ./seaweedfs/s3.json:/etc/seaweedfs/s3.json:ro
  command: server -dir=/data -s3 -s3.config /etc/seaweedfs/s3.json
```

### 2.2 S3 자격증명 (`docker-dev/seaweedfs/s3.json`)

```json
{
  "identities": [
    {
      "name": "admin",
      "credentials": [
        { "accessKey": "s3admin", "secretKey": "s3secret" }
      ],
      "actions": ["Admin", "Read", "List", "Tagging", "Write"]
    }
  ]
}
```

- Docker 환경에서 API 서비스는 `S3_ENDPOINT=http://seaweedfs:8333`로 내부 네트워크를 통해 접근
- `S3_ACCESS_KEY=s3admin`, `S3_SECRET_KEY=s3secret` (기본값, 운영 시 교체)

## 3. 설정 키

```yaml
s3:
  endpoint: ${S3_ENDPOINT:http://localhost:8333}                    # 서버 내부 통신용
  public-endpoint: ${S3_PUBLIC_ENDPOINT:${S3_ENDPOINT:http://localhost:8333}}  # Presigned URL 생성용
  bucket: ${S3_BUCKET:dochi-bot}
  region: ${S3_REGION:ap-northeast-2}
  access-key: ${S3_ACCESS_KEY:}
  secret-key: ${S3_SECRET_KEY:}
  path-style-access: ${S3_PATH_STYLE_ACCESS:true}                   # SeaweedFS는 true 필수
  presigned-url-expiration-seconds: ${S3_PRESIGNED_URL_EXPIRATION_SECONDS:600}  # 10분
```

- `endpoint`: 서버가 S3 API를 호출할 때 사용하는 엔드포인트 (Docker 내부: `http://seaweedfs:8333`)
- `public-endpoint`: Presigned URL 생성 시 사용하는 엔드포인트 (브라우저가 접근 가능한 주소). 기본값은 `endpoint`와 동일
- `path-style-access`: SeaweedFS는 `true` 필수 (AWS SDK의 path-style access 활성화)
- `presigned-url-expiration-seconds`: Presigned URL 만료 시간 (기본 600초 = 10분)

## 4. S3 클라이언트 구성

`S3Config`는 두 개의 클라이언트를 분리한다:

| 클라이언트 | 사용 엔드포인트 | 용도 |
|-----------|---------------|------|
| `S3Client` | `s3.endpoint` | 서버 측 오브젝트 삭제 등 내부 API 호출 |
| `S3Presigner` | `s3.public-endpoint` | Presigned URL 생성 (브라우저가 접근할 주소) |
| `S3AsyncClient` | `s3.endpoint` | 비동기 S3 API 호출 |

모든 클라이언트는 `StaticCredentialsProvider`로 자격증명을 주입받으며, `pathStyleAccessEnabled` 설정을 공유한다.

## 5. Presigned URL 플로우

### 5.1 업로드

```
1. POST /api/v1/documents/upload-url { originalFilename, contentType, sourceType }
   → DocumentUploadPolicy 검증 (PDF/Markdown만 허용)
   → 문서 UUID(v7) 생성
   → 오브젝트 key 생성: yyyy/MM/{documentId}_{sanitizedFilename}
   → s3Service.createPresignedPut() → Presigned PUT URL 발급
   → 응답: { documentId, uploadUrl, method="PUT", requiredHeaders={"Content-Type"}, ... }

2. 브라우저에서 native fetch로 S3에 직접 PUT (Content-Type 헤더 포함)
   ※ ky가 아닌 native fetch 사용 — S3 엔드포인트가 API base URL과 다르기 때문

3. POST /api/v1/documents { documentId, storageUri, title, sourceType, originalFilename }
   → object key 유효성 검증
   → Document + IngestionJob 생성
   → 상태: PENDING → IngestionJobWorker가 QUEUED 감지 → 처리
```

### 5.2 다운로드

```
GET /api/v1/documents/{id}/download-url?filename=...
   → 문서의 storageUri 파싱 (s3://{bucket}/{key})
   → s3Service.createPresignedGet() → Presigned GET URL 발급
   → Content-Disposition: attachment; filename="..." 헤더 포함
   → 응답: { downloadUrl, expiresInSeconds }
```

### 5.3 삭제

```
DELETE /api/v1/documents/{id}
   → 문서 상태 확인 (PROCESSING이면 거부)
   → storageUri 파싱 → s3Service.deleteObject() 호출
   → DB에서 문서 레코드 삭제
   → NoSuchKeyException/404는 무시 (멱등)
```

## 6. 오브젝트 Key 규칙

- 형식: `yyyy/MM/{documentId}_{sanitizedFilename}`
- 예: `2026/08/01930d5a-...-abc123_report.pdf`
- 파일명 sanitize: 경로 구분자 제거, 제어 문자 제거, 공백→`_`, 최대 120자

저장 URI는 `s3://{bucket}/{key}` 형식으로 문서 레코드의 `storage_uri` 컬럼에 저장된다.

## 7. 업로드 제한

### 7.1 허용 포맷 (DocumentUploadPolicy)

| 포맷 | 확장자 | MIME 타입 |
|------|--------|----------|
| PDF | `.pdf` | `application/pdf` |
| Markdown | `.md`, `.markdown` | `text/markdown`, `text/x-markdown`, `text/plain` |

위반 시: `400 BAD_REQUEST` — `"Unsupported file format. Only PDF(.pdf) and Markdown(.md, .markdown) are allowed."`

### 7.2 파일 크기

`dochibot.ingestion.content.max-bytes` (환경변수 `DOCHIBOT_INGESTION_CONTENT_MAX_BYTES`, 기본 `50000000` = 50MB)

- 이 값은 업로드 자체를 제한하는 것이 아니라 인제스트 파이프라인에서 콘텐츠 읽기 시 적용된다.
- S3 Presigned PUT URL 발급 시 크기 제한은 걸지 않는다 (프론트 측에서 별도 제한 필요).

## 8. Path-Style Access

SeaweedFS는 path-style access(`http://host:port/bucket/key`)만 지원한다. AWS S3의 virtual hosted-style(`http://bucket.s3.region.amazonaws.com/key`)은 사용하지 않는다.

`S3_PATH_STYLE_ACCESS=true`가 기본값이며, AWS S3로 전환 시 `false`로 변경할 수 있다.

## 9. 인프라 참고

| 환경 | S3_ENDPOINT | S3_PUBLIC_ENDPOINT | 비고 |
|------|------------|-------------------|------|
| 로컬 (IntelliJ) | `http://localhost:8333` | (기본값=endpoint) | Docker Compose로 SeaweedFS 실행 |
| Docker | `http://seaweedfs:8333` | (기본값=endpoint) | compose 내부 네트워크 |
| AWS S3 (운영) | `https://s3.{region}.amazonaws.com` | 동일 | path-style-access=false |