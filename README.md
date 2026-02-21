# DochiBot

라즈베리파이/로컬 환경에서 구동되는 **내부 문서 기반 RAG 운영 백엔드(인입/인덱싱/검색/관측/재처리)** 프로젝트입니다.

- PRD(무엇을/왜): `docs/PRD.md`
- SPEC(어떻게): `docs/SPEC.md`
- DB 스키마: `docs/db-schema.md`
- 인제션 파이프라인: `docs/ingestion-pipeline.md`
- 리트리벌(하이브리드/RRF/리랭크): `docs/retrieval.md`

## Tech Stack
- JDK 21, Kotlin, Spring Boot(WebFlux) + Coroutines
- PostgreSQL(R2DBC) + Flyway(JDBC)
- Redis
- PostgreSQL + pgvector (Dense) + FTS(tsvector, GIN) (Sparse)
- Ollama (Chat/Embedding)
- SeaweedFS (S3 Gateway; S3 호환, Presigned URL 용도)

## 로컬 실행(권장 흐름)

### 1) 인프라 기동 (Postgres/Redis/Ollama/SeaweedFS)
```bash
# 필요 시 docker-dev/.env 값을 수정하세요.
docker compose -f docker-dev/compose.yaml --env-file docker-dev/.env up -d
```

### 2) 백엔드 빌드/실행
```bash
./gradlew clean build
./gradlew bootRun
```

## 올도커(옵션)
API 컨테이너까지 함께 올리려면 `api` profile을 사용합니다.
```bash
docker compose -f docker-dev/compose.yaml --env-file docker-dev/.env --profile api up -d --build
```

## 설정 메모
- LLM은 기본값이 로컬 Ollama이며, `AI_CHAT_PROVIDER=openai`로 OpenAI 호환(OpenRouter 등)으로 전환할 수 있습니다.
- 임베딩 모델은 `OLLAMA_EMBEDDING_MODEL`로 바꿀 수 있습니다(기본: `mxbai-embed-large`). 필요하면 `nomic-embed-text`로 더 가볍게 시작할 수 있습니다.

## 문서
- API 상세: `docs/phase1-api.md`
- DB 스키마: `docs/db-schema.md`
- S3/SeaweedFS: `docs/s3-config.md`
- 인증(JWT): `docs/auth-jwt.md`
- 대화 메모리 전략: `docs/chat-memory-strategy.md`
