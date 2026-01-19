# S3/MinIO 설정 가이드

## 0. 개요
- DochiBot은 파일 저장을 위해 **S3 호환 스토리지**를 사용합니다.
- **AWS S3** 또는 **MinIO** (로컬/온프레미스) 둘 다 지원합니다.
- Presigned URL 방식을 통해 클라이언트가 직접 파일을 업로드/다운로드합니다.

## 1. 지원 스토리지

| 스토리지 | 설명 | 용도 |
|----------|------|------|
| **AWS S3** | AWS 클라우드 스토리지 | 프로덕션 환경 |
| **MinIO** | S3 호환 오브젝트 스토리지 | 로컬 개발, 온프레미스 |

## 2. 환경변수 설정

### 2.1 공통 설정

| 환경변수 | 필수 | 설명 | 기본값 |
|----------|------|------|--------|
| `S3_ENDPOINT` | Yes | 스토리지 엔드포인트 | - |
| `S3_BUCKET` | Yes | 버킷명 | `dochi-bot` |
| `S3_REGION` | Yes | 리전 | `ap-northeast-2` |
| `S3_ACCESS_KEY` | Yes | Access Key | - |
| `S3_SECRET_KEY` | Yes | Secret Key | - |
| `S3_PATH_STYLE_ACCESS` | No | Path Style 접근 사용 여부 | `false` |

### 2.2 엔드포인트 예시

**AWS S3 (서울 리전)**:
```
S3_ENDPOINT=https://s3.ap-northeast-2.amazonaws.com
S3_REGION=ap-northeast-2
```

**MinIO (로컬)**:
```
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_PATH_STYLE_ACCESS=true
```

### 2.3 docker-compose.yml 예시

```yaml
services:
  api:
    image: dochibot-api
    environment:
      - S3_ENDPOINT=${S3_ENDPOINT:-http://minio:9000}
      - S3_BUCKET=${S3_BUCKET:-dochi-bot}
      - S3_REGION=${S3_REGION:-us-east-1}
      - S3_ACCESS_KEY=${S3_ACCESS_KEY}
      - S3_SECRET_KEY=${S3_SECRET_KEY}
      - S3_PATH_STYLE_ACCESS=${S3_PATH_STYLE_ACCESS:-true}
    depends_on:
      - minio

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      - MINIO_ROOT_USER=${S3_ACCESS_KEY}
      - MINIO_ROOT_PASSWORD=${S3_SECRET_KEY}
    volumes:
      - minio_data:/data

volumes:
  minio_data:
```

## 3. Presigned URL 설정

### 3.1 만료 시간

| 설정값 | 설명 | 기본값 |
|--------|------|--------|
| `S3_PRESIGNED_URL_EXPIRATION_SECONDS` | Presigned URL 유효 시간(초) | `900` (15분) |

### 3.2 권장 설정값

| 환경 | 업로드 URL 만료 | 다운로드 URL 만료 |
|------|-----------------|-------------------|
| 개발 | 15분 (900초) | 15분 (900초) |
| 프로덕션 | 5분 (300초) | 1시간 (3600초) |

### 3.3 설정 예시

```yaml
# application.yml (또는 환경변수)
s3:
  presigned-url-expiration-seconds: 900
```

## 4. 파일 크기 제한

| 설정값 | 설명 | 기본값 |
|--------|------|--------|
| `S3_MAX_FILE_SIZE_MB` | 최대 파일 크기(MB) | `50` |

### 4.1 권장 파일 크기

| 파일 유형 | 최대 크기 |
|-----------|----------|
| PDF | 50MB |
| 텍스트 | 10MB |

### 4.2 설정 예시

```yaml
# 서버는 파일을 직접 받지 않지만(클라이언트가 S3로 직접 업로드),
# 문서 등록 API에서 파일 크기 검증을 위해 별도 설정을 둔다.

s3:
  max-file-size-mb: 50
  presigned-url-expiration-seconds: 900
```

## 5. URI 포맷

### 5.1 저장소 URI 규칙

```
s3://{bucket}/{year}/{month}/{uuid}_{filename}
```

### 5.2 예시

```
s3://dochi-bot/2026/01/550e8400-e29b-41d4-a716-446655440000_manual.pdf
```

### 5.3 디렉토리 구조

```
dochi-bot/
├── 2026/
│   └── 01/
│       ├── 550e8400-e29b-41d4-a716-446655440000_manual.pdf
│       └── 660e8400-e29b-41d4-a716-446655440001_guide.pdf
└── 2026/
    └── 02/
        └── ...
```

## 6. 버킷 초기화

### 6.1 MinIO 버킷 생성 (docker-compose 사용 시)

```bash
# MinIO 컨테이너 접속
docker compose exec minio /bin/sh

# 버킷 생성
mc alias set local http://localhost:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}
mc mb local/dochi-bot

# 공개 접근 설정 (필요시)
mc anonymous set public local/dochi-bot
```

### 6.2 AWS S3 버킷 정책

버킷이 Presigned URL을 통해 public 접근 없이도 파일을 읽고 쓸 수 있도록 설정합니다.

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowPresignedUrlAccess",
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::dochi-bot/*"
        }
    ]
}
```

## 7. 트러블슈팅

### 7.1 일반적인 오류

| 오류 | 원인 | 해결책 |
|------|------|--------|
| `InvalidAccessKeyId` | Access Key 오류 | 환경변수 확인 |
| `SignatureDoesNotMatch` | Secret Key 오류 | 환경변수 확인 |
| `BucketNotExists` | 버킷 미생성 | 버킷 생성 후 재시도 |
| `PresignedUrlExpired` | URL 만료 | 새로운 URL 발급 |

### 7.2 디버깅 방법

```bash
# S3 연결 테스트
curl -I ${S3_ENDPOINT}/${S3_BUCKET}/test

# Presigned URL 디버깅 (application.yml 설정 확인)
./gradlew bootRun --debug
```

## 8. 보안 권장사항

### 8.1 프로덕션 환경

- [ ] IAM 정책으로 최소 권한 부여
- [ ] VPC 엔드포인트 사용 (AWS)
- [ ] CloudTrail로 접근 로깅
- [ ] 버킷 암호화 활성화

### 8.2 IAM 정책 예시

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:DeleteObject"
            ],
            "Resource": "arn:aws:s3:::dochi-bot/*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:ListBucket"
            ],
            "Resource": "arn:aws:s3:::dochi-bot"
        }
    ]
}
```
