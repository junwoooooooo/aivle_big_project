# Concept Portfolio V2 최종 FULL_E2E 사용자 검증

## 공통 준비

1. [Canonical Notebook](../../../ai/notebooks/concept_portfolio_v2_lab.ipynb)을 연다.
2. Kernel을 restart한다.
3. `MODE='LIVE'`, `LIVE_TEST_LEVEL='FULL_E2E'`, `MAX_CONCEPTS=5`를 설정한다.
4. 단계별 Plan, Candidate, 사업정보 검사, Legal, recovery, final Portfolio, Hypothesis, Handoff 출력을 보존한다.

## 실행 순서

1. `FOOD_PHYSICAL_COMMERCE`
2. `OFFICE_EQUIPMENT_SUBSCRIPTION`
3. `WEEKEND_TRIP_PLANNER`
4. `B2B_AI_SALES_ASSISTANT`
5. `CAMPUS_SECONDHAND`

FULL_E2E가 안정되면 B2B 또는 Travel 한 개를 `LIVE_TEST_LEVEL='ONE_CLICK'`로 마지막 smoke 실행한다.

## Scenario별 확인

### FOOD

- dependency 결과 순서가 달라도 batch identity mismatch로 중단되지 않는다.
- 누락·중복·추가·잘못된 ID만 실패한다.

### OFFICE

- Completion 요청 field만 `patchChangedFields`에 나타난다.
- 요청 field가 실제 변경되고 compliance PASS, completeness COMPLETE가 된다.
- Concept identity, 가격, 수익모델, 사용자 LOCK이 보존된다.

### TRAVEL

- 온라인 일정 구성·예약 연결 Concept에 근거 없는 배송·현장 방문·설치가 최종 Candidate에 없다.
- `factConsistency`에서 INVALID가 나오면 `-FC1` 1회 repair 후 재검사되거나 해당 Candidate만 제외된다.

### B2B / CAMPUS

- 기존 B2B Handoff `CONTRACT_PASS`가 유지된다.
- Campus의 P2P 참가자가 외부 사업 파트너로 자동 승격되지 않는다.
- 한 Candidate 실패가 다른 Candidate의 Legal 진행을 막지 않는다.

## 공통 판정

- 시스템 예외로 전체 실행이 죽지 않는다.
- 유효 Portfolio가 1개 이상이면 READY_FULL 또는 READY_LIMITED다.
- `requiredInputs`에는 실제 사용자 확인만 있고 Provider 실패는 `preLegalExclusions`에 있다.
- 각 Legal Candidate의 `legalResolutions` terminal 상태가 명확하다.
- 7 Hypothesis에 placeholder가 없고 사용자 확정 후 Handoff를 만들 수 있다.
- 잘못된 사업 fact가 Legal/Handoff Snapshot에 없다.

## CUTOVER_GATE PASS 조건

1. 서로 다른 최소 3개 Domain에서 FULL_E2E Handoff 성공
2. 나머지도 시스템 예외 없이 명확한 실패 또는 부분 성공
3. 명백한 잘못된 사업 fact가 Handoff에 없음
4. 마지막 ONE_CLICK production `run_full` smoke PASS

위 네 조건을 모두 확인한 뒤에만 Concept Portfolio V2 Core를 FROZEN으로 판정하고 Production Workflow 이식으로 이동한다.
