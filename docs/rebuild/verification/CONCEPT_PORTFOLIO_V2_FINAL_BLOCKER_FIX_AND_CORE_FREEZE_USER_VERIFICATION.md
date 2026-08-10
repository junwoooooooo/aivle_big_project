# Concept Portfolio V2 마지막 Blocker 사용자 LIVE 검증

## 공통 준비

1. `ai/notebooks/concept_portfolio_v2_lab.ipynb`를 연다.
2. Kernel을 재시작해 이전 Scenario 상태를 제거한다.
3. `MODE='LIVE'`, `LIVE_TEST_LEVEL='FULL_E2E'`로 설정한다.
4. Scenario마다 fresh kernel로 실행하고 Candidate별 Legal 표, required inputs, Final Portfolio, 7 Hypothesis, Handoff를 저장한다.

## 1. B2B AI Sales Assistant

1. `LIVE_SCENARIO='B2B_AI_SALES_ASSISTANT'`로 FULL_E2E를 실행한다.
2. 가능하면 fresh kernel 기준 2~3회 반복한다.
3. `C4 BUSINESS_PARTNER` 요청에 같은 C4의 `PERSONAL_DATA`, `PHYSICAL_ACTIVITY`가 추가돼도 전체가 `LEGAL_FACT_DEPENDENCY_BATCH_IDENTITY_MISMATCH`로 실패하지 않아야 한다.
4. 추가 결과가 있었다면 `BATCH_EXTRA_RESULTS_IGNORED`와 `ignoredKeys` 진단이 남고, 요청한 key만 실제 판정에 사용되는지 확인한다.

## 2. Office Equipment Subscription

1. Kernel을 재시작한다.
2. `LIVE_SCENARIO='OFFICE_EQUIPMENT_SUBSCRIPTION'`로 FULL_E2E를 실행한다.
3. C1에 후보 단위 Legal 계약 오류가 나면 C1은 `SYSTEM_FAILURE`로 표시되어야 한다.
4. C2 이후 후보 검토가 계속되어 `Legal Reviewed`가 0으로 끝나지 않아야 한다.
5. 인증·설정·Legal source 전체 장애라면 반대로 global failure로 중단되어야 한다.

## 3. Campus Secondhand

1. Kernel을 재시작한다.
2. `LIVE_SCENARIO='CAMPUS_SECONDHAND'`로 FULL_E2E를 실행한다.
3. “판매 주체 확인 필요”, “판매자 자격 여부 확인 필요” 성격은 `NEEDS_INPUT`과 구체 질문으로 남아야 한다.
4. 구체적인 구조 변경 요구가 없는데 redesign을 반복하거나 Candidate의 다른 사업정보를 바꾸면 실패다.
5. 명시적으로 현재 구조를 제거·전환하고 새 계약/판매 주체를 요구할 때만 redesign이 허용된다.

## 4. 마지막 production 경로 smoke

위 세 Scenario가 시스템 예외 없이 명확한 terminal 상태에 도달하고 하나 이상이 Handoff까지 완주하면, B2B/Travel/Food 중 하나만 `LIVE_TEST_LEVEL='ONE_CLICK'`로 실행한다. production entrypoint와 같은 `run_full()` 결과에서 Final Portfolio, 7 Hypothesis 확정, Handoff `CONTRACT_PASS`를 확인한다.

## 동결 판정

다음을 모두 만족하면 `CONCEPT PORTFOLIO V2 CORE: FROZEN`으로 판정한다.

- safe extra 때문에 전체 실패하지 않음
- 실제 누락/중복/잘못된 ID는 계속 차단
- 후보 하나의 Legal 기술 오류가 다른 후보를 막지 않음
- 공통 Legal 장애는 전체 실패
- 사실 질문과 구조 변경이 구분됨
- 서로 다른 최소 3개 Domain에서 Handoff 성공
- 마지막 LIVE `run_full()` smoke 성공

Food와 Travel은 현재 최신 Handoff 성공 기준선이다. 새 회귀 징후가 없으면 우선 재실행하지 않는다.
