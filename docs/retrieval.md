# DochiBot RAG 리트리벌 파이프라인

> 마지막 갱신: 2026-08-08, 코드 기준

## 1. 개요

DochiBot의 검색 파이프라인은 문서 내 정확한 근거 청크를 찾기 위해 단계적으로 동작한다.

```
사용자 질의
  → Gate (섹션 검색: dense + sparse → RRF → topM 섹션 선택)
    → Range-limited 청크 검색 (선택된 섹션 내에서 dense + sparse)
      → RRF 융합
        → 리랭크 (선택적: Heuristic → Cross-Encoder → LLM Judge)
          → 컨텍스트 구성 (중복 제거, 인용 제한)
            → LLM 생성
```

전체 흐름은 `HybridRetrievalService.retrieveTopChunks()`가 오케스트레이션한다. 각 단계는 설정에서 비활성화하거나 임계값을 조정할 수 있으며, 모든 기본값은 `DochibotRagProperties`에서 관리한다.

## 2. Gate 단계 — 섹션 검색

목적: 전체 문서 컬렉션에서 질의와 가장 관련 있는 섹션을 대략적으로 찾아, 이후 청크 검색 범위를 좁힌다.

### 2.1 Dense 검색 (pgvector 코사인 거리)

```sql
SELECT s.id, s.document_id, d.title, s.heading, s.section_path,
       (s.section_embedding <=> (:embedding)::vector) AS dist
FROM sections s
JOIN documents d ON d.id = s.document_id AND d.status = 'COMPLETED'
WHERE s.section_embedding IS NOT NULL
ORDER BY s.section_embedding <=> (:embedding)::vector
LIMIT :limit
```

- 연산자: `<=>`는 pgvector의 코사인 거리 연산자. 거리가 작을수록 유사도가 높다.
- 임베딩이 없는 섹션은 제외. 문서 상태가 `COMPLETED`인 것만 검색.
- `limit`: `gate.denseTopK` (기본값 20)

### 2.2 Sparse 검색 (PostgreSQL Full-Text Search)

```sql
SELECT s.id, s.document_id, d.title, s.heading, s.section_path,
       ts_rank_cd(s.section_tsv, q) AS rank
FROM sections s
JOIN documents d ON d.id = s.document_id AND d.status = 'COMPLETED'
, websearch_to_tsquery('simple', :query_text) q
WHERE s.section_tsv @@ q
ORDER BY rank DESC
LIMIT :limit
```

- `websearch_to_tsquery('simple', ...)`: 사용자 질의를 tsquery로 변환. `'simple'` 설정 사용 (스테밍/동의어 사전 미적용).
- `ts_rank_cd`: 커버 밀도 기반 랭킹 함수.
- `limit`: `gate.sparseTopK` (기본값 20)

### 2.3 섹션 RRF 융합

Dense, sparse 각 결과를 RRF(Reciprocal Rank Fusion)로 병합한 뒤 `gate.sectionsTopM`(기본값 5)개만 선택한다.

RRF 점수: `score = 1 / (k + rank)`, k = `fusion.rrfK` (기본값 60). 순위는 1부터 시작한다. dense와 sparse 양쪽에 등장한 섹션은 두 RRF 점수를 합산한다.

선택된 섹션 ID 목록이 다음 Range-limited 단계의 필터로 사용된다. Gate 단계에서 섹션을 하나도 찾지 못하면 Range-limited 단계는 전체 청크를 대상으로 검색한다.

## 3. Range-limited 단계 — 청크 검색

Gate에서 선택된 섹션 ID로 범위를 제한한 후 청크 단위 검색을 수행한다.

### 3.1 Dense 검색

```sql
SELECT c.id, c.document_id, d.title, c.section_id, s.section_path,
       c.text, c.page,
       (c.chunk_embedding <=> (:embedding)::vector) AS dist
FROM chunks c
JOIN documents d ON d.id = c.document_id AND d.status = 'COMPLETED'
LEFT JOIN sections s ON s.id = c.section_id
WHERE c.section_id IN (:section_id_0, :section_id_1, ...)
ORDER BY c.chunk_embedding <=> (:embedding)::vector
LIMIT :limit
```

- Gate 통과 섹션이 없으면 `WHERE c.section_id IS NOT NULL` 조건만 사용 (전체 검색).
- `limit`: `retrieval.denseTopK` (기본값 50)

### 3.2 Sparse 검색

```sql
SELECT c.id, c.document_id, d.title, c.section_id, s.section_path,
       c.text, c.page,
       ts_rank_cd(c.chunk_tsv, q) AS rank
FROM chunks c
JOIN documents d ON d.id = c.document_id AND d.status = 'COMPLETED'
LEFT JOIN sections s ON s.id = c.section_id
, websearch_to_tsquery('simple', :query_text) q
WHERE c.chunk_tsv @@ q
  AND c.section_id IN (...)
ORDER BY rank DESC
LIMIT :limit
```

- `limit`: `retrieval.sparseTopK` (기본값 50)

### 3.3 청크 RRF 융합

섹션 RRF와 동일한 알고리즘(k=60)으로 dense/sparse 청크 결과를 병합한다. 병합 후 최종 정렬은 RRF 점수 내림차순이며, 동점 시 코사인 거리 오름차순으로 정렬한다. 이 시점에서 각 청크의 `finalScore`는 `rrfScore`로 설정된다.

## 4. 리랭크 (Rerank)

설정 `rerank.enabled`가 `true`일 때만 활성화된다 (기본값 `false`). 비활성화 시 RRF 점수 순서를 그대로 사용한다.

### 4.1 리랭커 라우터

`RerankerRouter`가 `rerank.model` 값에 따라 구현체를 선택한다.

- `HEURISTIC`: 규칙 기반. 항상 사용 가능.
- `LLM_JUDGE`: LLM을 judge로 사용. `ChatModel` 빈이 있을 때만 주입.
- `CROSS_ENCODER`: 외부 HTTP 엔드포인트 호출. 주입 실패 시 `HEURISTIC`으로 폴백.

리랭크 대상은 RRF 병합 결과 중 상위 `rerank.candidatesTopK`(기본값 100)개.

### 4.2 Heuristic Reranker

규칙 기반 점수로, 모델 호출 없이 동작한다.

```
combined_score = sparseScore x 0.6 + tokenOverlapScore x 0.4
```

- `sparseScore`: PostgreSQL ts_rank_cd 점수 (정규화되지 않은 원시 값). FTS 매칭 강도를 반영.
- `tokenOverlapScore`: 질의 토큰과 청크 텍스트(문서 제목 + 섹션 경로 + 본문) 간 토큰 중복 비율. 한글은 1글자, 영문은 2글자 이상만 토큰으로 취급.
- 동점 시 RRF 점수 내림차순으로 정렬.

### 4.3 Cross-Encoder Reranker

외부 Cross-Encoder 모델 HTTP 엔드포인트에 질의-청크 쌍을 보내 점수를 받는다.

- `endpoint`가 비어있으면 RRF 순서로 폴백.
- `maxCandidates`(기본값 50)개까지만 엔드포인트에 전송. 초과분은 RRF 순서를 유지하며 꼬리에 추가.
- 재시도: 최대 3회, 지연 시간 50ms x 시도 횟수.
- `timeoutMs`(기본값 2000ms) 내 응답 없으면 RRF 폴백.
- `apiKey`가 설정되어 있으면 `Authorization: Bearer`와 `X-API-Key` 헤더를 함께 전송.
- 청크 텍스트는 `snippetChars`(기본값 400)까지 줄바꿈 제거 후 전송.

### 4.4 LLM Judge Reranker

LLM에게 질의와 후보 청크 목록을 보내 각 청크의 관련성을 0.0~1.0 점수로 평가받는다.

- 앙상블: `ensembleCalls`(기본값 1)회 호출한 점수의 중앙값(median)을 최종 점수로 사용.
- 청크 텍스트는 `centerTrim`으로 중앙부 `snippetChars`(기본값 400)자를 추출.
- `maxCandidates`(기본값 20)개까지만 LLM에 전송. 초과분은 RRF 순서 유지.
- 재시도: `maxAttempts`(기본값 2)회. JSON 파싱에 실패하면 `fallbackScore`(기본값 0.5) 사용.
- `timeoutMs`(기본값 3000ms).
- 모든 ensemble 호출이 실패하면 RRF 순서로 폴백.

리랭크가 완료되면 각 청크의 `finalScore`가 리랭크 점수로 대체된다. `RerankerRouter`가 `LLM_JUDGE`나 `CROSS_ENCODER` 빈을 찾지 못하면 자동으로 `HEURISTIC`으로 폴백한다.

## 5. 컨텍스트 구성

### 5.1 증거 청크 선택

`ChatAnswerFormatter.selectEvidenceChunks()`가 최종 컨텍스트로 사용할 청크를 선택한다.

- 최대 인용 개수: 6개 (`MAX_CITATION_COUNT`)
- 문서당 최대 인용: 2개 (`MAX_CITATIONS_PER_DOCUMENT`)
- 중복 제거: 문서ID + 페이지 + 섹션경로 + 본문 앞 180자의 조합으로 중복 판정
- 더 많은 청크가 필요하면 `requestedCount`를 늘려 검색 단계에서 더 많이 가져온다.

### 5.2 시스템 프롬프트 구성

`ChatUseCase.buildContextMessage()`가 다음 구조의 컨텍스트를 생성한다.

```
[문서 근거]
[1] 문서제목 - 섹션경로 p.페이지
청크텍스트(최대 1200자)

[2] 문서제목 - 섹션경로 p.페이지
청크텍스트(최대 1200자)
...
```

- 시스템 프롬프트에 "근거가 부족하면 '문서에서 답을 찾을 수 없습니다'라고 답하라"는 지시 포함.
- `<think>` 태그 출력 금지 지시.
- 청크가 하나도 없으면 근거 없는 상태의 기본 시스템 프롬프트를 사용.

## 6. 설정 기본값 요약

```yaml
dochibot.rag:
  gate:
    dense-top-k: 20
    sparse-top-k: 20
    sections-top-m: 5
  retrieval:
    dense-top-k: 50
    sparse-top-k: 50
  fusion:
    rrf-k: 60
  context:
    top-n: 8
  rerank:
    enabled: false
    candidates-top-k: 100
    model: HEURISTIC
    llm-judge:
      max-candidates: 20
      timeout-ms: 3000
      snippet-chars: 400
      max-attempts: 2
      fallback-score: 0.5
      ensemble-calls: 1
    cross-encoder:
      endpoint: ""
      api-key: ""
      timeout-ms: 2000
      max-candidates: 50
      snippet-chars: 400
  verify:
    enabled: false
    policy: NO_EVIDENCE
    max-chunks-to-check: 5
    min-top1-final-score: 0.0
    min-top1-top2-gap: 0.0
    min-same-doc-support: 1
    max-distinct-docs: 0
    min-token-coverage: 0.0
    consistency-check-enabled: false
```

## 7. 측정 및 로깅

### 7.1 Micrometer 메트릭

`RetrievalMetrics`가 다음 Micrometer 메트릭을 기록한다.

- `retrieval_latency_ms` (Timer): 전체 retrieval 소요 시간
- `rerank_latency_ms` (Timer, tag: model): 리랭크 모델별 소요 시간
- `candidate_count` (DistributionSummary, tag: stage): 단계별 후보 개수 (`section_dense`, `section_sparse`, `section_gated`, `chunk_dense`, `chunk_sparse`, `chunk_fused`, `returned`)
- `top1_score` (DistributionSummary): 최종 top1 점수
- `verify_decision` (Counter, tag: decision): 검증 결정 유형별 카운트

### 7.2 구조화 로그

`RetrievalStructuredLogger`가 INFO 레벨로 `retrieval_result` 이벤트를 JSON 형태로 기록한다. 쿼리 해시, 후보 개수, 상위 청크 ID, 리랭크 점수 샘플, 소요 시간 등을 포함한다.

## 8. 리랭커 트레이드오프

| 리랭커 | 장점 | 단점 | 적합한 상황 |
|--------|------|------|-------------|
| Heuristic | 지연 없음, 외부 의존성 없음, 결정적 | 의미적 관련성 평가 불가, FTS 점수에 과의존 | 초기 개발, 빠른 응답이 중요한 경우 |
| Cross-Encoder | 높은 정확도, 의미적 관련성 평가 | 외부 서비스 의존성, 네트워크 지연, GPU 리소스 필요 | 전문 도메인 검색, 높은 정밀도 요구 |
| LLM Judge | 유연한 평가, 컨텍스트 이해력 | 높은 지연, 비용, 비결정적, JSON 파싱 실패 가능성 | 복잡한 질의, 의미적 뉘앙스가 중요한 경우 |