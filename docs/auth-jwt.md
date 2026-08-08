# 인증(JWT/OAuth2) 구성 가이드

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 개요

DochiBot은 Spring Security 리소스 서버로 동작하며, JWT(HS256) 기반 인증과 Google OAuth2 소셜 로그인을 지원한다.
인증 제공자는 `CREDENTIALS`(아이디/비밀번호)와 `GOOGLE`(Google OAuth2) 두 가지다.

## 2. 토큰 구조

### 2.1 Access Token

| 항목 | 내용 |
|------|------|
| 서명 알고리즘 | HS256 (HMAC-SHA256) |
| 시크릿 키 | `dochibot.jwt.secret` (환경변수 `JWT_SECRET`) |
| TTL | 기본 43,200초 (12시간) |
| 전달 방식 | `Authorization: Bearer <token>` 헤더 |
| 저장 위치 | **프론트엔드 메모리(Zustand)** — `localStorage`/`sessionStorage` 사용 금지 |

### 2.2 Refresh Token

| 항목 | 내용 |
|------|------|
| 서명 알고리즘 | HS256 (Access Token과 동일 키) |
| TTL | 기본 604,800초 (7일) |
| 전달 방식 | HttpOnly 쿠키 (`refresh_token`) |
| 쿠키 속성 | `HttpOnly=true`, `Path=/`, `SameSite=Lax`, `Secure`(기본 `false`) |
| 서버 저장소 | Redis (키: `refresh_token:{tokenId}`) |

### 2.3 JWT 클레임

| 클레임 | 설명 |
|--------|------|
| `jti` | 토큰 ID (UUID v7) |
| `sub` | 사용자 ID (UUID) |
| `iat` | 발급 시각 |
| `exp` | 만료 시각 |
| `tokenType` | `"access"` 또는 `"refresh"` |
| `username` | 사용자 로그인 아이디 |
| `role` | `ADMIN` 또는 `USER` |
| `provider` | `CREDENTIALS` 또는 `GOOGLE` |

## 3. 인증 플로우

### 3.1 로그인 (Username/Password)

- `POST /api/v1/auth/login { username, password }`
- 서비스: `AuthService.login()`
- BCrypt로 비밀번호 검증, 비활성 사용자 거부
- Access Token은 JSON body로, Refresh Token은 `Set-Cookie` 헤더로 응답

### 3.2 토큰 갱신 (Refresh)

- `POST /api/v1/auth/refresh` + Cookie: `refresh_token=<value>`
- 서비스: `AuthService.refresh()`
- JWT 디코딩(tokenType="refresh" 확인) → Redis 저장 확인 → 기존 토큰 삭제 → 새 쌍 발급 (로테이션)
- 만료된 토큰은 Redis에서 삭제 후 `REFRESH_TOKEN_EXPIRED` 오류 반환

### 3.3 로그아웃

- `POST /api/v1/auth/logout` + Cookie: `refresh_token=<value>`
- 서비스: `AuthService.logout()` (멱등)
- Redis에서 Refresh Token 삭제, 쿠키 제거(maxAge=0)
- 깨진 토큰도 예외 없이 성공 처리

### 3.4 OAuth2 (Google)

1. `GET /api/v1/auth/oauth2/authorize/google`
   - 302 Redirect → `https://accounts.google.com/o/oauth2/v2/auth`
   - scope: `email profile`, redirect_uri: `{backendBaseUrl}/api/v1/auth/oauth2/callback/google`

2. 사용자 Google 로그인 → callback

3. `GET /api/v1/auth/oauth2/callback/google?code=...`
   - Authorization Code → Token Endpoint 교환
   - UserInfo Endpoint → 사용자 정보 조회 (sub, email, name)
   - DB에서 `(provider=GOOGLE, providerId=sub)` 조회 → 없으면 자동 회원가입
   - JWT 발급 + refresh_token 쿠키 설정
   - 302 Redirect → `{frontendBaseUrl}/oauth/callback`

- 컨트롤러: `Oauth2Controller`
- 서비스: `AuthService.loginOrRegisterOauth2()` → `OAuth2UserService.getOrCreateUser()`

## 4. JWT 검증 구조

### 4.1 Spring Security

- `SecurityConfig` → `SecurityWebFilterChain` (WebFlux)
- JWT 디코더: `NimbusReactiveJwtDecoder` (HS256)
- JWT 인코더: `NimbusJwtEncoder` (HS256)
- 권한: JWT `role` 클레임 → `ROLE_ADMIN` / `ROLE_USER`

### 4.2 경로별 접근 권한

| 경로 | 권한 |
|------|------|
| `OPTIONS` | 모두 허용 |
| `/api/v1/auth/login`, `/refresh`, `/logout` | 모두 허용 |
| `/api/v1/auth/oauth2/**` | 모두 허용 |
| `/api/v1/health` | 모두 허용 |
| `/api/v1/documents/**` | `ROLE_ADMIN` |
| `/api/v1/ingestion-jobs/**` | `ROLE_ADMIN` |
| `/api/v1/chat/**` | `ROLE_USER` 또는 `ROLE_ADMIN` |
| 그 외 | 인증 필요 |

### 4.3 오류 응답 (JSON)

| 상황 | HTTP | 코드 |
|------|------|------|
| Authorization 헤더 없음 | 401 | `AUTH_REQUIRED` |
| JWT 유효하지 않음 | 401 | `AUTH_INVALID_TOKEN` |
| 권한 부족 | 403 | `AUTH_FORBIDDEN` |

### 4.4 JWT 시크릿 키

- `dochibot.jwt.secret` (환경변수 `JWT_SECRET`)
- 운영: 32 bytes 이상 필수
- local/docker 프로필 공백: ephemeral 키 자동 생성 (재시작 시 무효화)
- 프로덕션 프로필 공백: `IllegalStateException`

## 5. Refresh Token Redis 저장

| 항목 | 내용 |
|------|------|
| 키 패턴 | `refresh_token:{tokenId}` |
| 값 | JSON `{ tokenId, userId, provider, createdAt, expiresAt }` |
| TTL | Refresh Token TTL과 동일 (기본 7일) |
| 저장 | 로그인/리프레시/OAuth2 성공 시 |
| 삭제 | 리프레시(로테이션), 로그아웃, 만료 감지 시 |

## 6. 보안 정책

- Access Token: **프론트엔드 메모리(Zustand)**에만 저장 — `localStorage`/`sessionStorage` 금지
- Refresh Token: **HttpOnly 쿠키**로만 전달 — JS 접근 불가
- CORS: `dochibot.cors.allowed-origins` (기본 `http://localhost:5173`)
- CSRF: 비활성화 (stateless JWT)
- 쿠키 Secure: 개발 `false`, 운영 `true` (`dochibot.cookie.secure`)
- 비밀번호: BCrypt 해시

## 7. 설정 키 참조

```yaml
dochibot:
  jwt:
    secret: ${JWT_SECRET:}                           # 32 bytes 이상 권장
    access-token-ttl-seconds: ${JWT_ACCESS_TOKEN_TTL_SECONDS:43200}       # 12시간
    refresh-token-ttl-seconds: ${JWT_REFRESH_TOKEN_TTL_SECONDS:604800}    # 7일
  cookie:
    secure: ${COOKIE_SECURE:false}                   # 운영 시 true
  oauth2:
    backend-base-url: ${OAUTH2_BACKEND_BASE_URL:http://localhost:8080}
    frontend-base-url: ${OAUTH2_FRONTEND_BASE_URL:http://localhost:5173}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:}
            client-secret: ${GOOGLE_CLIENT_SECRET:}
```

## 8. 프론트엔드 연동

- `ky` 인스턴스(`shared/api/http.ts`): `Authorization: Bearer` 헤더 자동 주입
- 401 → refresh 1회 자동 재발급 (`POST /api/v1/auth/refresh`), 동시 요청은 단일 Promise로 제어
- 개발 환경: `VITE_DEV_BYPASS_AUTH=true`로 인증 우회 가능