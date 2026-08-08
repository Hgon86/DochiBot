# 채팅 메모리 전략

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 개요

DochiBot의 채팅은 **세션 기반 대화 메모리**와 **RAG 컨텍스트 주입**을 결합한다. 사용자 질문은 Spring AI의 `ChatMemory` advisor를 통해 직전 대화 이력을 포함하며, 동시에 하이브리드 리트리벌로 문서 근거를 주입받는다.

## 2. 데이터 모델

### 2.1 ChatSession

`chat_sessions` 테이블 — 클라이언트가 유지하는 세션 키를 서버에서 추적한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | `uuid PK` | 세션 UUID (앱에서 UUIDv7 생성) |
| `external_session_key` | `varchar(128) UNIQUE` | 프론트엔드가 관리하는 세션 식별 키 |
| `owner_user_id` | `uuid FK → users.id` | 세션 소유자 (nullable) |
| `created_at` | `timestamptz` | 생성 시각 |

- `external_session_key`는 UNIQUE 제약조건을 가진다.
- 세션 소유권 검증: 다른 사용자의 세션 키로 접근 시 `CHAT_SESSION_FORBIDDEN` 오류 반환.
- 동시 생성 충돌: `DuplicateKeyException` 또는 `DataIntegrityViolationException` 발생 시 재조회하여 resolve.

### 2.2 ChatMessage

`chat_messages` 테이블 — 각 대화 턴을 저장한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | `uuid PK` | 메시지 UUID (UUIDv7) |
| `chat_session_id` | `uuid FK → chat_sessions.id` | 소속 세션 (CASCADE 삭제) |
| `role` | `varchar(16)` | `USER` 또는 `ASSISTANT` |
| `content` | `text` | 메시지 본문 |
| `citations_json` | `jsonb NULL` | AI 응답 근거 정보 (ASSISTANT 전용) |
| `created_at` | `timestamptz` | 생성 시각 |

- 인덱스: `(chat_session_id, created_at)` 복합 인덱스로 시간순 조회 최적화.
- `citations_json`은 RAG가 생성한 근거 목록을 JSON 배열로 저장한다.

### 2.3 ChatRole enum

```kotlin
enum class ChatRole { USER, ASSISTANT }
```

## 3. 대화 메모리 저장/조회 방식

### 3.1 메시지 저장

`ChatMessageWriter.insert()` → `chat_messages`에 직접 INSERT (R2DBC DatabaseClient 사용).

```sql
INSERT INTO chat_messages (id, chat_session_id, role, content, citations_json, created_at)
VALUES (:id, :chatSessionId, :role, :content, :citationsJson::jsonb, :createdAt)
```

- ASSISTANT 메시지는 `citationsJson`이 포함된다.
- USER 메시지는 `citationsJson`이 `null`이다.

### 3.2 Spring AI ChatMemory 연동

`ChatUseCase.stream()`은 Spring AI의 `ChatClient`를 사용하며, advisor에 `CONVERSATION_ID` 파라미터로 세션 키를 전달한다:

```kotlin
chatClient.prompt()
    .system(contextMessage)
    .user(requestMessage)
    .advisors { it.param(ChatMemory.CONVERSATION_ID, prepared.sessionKey) }
    .stream()
```

Spring AI의 `ChatMemory` 구현체가 이 `CONVERSATION_ID`를 기준으로 DB에서 직전 대화 이력을 조회하여 프롬프트에 포함시킨다.

### 3.3 최대 메시지 수 제한

`dochibot.ai.chat.memory.max-messages` (환경변수 `DOCHIBOT_CHAT_MEMORY_MAX_MESSAGES`, 기본 `10`)

- 대화 윈도우에 포함할 최대 메시지 수를 지정한다.
- Spring AI의 `ChatMemory` 구현체가 이 값을 기준으로 오래된 메시지를 제외한다.
- `DochibotAiProperties.Chat.Memory.maxMessages`로 바인딩된다.

## 4. 전체 요청-응답 흐름

```
1. POST /api/v1/chat/stream { message, sessionId?, topK? }
   → JWT에서 userId 추출
   → sessionId가 없으면 UUIDv7 생성

2. ChatSession 조회 또는 생성
   → external_session_key로 조회 → 없으면 신규 생성
   → 소유권 검증 (다른 사용자 세션이면 403)

3. USER 메시지 DB 저장 (ChatMessageWriter)

4. RAG 리트리벌
   → 임베딩 생성 → HybridRetrievalService → 하이브리드 검색
   → EvidenceVerifier 근거 검증
   → 검증 실패 시 정책 응답(NO_EVIDENCE 또는 ASK_FOLLOWUP)

5. SSE 스트리밍 응답
   → metadata 이벤트: { sessionId, citations }
   → delta 이벤트: 증분 텍스트 (청크 단위)
   → done 이벤트: { sessionId, answer }
   → 오류 시 error 이벤트

6. ASSISTANT 메시지 DB 저장 (ChatMessageWriter)
   → content: 최종 답변
   → citationsJson: 근거 정보 JSON 배열
```

## 5. SSE 이벤트 명세

| 이벤트 | payload 필드 | 설명 |
|--------|-------------|------|
| `metadata` | `sessionId`, `citations` | 세션 키와 근거 목록을 먼저 전달 |
| `delta` | `delta` | LLM 생성 증분 텍스트 |
| `done` | `sessionId`, `answer` | 최종 답변과 세션 키 |
| `error` | `sessionId`, `message` | 오류 메시지 |

- `metadata`는 스트림 시작 직전에 한 번만 전송된다.
- `delta`는 토큰 생성마다 여러 번 전송될 수 있다.
- `done`은 스트림 종료 시 한 번 전송된다.

## 6. Citations (인용) 저장

### 6.1 저장 형식

`chat_messages.citations_json` (jsonb)에 다음과 같은 JSON 배열로 저장된다:

```json
[
  {
    "documentId": "uuid",
    "documentTitle": "문서명",
    "snippet": "근거 텍스트 (최대 300자)",
    "page": 3,
    "section": "섹션 경로",
    "score": 0.951
  }
]
```

### 6.2 참조 표현

답변 텍스트에는 `[1]`, `[2]` 형태의 근거 번호가 포함되며, 이 번호는 citations 배열의 인덱스(0-based + 1)에 대응한다.

## 7. 컨텍스트 빌드 전략

`ChatUseCase.buildContextMessage()`는 RAG 검색 결과를 시스템 프롬프트로 구성한다:

- 청크가 없는 경우: "문서에서 찾을 수 없습니다"라고 답변하도록 지시
- 청크가 있는 경우: 각 청크를 `[번호] 문서제목 섹션 p.페이지` 형식의 헤더와 1200자 텍스트로 구성
- `<think>` 태그 출력 금지, 추론 과정 출력 금지, 최종 답변만 출력하도록 시스템 프롬프트에 명시

이 컨텍스트 메시지는 `ChatMemory`가 제공하는 대화 이력과 함께 LLM에 전달된다. 즉, LLM은 **(1) 직전 N개 메시지의 대화 이력**과 **(2) 현재 질문에 대한 문서 근거**를 함께 받는다.

## 8. 설정 키 참조

```yaml
dochibot:
  ai:
    chat:
      memory:
        max-messages: ${DOCHIBOT_CHAT_MEMORY_MAX_MESSAGES:10}   # 대화 윈도우 크기
```

## 9. 프론트엔드 연동

`frontend/src/app/routes/chat.tsx` (ChatPage):

- React 상태로 `sessionId`를 관리 (빈 문자열이면 서버가 자동 생성)
- `topK` 슬라이더 (1~12, 기본 8)
- `streamChatMessage()`로 SSE 스트림 수신 → `onMetadata`/`onDelta`/`onDone` 콜백 처리
- `onMetadata`: sessionId 저장, citations 표시 준비
- `onDelta`: 증분 텍스트 누적
- `onDone`: 최종 answer로 치환, sessionId 갱신
- `<think>` 태그 제거, box-drawing 문자 정리 등 sanitize 처리
- Session ID를 유지하면 같은 대화 컨텍스트를 이어서 테스트 가능