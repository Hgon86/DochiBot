# AGENTS.md — DochiBot

## 0) 목적

이 파일은 DochiBot 코드베이스 작업 시 에이전트가 매 턴 참조하는 영구 컨텍스트다.
목표는 추측 기반 답변이 아니라, 코드/문서 근거 기반 의사결정이다.

## 1) 최상위 규칙(반드시 준수)

- 모든 답변은 한국어로 작성한다.
- IMPORTANT: pre-training-led reasoning보다 retrieval-led reasoning을 우선한다.
- 작업 순서: `프로젝트 구조 파악 -> 관련 문서/코드 탐색 -> Context7 최신 문서 확인 -> 구현/검증`.
- 문서와 코드가 충돌하면 아래 SoT(Source of Truth) 우선순위를 따른다.

## 2) SoT(Source of Truth) 우선순위

- DB 스키마 단일 진실 소스: `src/main/resources/db/migration/V1__init.sql`
- 런타임 설정: `src/main/resources/application.yml`, `docker-dev/.env`, `docker-dev/compose.yaml`
- 제품/아키텍처 의도: `docs/PRD.md`, `docs/SPEC.md`
- API 계약: `docs/phase1-api.md` + 실제 `controller/dto` + 테스트 코드
- 리트리벌/인제션 상세: `docs/retrieval.md`, `docs/ingestion-pipeline.md`, `docs/phase2.md`

## 3) Tech Stack (Compressed)

```text
[backend] Kotlin 2.0.21 | JDK 21 | Spring Boot 3.4.1 | WebFlux (Coroutines)
  |ai: Spring AI 1.1.2 (Ollama default, OpenAI-compatible optional)
  |db: PostgreSQL 17 + pgvector (R2DBC reactive) | Flyway migration (JDBC)
  |cache: Redis 7
  |storage: SeaweedFS (S3-compatible, presigned URL pattern)
  |security: JWT(HS256) + OAuth2(Google) | access=memory, refresh=HttpOnly cookie
  |pdf: Apache PDFBox 2.0.35
  |id: UUID v7 (uuid-creator 5.3.7)
  |build: Gradle Kotlin DSL
[frontend] React 19.2 | TypeScript 5.9 | Vite 7.2
  |routing: React Router 7.13
  |server-state: TanStack Query 5.90
  |client-state: Zustand 5.0
  |http: ky 1.14 (Bearer auto-inject, 401→refresh retry)
  |form: react-hook-form 7.71 + zod 4.3
  |style: Tailwind CSS 4.1 + CVA + Radix UI
  |lint: Biome 2.3
```

## 4) DochiBot Docs Index (Compressed)

```text
[docs]|root: ./docs
|00-core:{README.md,PRD.md,SPEC.md}
|01-api:{phase1-api.md,auth-jwt.md,s3-config.md}
|02-data:{db-schema.md}
|03-rag:{retrieval.md,ingestion-pipeline.md,phase2.md}
|04-note:{PRD=무엇/왜,SPEC=어떻게,DDL-SoT=V1__init.sql}
```

## 5) Code Index (Compressed)

```text
[code]|backend-root: ./src/main/kotlin/com/dochibot
|common:{config/{SecurityConfig,S3Config,CorsConfig}.kt,exception/GlobalExceptionHandler.kt,storage/S3PresignedUrlService.kt,util/}
|domain:{entity/{User,Document,DocumentChunk,DocumentIngestionJob}.kt,enums/{DocumentStatus,JobStatus,SourceType}.kt,repository/*Repository.kt}
|feature/auth:{controller/{AuthController,Oauth2Controller}.kt,service/AuthService.kt,config/JwtProperties.kt,dto/,exception/,util/JwtProvider.kt}
|feature/chat:{controller/ChatController.kt,application/ChatUseCase.kt,dto/,exception/}
|feature/document:{controller/DocumentController.kt,application/{CreateDocumentUploadUrlUseCase,FinalizeDocumentUploadUseCase,DocumentUploadPolicy,ListDocumentsUseCase,GetDocumentUseCase,CreateDocumentDownloadUrlUseCase,ReindexDocumentUseCase}.kt,dto/}
|feature/ingestionjob:{controller/IngestionJobController.kt,application/IngestionJobWorker.kt,dto/,repository/,exception/}
|feature/retrieval:{application/{RetrievalPipeline,DenseRetriever,SparseRetriever,RrfFusion}.kt,application/rerank/{Reranker,CrossEncoderReranker,LlmJudgeReranker}.kt,application/verify/,dto/,repository/,infrastructure/{metrics/,log/}}
|feature/health:{controller/HealthController.kt,dto/}
[frontend]|root: ./frontend/src
|app:{router.tsx,routes/{login,oauth-callback,dashboard,documents,document-detail,ingestion-jobs,chat,monitoring}.tsx}
|shared/api:{http.ts(ky instances+auth interceptor),admin.ts(all API functions),types.ts(response types)}
|shared/auth:{session.ts(zustand token store),require-auth.tsx(route guard),use-auth.ts(login hook),use-current-user.ts(me query)}
|shared/lib:{utils.ts}
|components:{layout/admin-shell.tsx,ui/{button,input,card,badge,...}.tsx}
```

## 6) API Endpoint Index (Compressed)

```text
[api]|base: /api/v1 | all suspend fun (WebFlux coroutine)
|auth: POST login | POST refresh | POST logout | GET me
|auth/oauth2: GET authorize/{provider} | GET callback/{provider}
|documents: POST upload-url | POST (finalize) | GET (list?status&limit&offset) | GET {id} | GET {id}/download-url | POST {id}/reindex
|ingestion-jobs: GET (list?status&limit&offset)
|chat: POST (query, sessionId, topK)
|health: GET (status)
|auth-note: login/refresh/logout/oauth2 → publicApi(no Bearer) | 나머지 → api(Bearer auto-inject)
|upload-flow: POST upload-url → presigned PUT to S3 → POST finalize → IngestionJob auto-created
```

## 7) Infrastructure (Compressed)

```text
[infra]|compose: ./docker-dev/compose.yaml | env: ./docker-dev/.env
|services:
  postgres(pgvector:pg17):5432 | redis(7-alpine):6379
  seaweedfs(S3-compat):8333,filer:8888,master:9333 | ollama:11434
  cross-encoder:8001(profile:cross-encoder,optional) | api:8080(profile:api,optional)
|local-dev: docker compose up(postgres,redis,seaweedfs,ollama) → IntelliJ 백엔드(:8080) → pnpm -C frontend dev(:5173)
|proxy: vite dev server /api/** → localhost:8080 (frontend/vite.config.ts)
|s3-flow: presigned PUT URL 발급(백엔드) → 브라우저에서 S3 직접 업로드(native fetch) → finalize(백엔드)
|frontend-env: VITE_API_BASE_URL=/api/v1 | VITE_DEV_BYPASS_AUTH=true(개발시 인증 우회)
```

## 8) Test Index (Compressed)

```text
[test]|root: ./src/test/kotlin/com/dochibot
|config: src/test/resources/application-test.yml
|e2e:{RagE2eSmokeTest,RagVerifyPassThroughE2eTest,RagVerifyPolicyE2eTest}.kt
|feature/document:{DocumentUploadPolicyTest}.kt
|feature/retrieval:{RetrievalBenchmarkTest,RetrievalIntegrationTest,Phase2EvalRunnerTest,Phase2EvalSetParseTest}.kt
|feature/retrieval/rerank:{CrossEncoderRerankerTest,LlmJudgeRerankerTest}.kt
|feature/retrieval/verify:{DefaultEvidenceVerifierTest,QueryTypeClassifierTest}.kt
|feature/retrieval/eval:{Phase2EvalScorerTest,Phase2EvalValidatorTest,SyntheticEvalQueryGeneratorTest}.kt
|mock:{MockCrossEncoderServer,MockDocumentStore}.kt
|eval-data: src/test/resources/eval/{phase2_eval_sample,phase2_mock_documents_sample}.json
|naming: <Subject>Test.kt | E2E: *E2eTest.kt | 벤치마크: *BenchmarkTest.kt
|frontend: 테스트 프레임워크 미설정 (lint/build로 검증)
```

## 9) Gotchas

- WebFlux 기반이므로 blocking I/O 금지 (`Thread.sleep`, JDBC 직접 호출 등). R2DBC + 코루틴만 사용.
  - 단, Flyway 마이그레이션은 JDBC 사용 (spring.datasource). 런타임 쿼리는 R2DBC.
- Access Token은 메모리(Zustand)에만 저장. `localStorage`/`sessionStorage` 사용 금지 (보안 정책).
- Refresh Token은 HttpOnly 쿠키로만 관리. JS에서 직접 접근 불가.
- S3 업로드는 반드시 Presigned URL 패턴 사용 (백엔드를 파일 프록시로 쓰지 않음).
- Entity ID는 UUID v7 사용 (`uuid-creator` 라이브러리). auto-increment 아님.
- 프론트엔드에서 S3 직접 PUT 시 `ky`가 아닌 native `fetch` 사용 (base URL이 다르므로).
- 프론트엔드 import는 절대 경로 사용: `@/shared/...`, `@/components/...` (vite alias `@` → `src/`).
- 프론트엔드 `api` 인스턴스는 401 시 refresh를 1회만 시도하고, 동시 요청은 단일 Promise로 동시성 제어.

## 10) 작업 프로토콜

- 먼저 기존 구현/패턴을 읽어 맥락을 파악한다.
- 기존 스타일을 무조건 따르지 않는다. 더 간결하고 유지보수성이 높은 방식이 명확하면 그 방향으로 작성한다.
- 다만 변경 범위는 요청 목적에 맞게 통제하고, 동작/호환성을 깨지 않는 범위에서 개선한다.
- 설계 판단 시 근거 파일 경로와 선택 이유(왜 더 나은지)를 함께 남긴다.
- 변경 후 최소 검증을 수행한다.
    - Backend: `./gradlew test` 또는 `./gradlew build`
    - Frontend: `pnpm -C frontend lint`, `pnpm -C frontend build`
- 보안/인증/마이그레이션 변경 시 영향 범위와 롤백 포인트를 함께 제시한다.

## 11) 코딩 규칙

### 11.1 KDoc

- KDoc는 선언부에 붙도록 어노테이션(`@GetMapping` 등) 위에 작성한다.
- 구현 세부 반복 설명은 피하고, 의도/계약 중심으로 간결하게 작성한다.
- 파라미터/리턴/프로퍼티가 있으면 최소한의 `@param`, `@return`, `@property`를 포함한다.
- 좋은 예: `/** JWT 토큰을 파싱하여 사용자 정보를 추출한다. */`

### 11.2 프로젝트 구조(VSA)

- feature 단위로 `controller/`, `application/`, `dto/`, `repository/`, `config/`, `exception/`를 우선 사용한다.
- shared entity는 `domain/entity/`, enum은 `domain/enums/`, 저장소 인터페이스는 `domain/repository/`를 우선 사용한다.
- 인프라성 로깅/메트릭은 feature 내부 `infrastructure/`로 분리한다.

### 11.3 네이밍 규칙

- Controller: `<Feature>Controller`
- Use Case: `<Action><Entity>UseCase`
- DTO: `<Action><Entity>Request` / `<Action><Entity>Response`
- Exception: `<Problem>Exception`

## 12) 라이브러리 문서 확인 규칙

- 기술/라이브러리 사용 시 Context7을 통해 최신 문서를 확인한다.
- 우선 확인 대상: Section 3의 Tech Stack 버전 기준.
- 모호한 구현은 추측하지 말고, 코드 탐색 + 문서 확인 후 결정한다.
