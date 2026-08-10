# Concept Portfolio V2 Generic Role Semantic Multi-Domain Recovery 결과

## [STATUS]

- IMPLEMENTATION COMPLETE
- STATIC / TARGETED / MULTI-DOMAIN CONTRACT VERIFICATION COMPLETE
- LIVE IDEA / PLAN / CANDIDATE: ALREADY OBSERVED WORKING ACROSS MULTIPLE DOMAINS
- LIVE ROLE SEMANTIC FALLBACK: PENDING USER RETEST
- LIVE LEGAL-READY PORTFOLIO: PENDING USER RETEST
- LIVE FULL LEGAL MULTI-DOMAIN: PENDING USER RETEST
- LIVE HYPOTHESIS MULTI-DOMAIN: PENDING USER RETEST
- LIVE ONE-CLICK: PENDING USER RETEST
- PRODUCTION ENTRYPOINT: CORE PATH PRESERVED, REAL PRODUCTION CUTOVER NOT STARTED

## [AUTHORITATIVE HEAD]

- Branch: `rebuild/new-pipeline-v1`
- 기준 HEAD: `8a78a1a9672cee549062f7be2a2a611acf782ec8`
- branch 변경, commit, push는 수행하지 않았다.

## [MULTI DOMAIN LIVE FAILURE BASELINE]

- 사용자 실행 증거의 AI 면접 코치, B2B AI 영업 보조, 식품 물리 커머스, 지역 펫케어 마켓플레이스 모두 Idea/Plan/Candidate까지 통과했다.
- 네 시나리오 모두 역할 문구가 deterministic `AMBIGUOUS`로 남자 즉시 Fact Completion으로 내려갔고, completion child도 같은 이유로 소진되어 Legal-ready가 0이 되었다.
- 기존 one-click 실패는 구체적인 pre-Legal 소진 원인 대신 `FAILED / UNCLASSIFIED_SYSTEM_FAILURE`로 표시되었다.
- 사용자 생성 시나리오별 Notebook 사본과 recordings는 증거로만 읽었으며 수정하지 않았다.

## [ROOT CAUSE]

- deterministic 역할 판정의 `AMBIGUOUS`가 semantic 판정 대기 상태가 아니라 곧바로 누락 사실로 취급되었다.
- 역할 해석 fallback이 Candidate 전체에 batch로 연결되지 않았고 completion child에도 같은 판정 경로가 없었다.
- pre-Legal 제외 결과가 사용자 입력 요구와 합쳐졌으며, Legal-ready 0 전용 failure taxonomy가 없었다.
- Notebook `ONE_CLICK` 선택 시 앞선 staged 외부 호출이 이미 실행되어 fresh one-click 비용과 호출 수를 왜곡했다.

## [FROZEN WORKING COMPONENTS]

- Opportunity Kernel, planning, DISTINCT/VARIANT/DUPLICATE, adaptive planning, Candidate fidelity, system canonicalization을 변경하지 않았다.
- 공식 Legal evidence, redesign/replan, candidate-scoped NEEDS_INPUT, 7 Hypothesis, delta legal, downstream handoff 계약을 보존했다.
- Backend active route 교체와 production cutover는 수행하지 않았다.

## [DETERMINISTIC ROLE SEMANTICS]

- `assess_role_semantics`는 빠르고 명확한 MATCH, EXPLICIT_ABSENCE, MISMATCH만 확정한다.
- 표현이 불명확하면 `AMBIGUOUS`를 유지하며 Core flow에서 곧바로 Fact Completion을 호출하지 않는다.
- 기존 standalone completeness 호환 상태는 유지하되 Engine은 전체 deterministic 결과를 먼저 수집한 후 semantic 결과를 병합한다.

## [SEMANTIC ROLE FALLBACK]

- strict `BusinessRoleSemanticItem`/`BusinessRoleSemanticBatch` 계약을 추가했다.
- 판정 enum은 `MATCH | EXPLICIT_ABSENCE | MISMATCH | UNKNOWN`으로 제한했다.
- 입력에는 대상 값뿐 아니라 4개 역할 필드, actorRoles, transaction/payment flow, operating/partner model, solution mechanism, canonical architecture와 confidence를 포함한다.
- Provider는 역할 의미만 판정하며 새 사업 사실, Legal 결론, 임의 canonical code를 만들지 않는다.

## [ROLE BATCHING]

- 초기 Candidate 전체에서 ambiguous 역할을 모아 최대 20개를 한 번에 판정한다.
- Candidate별 호출을 만들지 않는다.
- batch 결과의 순서와 `(candidateId, field)` identity가 요청과 다르면 strict provider schema failure로 처리한다.
- schema preflight에 `BusinessRoleSemanticBatch`를 포함했다.

## [FACT COMPLETION RECHECK]

- 1차 semantic 병합 후 `MISMATCH/UNKNOWN` 또는 다른 실제 누락이 남은 Candidate만 Fact Completion으로 보낸다.
- 생성된 completion child들은 모두 Candidate validation을 먼저 통과해야 한다.
- validation을 통과한 child 전체를 다시 한 번 semantic batch로 판정한다.
- role detail은 field, presence, deterministicStatus, semanticUsed, semanticStatus, finalStatus, safeReason을 제공한다.
- MOCK completion도 completionRequirements가 지정한 필드만 수정하며 price 등 비대상 사실을 임의 확정하지 않는다.

## [ARCHITECTURE ROLE CONSISTENCY]

- `CONSISTENT | POTENTIAL_CONFLICT | NOT_ENOUGH_EVIDENCE` 진단을 추가했다.
- 예를 들어 고신뢰 `INTERMEDIARY/MARKETPLACE` descriptor와 명시적 중개 부재가 함께 있으면 `POTENTIAL_CONFLICT`다.
- 이 결과는 관찰용 diagnostic이며 Candidate hard reject나 Legal 결론이 아니다.

## [PRE LEGAL EXCLUSIONS]

- additive `preLegalExclusions`를 최종 결과에 추가했다.
- 항목에는 candidateId, reasonCode, affectedFields, recoveryAttempted, recoveryResolution, safeSummary가 들어간다.
- 초기 및 redesign/replan 내부의 pre-Legal 제외도 Engine 누적 결과에 보존한다.

## [REQUIRED INPUT SEPARATION]

- pre-Legal 소진 후보는 `requiredInputs`와 `unresolvedCandidates`에 넣지 않는다.
- 두 필드는 실제 사용자가 답할 수 있는 외부 사실 또는 LOCK 충돌만 담는다.
- 부분 Portfolio에서 제외 후보가 있어도 사용자 입력이 없으면 `requiredInputs=[]`을 유지한다.

## [FAILURE TAXONOMY]

- `NO_LEGAL_READY_CANDIDATES`를 추가했다.
- Legal 실행 전 후보가 모두 제외되면 `LEGAL_RECOVERING / LEGAL_READY_PORTFOLIO_EXHAUSTED / FAILED` trace를 먼저 남긴다.
- terminal diagnostics는 이 선행 구체 이벤트를 root cause로 선택하므로 failedStage는 `LEGAL_RECOVERING`, failureCode는 `NO_LEGAL_READY_CANDIDATES`다.
- 더 이상 이 경로를 `UNCLASSIFIED_SYSTEM_FAILURE`로 보고하지 않는다.

## [ONE CLICK EXECUTION GATING]

- Notebook에 `RUN_STAGED_CORE`, `RUN_STAGED_LEGAL`, `RUN_STAGED_FULL`을 추가했다.
- `ONE_CLICK`은 env/scenario/schema 준비 후 staged Idea/Plan/Candidate/Legal을 건너뛰고 fresh `run_full`만 실행한다.
- `CORE`는 Candidate validation까지만, `LEGAL_C1`은 C1 Legal까지만, `FULL_E2E`는 staged 전체만 수행한다.
- MOCK full smoke도 `FULL_E2E`이면서 MODE가 LIVE가 아닐 때만 실행한다.

## [NOTEBOOK METRICS]

- Candidate generated, valid initially, regenerated, recovered를 분리했다.
- Fact completion attempted, validated, accepted를 분리했다.
- Legal ready, reviewed, accepted, final portfolio를 분리했다.
- terminal failure summary도 completion child의 validation을 초기 Candidate valid 수에 합산하지 않는다.
- pre-Legal exclusions 전용 표시를 추가했다.

## [EMPTY HYPOTHESIS READINESS]

- Hypothesis가 0개면 다음 값을 명시한다.
  - All Values Semantically Valid: False
  - All Decisions Confirmed: False
  - Ready For Handoff: False
  - reason: `NO_SELECTED_CONCEPT_OR_HYPOTHESES`
- 빈 배열의 `all()` 결과가 readiness를 true로 만들지 않는다.

## [DIGITAL PHYSICAL DEPENDENCY]

- 사용자 AI 면접 LIVE 원본에서 `physicalActivities=['모의면접 진행', 'AI 분석 결과에 따른 피드백 제공']`가 확인되었고 precheck가 물리 활동 true로 표시되었다.
- 이는 실제 물리 이행이 아니라 디지털 수행 문구의 필드 오분류다.
- precheck는 배송, 포장, 현장, 대면, 방문, 설치 등 generic 물리 활동 marker가 있는 실질 문구만 물리 이행으로 센다.
- Candidate/Fact Completion prompt에는 AI 분석·디지털 피드백·온라인 상호작용을 physicalActivities에 넣지 말라는 규칙을 추가했다.
- 식품, 펫케어 같은 실제 배송·방문 서비스는 계속 물리 이행으로 분류된다.

## [PLAN DIVERSITY OBSERVATION]

- 사용자 LIVE에서 Family가 유사하거나 일부 Plan만 남은 현상은 관찰 대상으로 유지했다.
- 이번 단계는 역할 semantic recovery와 pre-Legal 정상화 범위이며 planning acceptance를 새로 차단하지 않는다.

## [MULTI DOMAIN TEST RESULTS]

- 7개 generic domain 전부 MOCK canonical `run_full`에서 usable Portfolio와 Legal-ready 1개 이상을 확인했다.
- ambiguous paraphrase batch 1회, completion child batch recheck, architecture-role conflict diagnostic, partial/all exhaustion, required input 분리, digital physical false-positive를 검증했다.
- production entrypoint에 MOCK Engine을 주입한 fresh non-LIVE smoke는 `READY_FULL`, 2개 Concept으로 통과했다.

## [TEST RESULTS]

- `python -m compileall -q ai/app/concept_portfolio_v2 ai/app/tasks/concept_portfolio_v2`: PASS
- strict schema preflight 7종: PASS
- targeted Concept Portfolio/관련 계약 tests: `244 passed in 38.69s`
- canonical Notebook code cell 47개 syntax: PASS
- fresh non-LIVE production entrypoint smoke: `READY_FULL 2`
- `git diff --check`: PASS (개행 변환 warning만 표시, whitespace error 없음)

## [NOT RUN]

- AI Provider LIVE
- MOLEG/공식 Legal LIVE
- 4개 사용자 시나리오의 수정 후 LIVE 재실행
- Backend route cutover
- full repository regression, postgresTest, Docker, browser, frontend production build
- commit/push

## [USER LIVE RETEST ORDER]

각 시나리오마다 fresh kernel에서 다음 순서로 수행한다.

1. `MODE='LIVE'`, `LIVE_TEST_LEVEL='ONE_CLICK'`, 대상 `LIVE_SCENARIO`를 설정한다.
2. Notebook 전체 셀을 위에서 아래로 한 번 실행한다.
3. staged Engine의 Idea/Plan/Candidate/Legal 외부 호출이 0이고 one-click Engine만 호출했는지 Provider usage를 확인한다.
4. `BUSINESS_ROLE_SEMANTIC_BATCH` trace와 역할별 deterministic/semantic/final status를 확인한다.
5. Fact Completion이 semantic MATCH 후보에 불필요하게 호출되지 않았는지 확인한다.
6. Legal ready, reviewed, accepted, final portfolio 수를 각각 확인한다.
7. 제외가 있으면 `preLegalExclusions`에만 있고 `requiredInputs`에는 실제 사용자 답변 항목만 있는지 확인한다.
8. 최종 Portfolio가 있으면 7 Hypothesis → 확인 → 필요 시 delta legal → downstream handoff를 진행한다.

권장 시나리오 순서:

1. `AI_INTERVIEW_COACH`
2. `B2B_AI_SALES_ASSISTANT`
3. `FOOD_PHYSICAL_COMMERCE`
4. `LOCAL_PETCARE_MARKETPLACE`

## [FILES MODIFIED]

- `ai/app/concept_portfolio_v2/models.py`
- `ai/app/concept_portfolio_v2/legal_fact_completeness.py`
- `ai/app/concept_portfolio_v2/providers.py`
- `ai/app/concept_portfolio_v2/engine.py`
- `ai/app/concept_portfolio_v2/schema_preflight.py`
- `ai/app/concept_portfolio_v2/diagnostics/notebook_view.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb` (사용자 출력·시나리오 선택 보존, source gate만 수정)
- `ai/tests/concept_portfolio_v2/test_generic_role_semantic_recovery_round2.py`
- 본 결과 문서와 matching progress/verification 문서

## [GIT DIFF --STAT]

- tracked 구현 범위 stat: 7 files changed, 2365 insertions, 4715 deletions.
- 큰 Notebook 수치는 사용자 LIVE output 누적 diff를 포함하며 이번 source gate 변경만의 규모가 아니다.
- 신규 파일 4개(test 1, 문서 3)는 untracked이므로 위 tracked stat에 포함되지 않는다.
- 사용자 소유 checkpoint, 시나리오별 Notebook 사본, recordings는 수정 범위에서 제외했다.
