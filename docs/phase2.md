# DochiBot Phase 2 — 근거 검증 및 평가 체계

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 개요

Phase 2는 검색된 청크의 근거 품질을 사전 검증하여, 근거가 불충분할 경우 LLM 생성을 건너뛰고 정책 기반 응답을 반환하는 계층이다. 검증 통과 시에만 LLM이 검색된 청크를 컨텍스트로 사용해 답변을 생성한다. 또한 오프라인 평가(Eval) 체계를 통해 검색 품질을 정량적으로 측정한다.

## 2. 근거 검증 (Evidence Verification)

### 2.1 통합 지점

`ChatUseCase.prepareExecution()`에서 검색 완료 직후 `decidePolicy()`를 호출하여 검증한다. 검증은 LLM 생성 이전(Pre-Generation)에만 수행되며, Post-Generation 검증 단계는 현재 구현되어 있지 않다.

검증 흐름:

```
HybridRetrievalService.retrieveTopChunks()
  → decidePolicy()
    → verify.enabled == false → 검증 건너뛰기 (LLM 생성)
    → chunks.isEmpty() → "NO_CHUNKS" (즉시 정책 응답)
    → evidenceVerifier.verify() → 불충분 시 정책 응답, 충분 시 LLM 생성
```

### 2.2 QueryTypeClassifier — 질의 유형 분류

`QueryTypeClassifier.classify()`는 질의 텍스트의 키워드를 분석하여 세 가지 유형으로 분류한다.

| 유형 | 키워드 | 설명 |
|------|--------|------|
| `WHAT_WHO` | what, who, which, 무엇, 누구, 어느, 어떤, 무슨, 설명 | 사실/개체 식별 질의 |
| `HOW_WHY` | how, why, method, process, 어떻게, 왜, 방법, 이유, 원인 | 과정/이유 설명 질의 |
| `OTHER` | (그 외) | 위 분류에 해당하지 않는 질의 |

분류 로직:
1. 질의를 소문자 정규화 후 토큰화 (영숫자 + 한글 범위)
2. `how/why` 키워드나 한국어 힌트("어떻게", "왜", "방법", "이유", "원인")가 하나라도 있으면 `HOW_WHY`
3. `what/who` 키워드가 있으면 `WHAT_WHO`
4. 한국어 힌트("무엇", "누구", "어느", "어떤", "무슨")가 있으면 `WHAT_WHO` (부분 문자열 매칭)
5. 둘 다 없으면 `OTHER`

### 2.3 DefaultEvidenceVerifier — 임계값 검증

`DefaultEvidenceVerifier.verify()`는 상위 N개의 청크를 순차적으로 검사하며, 하나라도 실패하면 `isSufficient = false`를 반환한다. 검사 순서와 각 임계값 규칙:

**순서 1: 청크 존재 여부**

청크가 없으면 `NO_CHUNKS`로 즉시 실패.

**순서 2: 문서 분산 제한** (`maxDistinctDocs`)

상위 `maxChunksToCheck`개 청크가 서로 다른 문서에서 왔는지 확인. `maxDistinctDocs`가 0보다 크고 실제 distinct 문서 수가 이를 초과하면 `TOO_MANY_DISTINCT_DOCS`로 실패. 기본값 0이면 이 검사는 비활성화.

**순서 3: 사실 충돌 검사** (`consistencyCheckEnabled`)

활성화된 경우(`consistencyCheckEnabled = true`), 상위 청크 간 버전 정보 충돌을 감지한다. 정규식 `(?i)(버전|version)\s*([0-9]+(?:\.[0-9]+)*)`로 청크 텍스트에서 버전 번호를 추출하고, 서로 다른 버전이 2개 이상 발견되면 `CONFLICTING_FACTS`로 실패. 기본값은 `false` (비활성화).

**순서 4: 동일 문서 내 지지도** (`minSameDocSupport`)

top1 청크와 동일한 문서에서 온 청크가 상위 `maxChunksToCheck`개 중 몇 개인지 확인. `minSameDocSupport`보다 적으면 `SAME_DOC_SUPPORT_BELOW_THRESHOLD`로 실패. 기본값 1이면 이 검사는 비활성화.

**순서 5: Top1-Top2 점수 차이** (`minTop1Top2Gap`)

top1의 finalScore와 top2의 finalScore 차이가 `minTop1Top2Gap`보다 작으면 `TOP1_TOP2_GAP_BELOW_THRESHOLD`로 실패. 점수 차이가 작다는 것은 top1의 우위가 불확실하다는 신호. 기본값 0.0이면 비활성화.

**순서 6: Top1 최소 점수** (`minTop1FinalScore`)

top1 청크의 finalScore가 `minTop1FinalScore` 미만이면 `TOP1_SCORE_BELOW_THRESHOLD`로 실패. 기본값 0.0이면 비활성화.

**순서 7: 토큰 커버리지** (`minTokenCoverage`)

질의 토큰 중 몇 퍼센트가 검색된 청크 텍스트에 등장하는지 계산. `adjustedMinTokenCoverage` 미만이면 `TOKEN_COVERAGE_BELOW_THRESHOLD`로 실패. 기본값 0.0이면 이 검사는 비활성화. 단, `adjustedMinTokenCoverage`가 0보다 크고 커버리지가 충분하면 이 시점에서 `OK`로 통과.

**순서 8: 기본 통과**

모든 검사를 통과하고 `minTokenCoverage`가 0이면 `OK`로 통과 (토큰 커버리지 = 0.0).

### 2.4 질의 유형별 토큰 커버리지 보정

`adjustedMinTokenCoverage()`는 질의 유형에 따라 기준 커버리지를 보정한다.

| 유형 | 보정치 | 근거 |
|------|--------|------|
| `WHAT_WHO` | `base + 0.1` | 사실형 질의는 검색어가 청크에 직접 등장해야 할 가능성이 높음 |
| `HOW_WHY` | `base - 0.1` | 설명형 질의는 동의어/우회 표현이 사용될 여지가 큼 |
| `OTHER` | `base` | 기본값 유지 |

보정된 값은 항상 [0.0, 1.0] 범위로 제한된다.

### 2.5 토큰화 규칙

`DefaultEvidenceVerifier`와 `HeuristicReranker`는 동일한 토큰화 규칙을 공유한다.

- 소문자 정규화
- 영숫자(`a-z0-9`)와 한글(`가-힣`) 이외의 문자를 구분자로 분리
- 한글 토큰: 길이 1 이상 유지
- 영문/숫자 토큰: 길이 2 이상만 유지 (1글자 stopword 제거)

### 2.6 검증 결과 데이터

`EvidenceVerification` 데이터 클래스가 검증 결과를 담는다.

| 필드 | 설명 |
|------|------|
| `isSufficient` | 근거 충분 여부 |
| `reason` | 실패 코드와 상세 (예: `TOP1_SCORE_BELOW_THRESHOLD\|minTop1FinalScore=0.5,actualTop1Score=0.3`) |
| `tokenCoverage` | 질의 토큰 커버리지 (0~1) |
| `top1Score` | top1 finalScore |
| `top1Top2Gap` | top1과 top2 점수 차이 (top2 없으면 null) |
| `sameDocSupportCount` | top1과 동일 문서에서 온 상위 청크 수 |
| `distinctDocCount` | 상위 청크의 서로 다른 문서 수 |
| `queryType` | 분류된 질의 유형 |
| `appliedMinTokenCoverage` | 질의 유형 보정이 적용된 최소 커버리지 임계값 |

## 3. 정책 응답 (Verify Policy)

검증 실패 시 `VerifyPolicy` enum에 따라 다음 응답 중 하나를 반환한다.

| 정책 | 응답 |
|------|------|
| `NO_EVIDENCE` | "문서에서 답을 찾을 수 없습니다." |
| `ASK_FOLLOWUP` | "정확한 근거를 찾기 어렵습니다. 질문을 구체화해 주시면 다시 찾아보겠습니다. '{질의 앞 120자}'" + 추가 정보 요청 안내 (문서 제목/파일명, 관련 키워드 2~3개, 범위) |

정책 응답은 LLM을 거치지 않고 즉시 SSE `metadata` → `delta`(정책 응답 텍스트) → `done` 이벤트로 스트리밍된다.

## 4. 설정 기본값

```yaml
dochibot.rag.verify:
  enabled: false               # 기본적으로 검증 비활성화
  policy: NO_EVIDENCE          # 실패 시 정책
  max-chunks-to-check: 5       # 검증 대상 상위 청크 수
  min-top1-final-score: 0.0    # 0이면 비활성화
  min-top1-top2-gap: 0.0       # 0이면 비활성화
  min-same-doc-support: 1      # 1이면 비활성화
  max-distinct-docs: 0         # 0이면 비활성화
  min-token-coverage: 0.0      # 0이면 비활성화
  consistency-check-enabled: false
```

기본적으로 모든 임계값 검사가 비활성화되어 있으며, 실무에서는 `minTop1FinalScore`, `minTokenCoverage` 등을 운영 데이터 기반으로 조정한다.

## 5. 평가(Eval) 체계

### 5.1 평가 데이터 형식

`phase2_eval_sample.json`은 다음 구조의 JSON 파일이다.

```json
{
  "version": 1,
  "items": [
    {
      "id": "q1",
      "query": "(예시) 문서 업로드는 어떻게 하나요?",
      "queryType": "how",
      "expected": {
        "chunkIds": [],
        "documentId": null,
        "documentTitleContains": "업로드"
      },
      "tags": [],
      "difficulty": null,
      "notes": "예시 데이터"
    }
  ]
}
```

`Phase2EvalItem` 필드:

| 필드 | 필수 | 설명 |
|------|------|------|
| `id` | 필수 | 평가 항목 식별자 (중복 불가) |
| `query` | 필수 | 사용자 질의 |
| `expected` | 필수 | 기대 근거 조건 (chunkIds / documentId / documentTitleContains 중 하나 이상) |
| `queryType` | 선택 | 질의 유형 |
| `tags` | 선택 | 분류 태그 |
| `difficulty` | 선택 | 난이도 |
| `notes` | 선택 | 비고 |

`Phase2EvalExpected`는 다음 중 하나 이상을 지정해야 한다:

- `chunkIds`: 정답 청크 UUID 목록 (빈 배열은 무시)
- `documentId`: 정답 문서 UUID
- `documentTitleContains`: 정답 문서 제목에 포함되어야 할 문자열 (대소문자 무관)

### 5.2 Phase2EvalValidator — 평가 데이터 검증

평가 데이터 로드 시 `Phase2EvalValidator.validate()`로 스키마 무결성을 검사한다.

- items가 비어있으면 오류
- 중복된 id가 있으면 오류
- 각 item의 id, query가 공백이면 오류
- expected가 null이거나 chunkIds/documentId/documentTitleContains가 모두 비어있으면 오류

### 5.3 Phase2EvalScorer — 랭킹 기반 채점

`Phase2EvalScorer.score()`는 검색 결과 목록에서 정답 청크의 순위를 찾아 다음 지표를 계산한다.

정답 매칭 우선순위:
1. `chunkIds`가 비어있지 않으면 → 청크 ID가 목록에 포함되는지
2. `documentId`가 있으면 → 문서 ID 일치 여부
3. `documentTitleContains`가 있으면 → 문서 제목에 해당 문자열 포함 여부 (대소문자 무관)

계산 지표:

| 지표 | 계산식 |
|------|--------|
| `rank` | 정답이 등장한 첫 번째 순위 (1부터 시작, 없으면 null) |
| `hit1` | rank == 1 |
| `hit3` | rank != null && rank <= 3 |
| `hit5` | rank != null && rank <= 5 |
| `reciprocalRank` | rank가 있으면 1/rank, 없으면 0.0 |

### 5.4 Phase2EvalRunnerTest — 평가 실행

`Phase2EvalRunnerTest`는 시스템 프로퍼티 `dochibot.eval=true`일 때만 실행되는 통합 테스트다.

실행 방법:
```bash
./gradlew.bat test \
  -Ddochibot.eval=true \
  -Ddochibot.eval.path=C:/path/phase2_eval.json \
  --tests com.dochibot.feature.retrieval.Phase2EvalRunnerTest
```

시스템 프로퍼티:

| 프로퍼티 | 설명 | 기본값 |
|----------|------|--------|
| `dochibot.eval` | 평가 실행 플래그 | (필수) |
| `dochibot.eval.path` | 평가 데이터 JSON 경로 | classpath:eval/phase2_eval_sample.json |
| `dochibot.eval.mock.enabled` | Mock 저장소 사용 여부 | false |
| `dochibot.eval.mock.path` | Mock 문서 데이터 경로 | classpath:eval/phase2_mock_documents_sample.json |
| `dochibot.eval.report.path` | 보고서 출력 경로 (.html 또는 .json) | (출력 안 함) |

실행 흐름:
1. 평가 데이터 로드 및 `Phase2EvalValidator` 검증
2. Mock 저장소 또는 실제 `HybridRetrievalService`로 각 질의에 대해 top-5 검색
3. `Phase2EvalScorer`로 각 항목 채점
4. 전체 Hit@1, Hit@3, Hit@5, MRR 집계
5. 실패 케이스 수집
6. `reportPath`가 지정된 경우 HTML 또는 JSON 보고서 출력

### 5.5 평가 보고서 형식

HTML 보고서는 Hit@1/Hit@3/Hit@5 비율, MRR, 실패 케이스 목록(질의 ID, 질의 텍스트, top1 문서 제목, top1 점수)을 포함한다. JSON 보고서는 동일한 정보를 머신 리더블 형태로 출력한다.

## 6. 운영 시나리오

### 6.1 검증 비활성화 (기본)

```yaml
dochibot.rag.verify.enabled: false
```

모든 질의에 대해 검색 결과를 그대로 LLM 컨텍스트로 전달한다. 청크가 전혀 없을 때만 "문서에서 답을 찾을 수 없습니다"를 반환한다. 개발 초기 단계나 검색 품질을 평가 중일 때 적합하다.

### 6.2 보수적 검증

```yaml
dochibot.rag.verify:
  enabled: true
  policy: NO_EVIDENCE
  min-top1-final-score: 0.1
  min-token-coverage: 0.3
  max-distinct-docs: 3
  min-same-doc-support: 2
```

top1 점수가 낮거나, 토큰 커버리지가 부족하거나, 너무 많은 문서에 흩어져 있거나, 동일 문서 내 지지가 부족하면 "문서에서 답을 찾을 수 없습니다"를 반환한다.

### 6.3 대화형 검증

```yaml
dochibot.rag.verify:
  enabled: true
  policy: ASK_FOLLOWUP
  min-top1-final-score: 0.15
  min-token-coverage: 0.2
```

근거가 불충분하면 사용자에게 추가 정보를 요청하는 후속 질문을 반환한다. 사용자 경험이 중요한 대화형 서비스에 적합하다.

### 6.4 버전 충돌 감지

```yaml
dochibot.rag.verify:
  enabled: true
  consistency-check-enabled: true
  policy: NO_EVIDENCE
```

동일한 개념에 대해 서로 다른 버전 정보를 가진 청크들이 검색되면 경고하고 응답을 차단한다. 버전 관리가 중요한 기술 문서에 적합하다.

### 6.5 메트릭 모니터링

`verify_decision` 카운터를 통해 검증 통과/실패 비율을 모니터링할 수 있다. 가능한 decision 값:

- `PASS`: 검증 통과
- `VERIFY_DISABLED`: 검증 비활성화 상태
- `NO_CHUNKS`: 청크 없음
- `POLICY_NO_EVIDENCE`: 검증 실패 → NO_EVIDENCE 정책
- `POLICY_ASK_FOLLOWUP`: 검증 실패 → ASK_FOLLOWUP 정책