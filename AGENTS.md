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

## 3) DochiBot Docs Index (Compressed)

```text
[docs]|root: ./docs
|00-core:{README.md,PRD.md,SPEC.md}
|01-api:{phase1-api.md,auth-jwt.md,s3-config.md}
|02-data:{db-schema.md}
|03-rag:{retrieval.md,ingestion-pipeline.md,phase2.md}
|04-note:{PRD=무엇/왜,SPEC=어떻게,DDL-SoT=V1__init.sql}
```

## 4) Code Index (Compressed)

```text
[code]|backend-root: ./src/main/kotlin/com/dochibot
|common:{config,exception,storage,util}
|domain:{entity,enums,repository}
|feature/auth:{controller,service,config,dto,exception,util}
|feature/chat:{controller,application,dto,exception}
|feature/document:{controller,application,dto}
|feature/ingestionjob:{controller,application,dto,repository,exception}
|feature/retrieval:{application,dto,repository,infrastructure/{metrics,log}}
|feature/health:{controller,dto}
|frontend-root: ./frontend/src
|frontend-app:{app/router.tsx,app/routes/*}
|frontend-shared:{shared/api/http.ts,shared/auth/*,shared/lib/*}
|frontend-ui:{components/ui/*}
```

## 5) 작업 프로토콜

- 먼저 기존 구현/패턴을 읽어 맥락을 파악한다.
- 기존 스타일을 무조건 따르지 않는다. 더 간결하고 유지보수성이 높은 방식이 명확하면 그 방향으로 작성한다.
- 다만 변경 범위는 요청 목적에 맞게 통제하고, 동작/호환성을 깨지 않는 범위에서 개선한다.
- 설계 판단 시 근거 파일 경로와 선택 이유(왜 더 나은지)를 함께 남긴다.
- 변경 후 최소 검증을 수행한다.
    - Backend: `./gradlew test` 또는 `./gradlew build`
    - Frontend: `pnpm -C frontend lint`, `pnpm -C frontend build`
- 보안/인증/마이그레이션 변경 시 영향 범위와 롤백 포인트를 함께 제시한다.

## 6) 코딩 규칙

### 6.1 KDoc

- KDoc는 선언부에 붙도록 어노테이션(`@GetMapping` 등) 위에 작성한다.
- 구현 세부 반복 설명은 피하고, 의도/계약 중심으로 간결하게 작성한다.
- 파라미터/리턴/프로퍼티가 있으면 최소한의 `@param`, `@return`, `@property`를 포함한다.
- 좋은 예: `/** JWT 토큰을 파싱하여 사용자 정보를 추출한다. */`

### 6.2 프로젝트 구조(VSA)

- feature 단위로 `controller/`, `application/`, `dto/`, `repository/`, `config/`, `exception/`를 우선 사용한다.
- shared entity는 `domain/entity/`, enum은 `domain/enums/`, 저장소 인터페이스는 `domain/repository/`를 우선 사용한다.
- 인프라성 로깅/메트릭은 feature 내부 `infrastructure/`로 분리한다.

### 6.3 네이밍 규칙

- Controller: `<Feature>Controller`
- Use Case: `<Action><Entity>UseCase`
- DTO: `<Action><Entity>Request` / `<Action><Entity>Response`
- Exception: `<Problem>Exception`

## 7) 라이브러리 문서 확인 규칙

- 기술/라이브러리 사용 시 Context7을 통해 최신 문서를 확인한다.
- 우선 확인 대상: Spring Boot 3.4.x, Spring AI 1.1.x, Kotlin 2.0.x, React 19.x, React Router 7.x, TanStack Query 5.x, Vite
  7.x.
- 모호한 구현은 추측하지 말고, 코드 탐색 + 문서 확인 후 결정한다.
