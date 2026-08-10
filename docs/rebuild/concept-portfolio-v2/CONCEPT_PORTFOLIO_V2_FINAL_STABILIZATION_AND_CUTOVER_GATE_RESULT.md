# Concept Portfolio V2 최종 안정화 및 이식 Gate 결과

## [현재 상태]

- FINAL STABILIZATION IMPLEMENTATION COMPLETE
- STATIC / ADVERSARIAL / REGRESSION VERIFICATION COMPLETE
- CURRENT LIVE FULL E2E: PARTIALLY PROVEN
- USER FINAL FULL E2E VALIDATION: PENDING
- CUTOVER GATE: PENDING

작업 기준 branch는 `rebuild/new-pipeline-v1`, 시작 HEAD는 `c43e6e1f8fdea1a4a16d5d0a838bc2bab924fefb`이다. commit, push, branch 변경은 수행하지 않았다.

## [이번 작업 이전 LIVE 기준]

- Idea 이해부터 Plan, Candidate, 공식 Legal, Legal recovery, 최종 Portfolio, 7 Hypothesis, 사용자 확정, Market/Marketing Snapshot까지 실제 실행이 관찰됐다.
- `B2B_AI_SALES_ASSISTANT`, `WEEKEND_TRIP_PLANNER`는 실제 FULL_E2E와 Downstream `CONTRACT_PASS`가 확인된 성공 기준선이다.
- `FOOD_PHYSICAL_COMMERCE`는 Legal fact dependency batch 응답 순서가 요청 순서와 달라 전체 흐름이 중단됐다.
- Fact Completion은 전체 Candidate형 응답 때문에 요청 외 fact가 함께 반환되는 위험이 실제 LIVE에서 관찰됐다.
- `WEEKEND_TRIP_PLANNER`에서는 온라인 일정·예약 연결 서비스에 배송·현장 방문·설치가 들어간 자기모순 fact가 관찰됐다.

## [이번에 수정한 3가지 문제]

1. 의미 판정 batch를 배열 순서가 아닌 business identity key로 검증한다.
2. 사업정보 보완 Provider schema를 요청 필드만 존재하는 동적 schema로 제한한다.
3. Legal 전에 다섯 관계의 명백한 사업정보 자기모순을 검사하고 문제 필드만 최대 1회 정정한다.

이 세 범위 밖의 개선 과제는 추가하지 않았다.

## [수정하지 않은 동결 영역]

Idea Brief, Safety, Interpretation, Opportunity Kernel, Planning/Selection/Replenishment, Portfolio relation 정책, Candidate 생성/Fidelity/재생성/Reserve, architecture 분류 정책, Business Role 의미 정책, Official Legal/MOLEG/Evidence/repair, Legal redesign/replan, NEEDS_INPUT, 7 Hypothesis, 사용자 확인, Delta Legal, Snapshot/Handoff, production `run_full` 구조는 재설계하거나 완화하지 않았다.

## [Batch 결과 안정성]

- Legal dependency: `(candidateId, dependencyType)`
- Business Role: `(candidateId, field)`
- Architecture: `(entityId)`
- Hypothesis: `(hypothesisType)`

위 key의 집합과 개수를 검증하고 결과를 요청 key 순서로 canonicalize한다. Provider 응답 순서는 자유다. 누락, 중복, 요청 외 key, 변경된 ID는 `RESULT_SCHEMA_INVALID`와 `missing/duplicate/extra` 진단으로 실패한다. Architecture 응답에는 분류 정책 변경 없이 매칭용 `entityId`만 추가했다.

## [Targeted Fact Completion]

Provider는 더 이상 모든 nullable 필드를 가진 큰 patch schema를 받지 않는다. `sellerRole`만 필요하면 schema에는 `sellerRole`만, `sellerRole + personalDataUsage`가 필요하면 두 필드만 존재한다. Concept 이름, 문제, 대상, 가치, solution, feature, differentiator, 가격, 수익모델은 Provider schema에 들어갈 수 없다.

System은 응답을 전체 `LegalFactCompletionPatch` 내부 형식으로 정규화한 뒤 기존 Candidate에 non-null 값만 병합한다. 요청 외 필드와 LOCK 변경을 차단한다. 성공 조건은 요청 필드의 실질 변경 또는 명시적 불필요 확인, 전체 Candidate validation PASS, completeness 재검사 COMPLETE의 동시 충족이다.

## [사업정보 정합성]

가벼운 `ConceptFactConsistency` 검사는 다음 관계만 본다.

- 서비스 방식 ↔ 물리 활동
- 거래 방식 ↔ 중개 역할
- 거래/결제 ↔ 판매자 역할
- 파트너 운영 ↔ 사업 파트너 정보
- 실제 데이터 사용 ↔ 개인정보 처리 정보

결과는 `CONSISTENT / POTENTIAL_CONFLICT / INVALID_FACT`다. 순수 디지털 동작과 근거 없는 배송·방문·설치, 중개 필드의 파트너 협력 설명처럼 명백한 자기모순만 `INVALID_FACT`다. `POTENTIAL_CONFLICT`는 진단만 남긴다. `INVALID_FACT`는 해당 fact 필드만 동적 schema로 최대 1회 정정하며 Candidate validation과 정합성 재검사를 다시 수행한다. 계속 실패하면 해당 Candidate만 pre-Legal에서 제외한다.

## [기존 성공 Scenario 회귀]

MOCK canonical `run_full(auto_confirm_hypotheses=True)`로 다음 5개 Scenario를 다시 검증했다.

- B2B_AI_SALES_ASSISTANT
- WEEKEND_TRIP_PLANNER
- FOOD_PHYSICAL_COMMERCE
- OFFICE_EQUIPMENT_SUBSCRIPTION
- CAMPUS_SECONDHAND

모두 Portfolio 1개 이상, `READY_FULL` 또는 `READY_LIMITED`, 7 Hypothesis 확정, Handoff `CONTRACT_PASS`를 만족했다. 이는 정적·MOCK 회귀 결과이며 새로운 LIVE 성공으로 간주하지 않는다.

## [잘못된 Provider 응답 테스트]

- 응답 순서 변경: PASS
- 요청 외 필드 반환: Candidate-scoped 차단
- 요청 필드 미변경: Provider noncompliance 차단
- 동일 batch 결과 중복: 차단
- batch 결과 누락: 차단
- 요청 외 batch 결과 추가: 차단
- 잘못된 Candidate ID: 차단
- 디지털 서비스의 거짓 물리 활동: `INVALID_FACT`, 1회 targeted repair
- 중개 필드의 파트너 협력 설명: `INVALID_FACT`
- 한 Candidate repair 실패 시 다른 Candidate 계속 진행: PASS

## [전체 테스트 결과]

- `python -m compileall -q ai/app`: PASS
- strict schema preflight: PASS
- Concept Portfolio 전체 targeted tests: **228 passed**
- Batch reorder/duplicate/missing/extra/wrong ID: PASS
- Dynamic completion schema/scope/unchanged/identity preservation: PASS
- 사업정보 정합성/physical false fact/intermediary false fact: PASS
- B2B/Travel/Food/Office/Campus regression: PASS
- Legal evidence/Hypothesis/Handoff/production entrypoint: targeted suite에서 PASS
- Notebook JSON parse/code cell compile: PASS, code cell 47개
- `git diff --check`: PASS; 줄바꿈 변환 warning만 존재

## [사용자 FULL_E2E 재검증 대상]

1. `FOOD_PHYSICAL_COMMERCE`: FULL_E2E에서 dependency batch 순서 변경 내성 확인
2. `OFFICE_EQUIPMENT_SUBSCRIPTION`: 필요한 fact의 동적 schema 보완 성공 확인
3. `WEEKEND_TRIP_PLANNER`: 잘못된 물리 활동이 Legal/Handoff로 넘어가지 않는지 확인
4. `B2B_AI_SALES_ASSISTANT`: 기존 FULL_E2E `CONTRACT_PASS` 회귀 확인
5. `CAMPUS_SECONDHAND`: Candidate 단위 실패 격리와 최종 Legal resolution 확인
6. FULL_E2E 안정 후 B2B 또는 Travel 1개로 ONE_CLICK production `run_full` smoke

모든 Scenario가 Final 5이거나 모두 Legal ACCEPT일 필요는 없다. 시스템 예외 없이 명확한 partial result와 유효 Portfolio 1개 이상이면 `READY_LIMITED`는 정상이다.

## [아직 실행하지 않은 항목]

- AI Provider LIVE
- MOLEG LIVE
- 사용자 5개 Scenario FULL_E2E
- 사용자 최종 ONE_CLICK production smoke
- Backend/DB/Frontend route cutover
- Docker/browser/frontend build/full postgresTest
- commit/push

## [이식 종료조건]

정적 조건, adversarial 방어, MOCK 5-domain `run_full`/Handoff, production entrypoint 계약은 충족했다. 다만 실제 서로 다른 최소 3개 Domain의 FULL_E2E `CONTRACT_PASS`와 최종 production `run_full` LIVE smoke는 사용자 검증이 남았다. 현재 실제 확인 기준은 B2B와 Travel 두 Domain이므로 Core 동결 선언 조건에는 아직 1개 Domain이 부족하다.

사용자 검증에서 최소 3개 Domain Handoff 성공, 나머지 명확한 partial/failure, 잘못된 fact 미포함, `run_full` smoke PASS가 확인되면:

- CONCEPT PORTFOLIO V2 CORE: FROZEN
- FINAL STABILIZATION: PASS
- CUTOVER GATE: PASS
- NEXT: PRODUCTION WORKFLOW INTEGRATION

## [현재 이식 가능 여부]

구현과 정적 gate 기준으로는 사용자 최종 LIVE 검증을 시작할 수 있다. 그러나 production Workflow 이식 착수 판정은 `CUTOVER_GATE: PENDING`이다. 사용자 FULL_E2E 결과 전에는 Core를 `FROZEN`으로 선언하지 않는다.

## [수정 파일 목록]

- `ai/app/concept_portfolio_v2/models.py`
- `ai/app/concept_portfolio_v2/providers.py`
- `ai/app/concept_portfolio_v2/engine.py`
- `ai/app/concept_portfolio_v2/fact_consistency.py`
- `ai/app/concept_portfolio_v2/diagnostics/notebook_view.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`
- `ai/tests/concept_portfolio_v2/test_final_stabilization_cutover_gate.py`
- 기존 targeted completion/role semantic 테스트 계약 보정
- 본 결과/진행/사용자 검증 문서

사용자가 작업 전에 생성한 FULL_E2E Notebook 사본, recordings, canonical Notebook output/checkpoint는 삭제·복구·정리하지 않았다.

## [git diff --stat]

Core/test tracked diff는 6 files changed, 330 insertions, 68 deletions이다. 여기에 신규 `fact_consistency.py`, adversarial test, 결과/진행/검증 문서 3개가 추가되었다. Canonical Notebook의 전체 worktree diff는 사용자 기존 FULL_E2E output을 포함해 6,933줄 규모로 보이지만 본 작업은 source 진단 행만 추가했다. 사용자 LIVE Notebook 사본과 recordings는 작업 범위에서 제외했다.
