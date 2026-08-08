# DochiBot 제품 요구사항 문서 (PRD)

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 제품 개요

DochiBot은 **내부 문서를 기반으로 한 RAG(Retrieval-Augmented Generation) 운영 백엔드**다. 라즈베리파이/로컬 환경에서도 구동 가능하도록 설계되었으며, 단순한 벡터 검색을 넘어 검색 최적화(Retrieval Optimization)부터 신뢰성 검증(Verification)까지 RAG 파이프라인의 전 과정을 다룬다.

## 2. 타깃 사용자

- 라즈베리파이 또는 로컬 서버에서 자체 문서 검색/질의 시스템을 운영하려는 개인 및 소규모 팀
- 외부 API 의존 없이 온프레미스에서 완결된 RAG 시스템을 원하는 사용자
- 문서 업로드, 인제션, 채팅, 모니터링을 하나의 대시보드에서 관리하려는 관리자

## 3. 핵심 문제

일반적인 Naive RAG(단순 벡터 검색 후 LLM에 주입)는 실무에서 두 가지 치명적 한계에 부딪힌다.

- **키워드 누락**: Dense Vector 검색은 문맥적 의미는 잘 파악하지만 고유명사, 품번, 제품명 등 정확한 키워드(Exact Match) 검색에 취약하다.
- **환각(Hallucination)**: 검색된 문서가 부족하거나 무관해도 LLM이 그럴싸한 거짓말을 지어낼 수 있다.

## 4. 해결 접근

DochiBot은 세 단계의 공정으로 이 문제를 해결한다.

### 4.1 하이브리드 검색 + RRF 융합

PostgreSQL의 `pgvector`(Dense)와 `tsvector` FTS(Sparse)를 동시 수행하는 하이브리드 검색을 구축했다. 성질이 다른 두 검색 결과의 점수를 공정하게 병합하기 위해 RRF(Reciprocal Rank Fusion) 알고리즘을 적용한다.

### 4.2 다중 리랭킹 (Reranker Router)

1단계에서 찾은 Top-K 후보군을 사용자 질의와의 진짜 연관성에 따라 재정렬한다. 질의 난이도와 시스템 리소스에 따라 Cross-Encoder, LLM-as-a-Judge, Heuristic 리랭커를 동적으로 선택하는 Reranker Router 구조를 갖췄다.

### 4.3 검증 파이프라인 (Verify Pipeline)

LLM 응답 생성 전후로 개입하는 자체 Verify 파이프라인을 구축했다. 문서 목록이 질문에 답할 수 있는지 사전 평가(Pre-Generation Verify)하고, 최종 답변에는 사용자가 직접 출처를 검증할 수 있도록 원문 스니펫과 메타데이터를 강제로 포함(Post-Generation Verify)시킨다.

## 5. 주요 기능

### 5.1 문서 업로드 및 관리

- Presigned URL 방식: 클라이언트가 S3(SeaweedFS)로 직접 업로드, 백엔드는 파일을 프록시하지 않음
- 지원 파일 형식: PDF, Markdown
- 문서 목록 및 상태 조회 (PENDING → PROCESSING → COMPLETED / FAILED)
- 문서 다운로드용 Presigned URL 발급
- SHA-256 기반 중복 업로드 방지
- 재인덱싱 트리거

### 5.2 인제션 파이프라인

- 비동기 Job Worker 기반: 문서 청킹·임베딩을 메인 스레드에서 분리
- Job 상태 추적: QUEUED → RUNNING → SUCCEEDED / FAILED
- 실패 시 재처리(Retry) 및 오류 메시지 기록
- Ingestion Job 목록 조회

### 5.3 채팅 (RAG)

- 하이브리드 검색 → RRF 병합 → 리랭킹 → 생성·검증의 전체 파이프라인
- 세션 기반 대화 (sessionId)
- 응답에 출처(Citation) 포함: 문서 제목, 스니펫, 페이지, 섹션, 연관도 점수
- 근거가 없을 경우 "문서에서 찾을 수 없음" 정책 적용

### 5.4 인증 및 인가

- JWT Access Token + Refresh Token(HttpOnly 쿠키) 기반 인증
- OAuth2(Google) 소셜 로그인 지원
- 역할 기반 접근: ADMIN(문서 관리·인제션) / USER(채팅)
- Access Token은 메모리(Zustand)에만 저장, LocalStorage/SessionStorage 사용 금지

### 5.5 모니터링

- 헬스 체크 엔드포인트 (/health)
- 요청 추적: X-Request-Id 헤더 전파

## 6. 비기능 요구사항

### 6.1 로컬 LLM 지향

Ollama 기반으로 구성하여 라즈베리파이 등 제한된 엣지 환경에서도 외부 API 의존 없이 동작 가능. 동시에 OpenAI 호환 제공자(NVIDIA NIM 등)로 전환 가능한 유연성을 갖춘다.

### 6.2 100% Non-Blocking I/O

Spring WebFlux + Kotlin Coroutines 기반 Reactive Programming으로 적은 스레드로 높은 동시성을 확보한다. 단, Flyway 마이그레이션만 JDBC를 사용한다.

### 6.3 보안

- Access Token은 메모리에만 저장 (XSS 위험 최소화)
- Refresh Token은 HttpOnly 쿠키로 관리 (JS 접근 불가)
- S3 업로드는 Presigned URL 패턴만 사용
- CORS: 허용된 도메인만 접근 가능

### 6.4 경량 인프라

- PostgreSQL 17 + pgvector (벡터 검색 + FTS를 단일 DB로 통합)
- SeaweedFS (경량 S3 호환 스토리지)
- Redis 7 (캐시)
- Ollama (로컬 LLM / 임베딩)

## 7. 향후 방향

- 실시간 스트리밍 응답 (SSE)
- 다중 문서 유형 지원 확장 (Word, HTML, 코드 파일 등)
- 대화 메모리 및 컨텍스트 관리 고도화
- 평가(Evaluation) 프레임워크 기반 지속적 RAG 품질 개선
- Fine-tuning 파이프라인 통합
- 멀티 테넌트 지원

## 8. 관련 문서

- 기술 설계: [SPEC.md](SPEC.md)
- API 명세: [phase1-api.md](phase1-api.md)
- DB 스키마: [db-schema.md](db-schema.md)
- JWT 인증 설계: [auth-jwt.md](auth-jwt.md)
- S3 설정: [s3-config.md](s3-config.md)
