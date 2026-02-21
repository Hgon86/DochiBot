## 문서 구성(최소 세트)

빠르게 전체를 파악하려면 아래 4개만 보면 된다.

- `docs/PRD.md`: 무엇을/왜 만들었는지(제품 목표)
- `docs/SPEC.md`: 아키텍처/정책/데이터 모델(설계의 단일 기준)
- `docs/phase1-api.md`: 실제 API 스펙(v1)
- `docs/phase2.md`: Phase 2 계획 + 상세 설계(진행 중)

## 상세 문서(필요할 때)

- `docs/retrieval.md`: Gate + Range-limited + RRF(+Rerank/Verify) 리트리벌 상세
- `docs/ingestion-pipeline.md`: 인제션/청킹/임베딩/FTS 저장 흐름
- `docs/db-schema.md`: DB 스키마(개념 명세; 단일 진실 소스는 Flyway DDL)
- `docs/auth-jwt.md`: 인증/인가(JWT/OAuth2/Redis) 설계 상세
- `docs/s3-config.md`: S3/SeaweedFS 설정 가이드

## 참고

- DB DDL의 단일 진실 소스: `src/main/resources/db/migration/V1__init.sql`
