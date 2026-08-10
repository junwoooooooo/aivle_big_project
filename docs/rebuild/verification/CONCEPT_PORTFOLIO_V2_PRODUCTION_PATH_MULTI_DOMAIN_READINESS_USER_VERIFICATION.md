# Concept Portfolio V2 Production Path / Multi-Domain Readiness — 사용자 검증

정본: `ai/notebooks/concept_portfolio_v2_lab.ipynb`

## 공통 준비

1. Kernel Restart 후 환경변수 상태와 Schema Preflight PASS를 확인한다.
2. `MODE='LIVE'`를 확인한다.
3. `LIVE_SCENARIO`와 `LIVE_TEST_LEVEL`은 한 번에 하나씩만 선택한다.
4. FULL_E2E와 ONE_CLICK을 동시에 실행하지 않는다.

## 1차 — Food One-click root-cause 재검증

1. `LIVE_SCENARIO='FOOD_PHYSICAL_COMMERCE'`로 둔다.
2. `LIVE_TEST_LEVEL='ONE_CLICK'`, `RUN_ONE_CLICK_LIVE=True`로 둔다.
3. One-click cell만 fresh Kernel 흐름에서 실행한다.
4. 기대값은 `READY_FULL` 또는 `READY_LIMITED`, `producedConceptCount >= 1`, `downstreamReadiness=PENDING_HYPOTHESIS_CONFIRMATION`이다.
5. candidate-scoped NEEDS_INPUT이 있으면 다른 ACCEPT 후보가 Final Portfolio에 남는지 확인한다.
6. 실패하면 자동 출력된 run summary, failureDiagnostics, provider failure, 최근 trace 20개, requiredInputs, unresolvedCandidates 전체를 보존한다.

## 2차 — B2B SaaS

1. `LIVE_SCENARIO='B2B_AI_SALES_ASSISTANT'`, `LIVE_TEST_LEVEL='CORE'`로 Plan/Candidate까지 실행한다.
2. 물리 활동이나 marketplace 역할이 근거 없이 핵심 구조로 강제되지 않는지 확인한다.
3. `LEGAL_C1`로 C1 Legal Fact와 Full Legal route를 확인한다.
4. 문제가 없을 때만 `FULL_E2E`로 전체 Legal, Final Portfolio, 7 Hypothesis, Handoff를 실행한다.
5. targetRegion `일본/미국 캘리포니아`, price `$19/month/고객별 견적`, channels `Slack 앱 디렉터리`가 VALID인지 확인한다.

## 3차 — Local Marketplace

1. `LIVE_SCENARIO='LOCAL_PETCARE_MARKETPLACE'`로 CORE → LEGAL_C1 → FULL_E2E를 순차 실행한다.
2. seller/provider/intermediary 역할과 예약·결제·정산 주체가 구분되는지 확인한다.
3. `intermediaryRole='배송 담당'` 같은 값은 MISMATCH/Fact Completion 대상이어야 한다.
4. candidate-scoped unknown facts가 있으면 구체적 질문과 가능한 사용자 행동이 표에 표시되는지 확인한다.

## 4차 이후

1. `AI_INTERVIEW_COACH`는 ONE_CLICK으로 실행한다.
2. `CAMPUS_SECONDHAND`, `OFFICE_EQUIPMENT_SUBSCRIPTION`, `WEEKEND_TRIP_PLANNER`를 하나씩 실행한다.
3. exact title/price가 아니라 Idea fidelity, role coherence, legal terminal resolution, hypothesis readiness, handoff contract를 확인한다.
4. 모든 scenario를 한 번에 LIVE 실행하지 않는다.

## 성공 판정

- 후보 1건 오류가 나머지 ACCEPT 후보를 제거하지 않는다.
- global configuration/dependency 오류만 전체 FAILED가 된다.
- 실패 시 위치·코드·entity·provider detail·최근 trace를 한 cell에서 확인할 수 있다.
- candidate NEEDS_INPUT은 질문/action을 제공하고 나머지 후보는 READY_LIMITED로 진행한다.
- `중개하지 않음`과 `직접 판매하지 않음`이 positive role로 오인되지 않는다.
- 7 Hypothesis placeholder는 차단되고 국제 지역·통화·채널 값은 유효하게 처리된다.
- 7개 hypothesis accepted/semantic valid, Legal ACCEPT, Delta Legal 완료 후에만 `CONTRACT_PASS`다.
