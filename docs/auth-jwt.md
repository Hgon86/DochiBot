# 인증/인가 설계 (Phase 1) — JWT (단순형)

## 0. 목적
- Phase 1(1주 MVP)에서 **과도한 복잡도 없이** 사용자 인증을 도입한다.
- Phase 2~3(실시간 채팅/에스컬레이션)로 확장 가능한 방향을 유지한다.

## 1. 범위(Phase 1)
### 1.1 In Scope
- 사용자 로그인 기반 JWT 발급
- API 접근 제어(인증 필요/권한 필요)
- 최소 역할(Role) 구분: `ADMIN`, `USER`

### 1.2 Out of Scope (Phase 1에서 하지 않음)
- OAuth/Social 로그인
- SSO/LDAP
- 정교한 RBAC/ABAC
- 토큰 회수(Blacklist)/디바이스 관리
- Refresh Token 로테이션(필요 시 Phase 2에서 추가)

## 2. 인증 방식
### 2.1 토큰 타입
- **Access Token만 사용**(Phase 1 권장, 단순)
  - 만료 시간(TTL) 예: 12시간
  - 만료 시 재로그인

> 옵션(추후): Refresh Token을 도입하면 UX가 좋아지지만, 저장/폐기/탈취 대응이 추가로 필요해진다.

### 2.2 전달 방식
- `Authorization: Bearer <JWT>` 헤더

> Next.js UI에서는 토이 프로젝트 기준으로 `localStorage` 보관도 가능하지만, 보안상으론 `HttpOnly Cookie`가 더 안전하다.
> Phase 1은 내부망 전제를 두되, 최소한 XSS를 유발할 수 있는 패턴은 피한다.

## 3. JWT 클레임(권장)
- `sub`: user id(UUID)
- `username`: 로그인 아이디
- `role`: `ADMIN|USER`
- `iat`, `exp`
- (옵션) `iss`, `aud`

## 4. 비밀번호 정책/저장
- 저장: **해시만 저장** (`BCrypt` 권장)
- 평문 비밀번호/복호화 가능한 형태 저장 금지

## 5. 권한 모델(Phase 1)
- `ADMIN`: 문서 업로드/인덱싱/문서 목록 조회 등 운영 API
- `USER`: 채팅(RAG) API

## 6. 엔드포인트(요약)
상세 스펙은 `docs/phase1-api.md` 참고.
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/users` (ADMIN)

## 7. 보안 설정(구현 가이드 수준)
- JWT 서명 키: 환경변수로 주입
  - `JWT_SECRET` (최소 32 bytes 이상 권장)
- CORS: Next.js UI 도메인만 허용(내부 환경 기준)
- 로그: Authorization 헤더/토큰 본문 절대 로깅 금지

## 8. Phase 2~3 확장 포인트
- Refresh Token + 로테이션 + 서버 저장(해시)
- 에스컬레이션/상담원 라우팅 시 역할/권한 확장

