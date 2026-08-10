# Concept Portfolio V2 Generic Role Semantic Multi-Domain Recovery 사용자 검증

## 준비

1. branch가 `rebuild/new-pipeline-v1`인지 확인한다.
2. `ai/notebooks/concept_portfolio_v2_lab.ipynb`를 연다.
3. fresh kernel로 재시작한다.
4. `MODE='LIVE'`, `LIVE_TEST_LEVEL='ONE_CLICK'`, `RUN_ONE_CLICK_LIVE=True`를 확인한다.
5. API key와 Legal registry 환경값은 화면에 출력하지 않는다.

## 시나리오 순서

1. `AI_INTERVIEW_COACH`
2. `B2B_AI_SALES_ASSISTANT`
3. `FOOD_PHYSICAL_COMMERCE`
4. `LOCAL_PETCARE_MARKETPLACE`

시나리오를 바꿀 때마다 fresh kernel에서 전체 셀을 한 번만 실행한다.

## 실행 레벨 확인

- `ONE_CLICK`에서는 staged `engine`의 Idea/Plan/Candidate/Legal 호출이 없어야 한다.
- one-click용 새 Engine의 `run_full`만 실행되어야 한다.
- `CORE`는 Candidate validation까지만 실행되고 Legal 호출이 없어야 한다.
- `LEGAL_C1`은 Legal-ready 준비와 C1 Legal까지만 실행되어야 한다.
- `FULL_E2E`에서만 remaining Legal, Hypothesis, delta/handoff staged 경로를 수행한다.

## 역할 fallback 확인

- trace에 `BUSINESS_ROLE_SEMANTIC_BATCH`가 phase별 최대 1회 나타나는지 확인한다.
- 각 역할 detail의 `deterministicStatus`, `semanticUsed`, `semanticStatus`, `finalStatus`, `safeReason`을 확인한다.
- deterministic `AMBIGUOUS`가 semantic `MATCH` 또는 `EXPLICIT_ABSENCE`면 Fact Completion을 호출하지 않아야 한다.
- semantic `UNKNOWN/MISMATCH`만 실제 보완 대상으로 내려가야 한다.
- completion child 여러 개가 있어도 child semantic recheck는 Candidate별이 아니라 batch 1회여야 한다.

## Legal-ready 및 제외 확인

- `Candidate generated`, `Candidate valid initially`, `Candidate regenerated`, `Candidate recovered`를 구분한다.
- `Fact completion attempted/validated/accepted`를 구분한다.
- `Legal ready`, `Legal reviewed`, `Legal ACCEPT`, `Final portfolio`를 구분한다.
- 제외 후보는 `preLegalExclusions`에 표시되어야 한다.
- `requiredInputs`와 `unresolvedCandidates`에는 실제 사용자 답변이 필요한 사실만 있어야 한다.
- Legal-ready가 0이면 failedStage `LEGAL_RECOVERING`, failureCode `NO_LEGAL_READY_CANDIDATES`인지 확인한다.

## 디지털/물리 확인

- `AI_INTERVIEW_COACH`에서 AI 분석, 모의면접 피드백 같은 디지털 문구만으로 `regulatedPhysicalActivity=True`가 되지 않아야 한다.
- `FOOD_PHYSICAL_COMMERCE`의 포장/배송과 `LOCAL_PETCARE_MARKETPLACE`의 방문 돌봄은 물리 이행으로 유지되어야 한다.
- completion이 순수 디지털 Candidate에 generic physicalActivities 문구를 새로 만들지 않아야 한다.

## Hypothesis/Handoff 확인

- 선택 Concept 또는 Hypothesis가 없으면 세 readiness 축이 모두 false이고 reason은 `NO_SELECTED_CONCEPT_OR_HYPOTHESES`여야 한다.
- Concept가 선택되면 7 Hypothesis를 확인하고 사용자 확정 후에만 handoff를 생성한다.
- delta legal이 필요한 수정은 delta review 통과 전 handoff가 준비되면 안 된다.

## 중단 조건

- 새 role semantic 응답이 strict schema에서 실패한다.
- 동일 phase에서 Candidate 수만큼 role semantic 외부 호출이 발생한다.
- pre-Legal 제외가 사용자 requiredInputs로 섞인다.
- Legal-ready 0이 다시 `UNCLASSIFIED_SYSTEM_FAILURE`로 표시된다.
- `ONE_CLICK` 전에 staged Provider 호출이 발생한다.

위 조건 중 하나라도 발생하면 결과 Notebook을 별도 사본으로 저장하고, failure diagnostics와 마지막 trace 20개 및 새 recording ID를 함께 전달한다.
