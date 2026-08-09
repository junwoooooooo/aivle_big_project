# Concept Portfolio Engine V2 Lab 결과

## 상태

**IMPLEMENTATION COMPLETE**

**MOCK/STATIC/TARGETED VERIFICATION COMPLETE**

**LIVE RUNTIME ACCEPTANCE PENDING USER TEST**

기준 브랜치 `rebuild/new-pipeline-v1`, 조사 HEAD `c2b1921f0dc73c33b89b3a345d651f28cd1540e7`에서 기존 v1 production flow를 수정하지 않고 격리된 V2 Python Lab/Core를 구현했다.

## 발견·재사용한 현행 자산

상세 표는 [CURRENT_REUSABLE_ASSETS.md](CURRENT_REUSABLE_ASSETS.md)에 있다.

- `IdeaBriefDerivationInput`과 13개 `FieldKey`, required 3개, optional 10개를 adapter에서 직접 재사용했다.
- 현행 `USER_INPUT/USER_CONFIRMED + LOCKED` 권위와 AI가 LOCK할 수 없다는 의미를 보존했다.
- LIVE Safety는 현행 `execute_idea_brief_derivation` 경로를 사용한다.
- LIVE structured output은 현행 `execute_structured_prompt`와 timeout/error 계약을 사용한다.
- 최종 Candidate는 현행 `ConceptCandidateResult`와 31개 `valueSemantics` strict validator를 그대로 사용한다.
- distinctness 입력은 현행 21개 `BusinessFingerprint` 차원을 재사용한다.
- LIVE Legal은 현행 `LegalFactPattern`, official evidence pipeline, MOLEG, `execute_concept_legal_review`를 사용한다.
- Concept 선택 후 7개 hypothesis 확인 의미와 `market-analysis-seed-snapshot-v1`, `marketing-source-snapshot-v1` 필드 변환을 그대로 반영했다.

## V2 architecture

```text
Idea Brief Adapter → Safety Gate → Seed/Open Design Space
    → Dynamic Plan Pool → Lock/Anchor/Mechanics 검증 → 최대 5개 선택
    → Full Candidate 확장 → Schema/Lock/Anchor/Plan/Distinctness 검증
    → Structural Legal Precheck → Full Legal
       ├─ ACCEPT
       ├─ REDESIGN_WITHIN_LINEAGE (parent/lineage/round 유지)
       ├─ REPLAN_REQUIRED (1회 budget)
       └─ NEEDS_INPUT (Legal-LOCK 충돌)
    → Final Portfolio → Concept 선택 → 7 Hypothesis 확정
    → Market Analysis Seed + Marketing Source Handoff
```

Core 공개 진입점은 `from app.concept_portfolio_v2 import ConceptPortfolioEngine`이다. Notebook에는 business logic을 넣지 않았다.

## 기존과 달라진 핵심 철학

- fixed 5 lens를 generation hard constraint로 사용하지 않는다.
- 정확히 5개를 강제하지 않고 `producedConceptCount <= maxConcepts <= 5`를 보장한다.
- 열린 설계 차원과 LOCK 밀도에 따라 `EXPLORE/REFINE/AS_IS`, diversity capacity, suggested max를 계산한다.
- 같은 problem/target/사용자 LOCK은 정상적인 portfolio 공통점이다. 중복은 solution, operation, partner, transaction, commercial, fulfillment 등 business mechanics로 판단한다.
- Plan을 먼저 작게 생성·검증한 뒤 통과 Plan만 Full Candidate로 확장한다.
- origin/anchor 보존과 portfolio duplicate를 별도 검사한다.
- Legal redesign은 새 Concept이 아니라 parent와 같은 lineage의 자식이다. parent와 self-duplicate 비교하지 않는다.
- 핵심 구조가 막히면 작은 replan budget으로 다른 plan을 선택한다. LOCK과 Legal change가 충돌하면 재생성하지 않고 `NEEDS_INPUT`이다.

## Seed / Lock / Anchor / Open Space

- required: `ideaOverview`, `problem`, `targetUsers`
- optional: `targetRegion`, `knownCompetitors`, `revenueModel`, `price`, `channels`, `differentiators`, `budgetConstraint`, `teamConstraint`, `timelineConstraint`, `otherConstraint`
- `hardLocks`: 현재 Idea Brief의 non-empty `LOCKED` 값
- `semanticAnchors`: idea intent, problem, target user opportunity
- `openDimensions`: solution/value/operation/supply/partner/transaction/fulfillment/platform/commercial/data/physical 중 LOCK으로 제한되지 않은 차원

## Planner / Diversity / Candidate

- 기본 요청 5개일 때 7개 plan pool을 한 번에 생성할 수 있다.
- invalid lock/anchor plan과 mechanics clone을 제거하고 diversity capacity와 max 안에서 선택한다.
- Candidate는 31개 현행 business field, 31개 semantics, 21개 fingerprint 호환을 유지한다.
- Candidate 검사는 schema, hard lock, semantic anchor, plan fidelity, 다른 final Candidate와의 mechanics distinctness로 분리했다.

## Portfolio Count

- `READY_FULL`: 요청 최대치까지 유효 Concept 확보
- `READY_LIMITED`: 1~4개 유효 Concept만 확보; 억지 추가 금지
- `NEEDS_INPUT`: 입력 또는 Legal-LOCK 결정 필요
- `FAILED`: 0개 유효 Concept 또는 Provider/schema/replay/system 실패

## Legal / Redesign / Replan

- Precheck 표시는 항상 `Structural risk precheck — not final legal review`이다.
- V2 Legal route: `ACCEPT`, `REDESIGN_WITHIN_LINEAGE`, `REPLAN_REQUIRED`, `NEEDS_INPUT`, `SYSTEM_FAILURE`.
- same-lineage redesign은 `parentCandidateId`, `lineageId`, `redesignRound`를 유지한다.
- redesign identity drift 및 다른 portfolio Candidate와의 duplicate를 별도 검사한다.
- replan은 기본 1회이며, 실패·기존 선택 plan과 mechanics가 다른 pool plan만 사용한다.
- Legal-LOCK 충돌은 `conflictingLock`, `currentValue`, `requiredLegalChange`, `reason`, `possibleUserAction`을 반환한다.
- MOCK Legal은 official-evidence-shaped fixture이며, LIVE는 실제 현행 MOLEG/evidence task를 호출한다.

## Downstream handoff

- 선택 Candidate에서 현행 7개 hypothesis를 생성한다.
- 사용자 LOCK hypothesis는 현재 production 의미와 같이 자동 확정 상태가 되고, 나머지는 accept/edit 후 확정한다.
- 7개가 모두 확정되지 않으면 handoff `FAIL`이다.
- 호환 payload:
  - `market-analysis-seed-snapshot-v1`, schema `2.0`
  - `marketing-source-snapshot-v1`, schema `2.0`
- payload와 함께 source provenance, V2→downstream field mapping, transformed/required 여부를 제공한다.

## MOCK / REPLAY / LIVE

- MOCK: 7개 deterministic scenario로 full, limited, duplicate, redesign, replan, needs-input을 재현한다.
- REPLAY: canonical request hash와 일치하는 기록만 사용하며 miss 시 `REPLAY_MISS`; MOCK fallback 없음.
- LIVE: 현행 structured provider와 official Legal pipeline을 호출하고 성공 응답을 redacted record로 저장한다.
- timeout 기본값은 현행 `AI_PROVIDER_TIMEOUT_SECONDS=60`이다.
- transient retry는 현행 v1 값과 같은 호출당 최대 2회, 2초/5초 fallback, Retry-After 최대 15초를 사용한다.
- trace에는 raw secret, Authorization, chain-of-thought를 기록하지 않는다.

## Notebook

- 경로: `ai/notebooks/concept_portfolio_v2_lab.ipynb`
- 36개 셀, 00~31 단계 설명, 상단 `MODE` 한 곳 변경, 직접 `TEST_INPUT` 수정, 단계별 실행, one-click full run, partial/heavy/replay 시나리오를 제공한다.
- viewer는 pandas가 있으면 DataFrame, 없으면 읽을 수 있는 row 구조로 반환한다.
- 실행 안내: `ai/notebooks/CONCEPT_PORTFOLIO_V2_LAB_README.md`

## Trace 예

```text
[SEED_ANALYZING][ANALYZED] 필수 3개와 LOCK 값을 분류
[PLANNING][GENERATED] Plan pool=7
[PLAN_VALIDATING][REJECTED] reasonCode=PLAN_DUPLICATE
[EXPANDING][EXPANDED] P1 → C1
[CANDIDATE_VALIDATING][VALIDATED] PASS
[LEGAL_REVIEWING][REVIEWED] REDESIGN_WITHIN_LINEAGE
[LEGAL_RECOVERING][REDESIGNED] Parent=C1 Child=C1-R1 SameLineage=true
[PORTFOLIO_VALIDATING][HANDOFF_VALIDATED] PASS
[READY][COMPLETED] READY_FULL
```

## fixture

- `food_minimal`
- `food_partial_lock`
- `food_heavy_lock`
- `legal_redesign`
- `legal_replan`
- `lock_legal_conflict`
- `duplicate_plans`

## 실제 수행한 검사

| 검사 | 결과 |
|---|---|
| `python -m compileall -q app/concept_portfolio_v2` | PASS |
| V2 targeted pytest | **23 passed in 0.39s** |
| Notebook JSON/nbformat 기본 구조 검사 | PASS |
| Notebook 18개 code cell top-level-await 구문 검사 | PASS (`36` total cells) |
| 직접 MOCK full run | `READY_FULL`, 5 concepts, handoff `PASS`, runtime `READY`, 11 provider-stage calls |
| `git diff --check` | PASS (CRLF 변환 warning만 있음) |

## 의도적으로 실행하지 않은 검사

- `nbconvert --execute`: 현재 `.venv`에 `jupyter-nbconvert`가 설치되어 있지 않아 **NOT RUN**. `requirements-dev.txt`에는 설치 항목을 추가했다.
- LIVE AI Provider: 비용·외부 key가 필요하므로 NOT RUN.
- LIVE MOLEG/Legal: 외부 key와 실제 근거 조회가 필요하므로 NOT RUN.
- Backend/Frontend/Docker/full regression/production build: 이번 격리 Lab 범위가 아니며 fast profile에 따라 NOT RUN.

## 변경 파일

추가:

- `ai/app/concept_portfolio_v2/` 아래 공개 API, models, adapters, providers, engine, diagnostics 7개 파일
- `ai/fixtures/concept_portfolio_v2/` 아래 fixture 7개
- `ai/tests/concept_portfolio_v2/test_engine.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`
- `ai/notebooks/CONCEPT_PORTFOLIO_V2_LAB_README.md`
- `docs/rebuild/concept-portfolio-v2/CURRENT_REUSABLE_ASSETS.md`
- 본 결과 문서

수정:

- `ai/requirements-dev.txt`: Jupyter/nbconvert/nbformat/pandas 개발 의존성 추가

기존 v1 Python/Java, DB migration, Backend controller/worker, Frontend 파일은 수정하지 않았다.

## 알려진 위험

1. LIVE plan/candidate structured schema는 실제 Provider smoke 전까지 모델별 response-schema acceptance가 미확정이다.
2. LIVE Legal은 MOLEG availability, registry version, 공식 근거 조회 결과에 따라 `NEEDS_INPUT`/failure가 달라진다.
3. downstream adapter는 Java factory의 현재 field mapping을 Python에서 mirror한다. production integration 전에는 Java fixture/validator와 교차 contract test를 추가해야 한다.
4. 실제 LIVE recording은 비밀·비용 문제로 저장소에 포함하지 않았다. REPLAY 예제는 사용자가 LIVE 성공 후 생성한다.
5. fresh-kernel Notebook Run All은 nbconvert 미설치 때문에 아직 자동 acceptance가 아니다.

## 정확한 continuation point

사용자는 `ai` 가상환경에 `requirements-dev.txt`를 설치하고 MOCK Notebook Run All을 수행한다. 그 결과가 통과하면 환경변수를 설정해 LIVE Provider/Legal 1회 acceptance를 실행한다. 이 검증 전에는 production route, DB, Backend orchestration, Frontend를 V2로 연결하지 않는다.
