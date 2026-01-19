# AGENTS.md — DochiBot

## 최상위 규칙(반드시 준수)
- 모든 답변은 한국어로 작성한다.
- 코드/파일/사용처를 프로젝트 전역에서 찾아야 하면, 추측하지 말고 Codanna MCP를 먼저 사용한다.
- 코드를 수정하기 전에 반드시 변경 계획(수정 파일 목록 + 테스트 커맨드)을 먼저 제시한다.
- 유저가 직접 빌드/테스트를 수행한다. 따라서 실행이 필요한 경우 “어떤 커맨드를 어떤 순서로 실행할지”만 제시한다.

## Codanna MCP 사용 규칙(우선순위 높음)
다음 상황에서는 반드시 Codanna MCP를 먼저 사용한다:
- “어디에 구현돼 있지?”, “사용처가 어디야?”, “호출하는 곳이 어디야?”, “영향 범위가 뭐야?” 같은 질문
- 리팩터링/시그니처 변경/도메인 모델 변경처럼 변경 범위가 넓을 가능성이 있는 작업
- 파일명/클래스명/엔드포인트 위치가 확실하지 않은 상태에서 추측으로 답을 만들게 되는 상황

툴 사용 순서(기본):
1) `semantic_search_with_context`: 기능/행위 기반 “자연어 질의”로 시작한다. (예: "<SymbolName> usages" 같은 키워드 나열 금지)
2) `analyze_impact`: 변경 영향 범위(호출 관계/파급) 파악
3) 정밀 심볼 탐색 필요 시에만 `find_symbol` → `get_calls`/`find_callers`

Codanna 결과를 사용한 뒤에는:
- “어떤 파일/심볼을 확인했고, 어디가 영향을 받는지”를 5줄 이내로 요약한 다음 수정/설계를 진행한다.

## 코드 스타일 가이드(VSA)
### 프로젝트 구조
feature 단위로 `controller/`, `application/`, `dto/`, `repository/`, `config/`, `exception/`.
shared entity는 `domain/entity/`, enum은 `domain/enums/`.

### 네이밍 규칙
- Controller: `<Feature>Controller` (예: `FolderCreateController`)
- Use Case: `<Action><Entity>UseCase` (예: `CreateFolderUseCase`)
- DTO: `<Action><Entity>Request/Response` (예: `CreateFolderRequest`)
- Exception: `<Problem>Exception`