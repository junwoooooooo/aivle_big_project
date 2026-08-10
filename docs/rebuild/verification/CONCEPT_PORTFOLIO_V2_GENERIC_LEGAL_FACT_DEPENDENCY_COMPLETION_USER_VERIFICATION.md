# Concept Portfolio V2 Generic Legal Fact Dependency 사용자 검증

## 준비

1. `ai/notebooks/concept_portfolio_v2_lab.ipynb`를 연다.
2. Kernel을 restart한다.
3. `MODE='LIVE'`, `LIVE_TEST_LEVEL='ONE_CLICK'`, `MAX_CONCEPTS=5`를 확인한다.
4. Provider/MOLEG 자격정보가 기존 방식으로 설정되어 있는지 확인한다.

## 우선 검증: Office Equipment

1. `LIVE_SCENARIO='OFFICE_EQUIPMENT_SUBSCRIPTION'`으로 실행한다.
2. `dependencySemanticBatchCalls`가 batch 단위인지 확인한다.
3. `personalDataUsage`가 REQUIRED이면 실제 항목·목적이 patch에 채워지는지 확인한다.
4. `patchChangedFields`가 structured requirement allow-list 밖을 포함하지 않는지 확인한다.
5. `completionCompliance.status == 'PASS'`인지 확인한다.
6. 재검사 completeness가 `COMPLETE`이며 Legal Ready가 1개 이상인지 확인한다.

## 도메인별 검증

1. `CAMPUS_SECONDHAND`: 학생 판매자/구매자만 있다는 이유로 BUSINESS_PARTNER REQUIRED가 되지 않아야 한다.
2. `FOOD_PHYSICAL_COMMERCE`: 실제 개인정보·물리 이행·외부 파트너만 REQUIRED 및 보완되어야 한다.
3. `WEEKEND_TRIP_PLANNER`: 역할 보완이 관련 없는 `physicalActivities`를 변경하지 않아야 한다.
4. `AI_INTERVIEW_COACH`: 순수 디지털 구조에서 불필요한 물리/파트너 completion이 생기지 않아야 한다.
5. `B2B_AI_SALES_ASSISTANT`, `LOCAL_PETCARE_MARKETPLACE`도 동일 gate로 확인한다.

## 실패 판독

- Provider가 요청 필드를 바꾸지 않음: `LEGAL_FACT_COMPLETION_PROVIDER_NONCOMPLIANT`
- patch 후 COMPLETE가 아님: `LEGAL_FACT_COMPLETION_RECHECK_FAILED`
- dependency가 UNKNOWN으로 남음: `LEGAL_FACT_DEPENDENCY_UNRESOLVED`
- child 전체 validation 실패: `LEGAL_FACT_COMPLETION_CANDIDATE_INVALID`
- 요청 밖 또는 LOCK 변경: `LEGAL_FACT_COMPLETION_SCOPE_VIOLATION`

위 실패는 `requiredInputs`가 아니라 `preLegalExclusions`에 있어야 한다. `requiredInputs`에는 공식 Legal이 요청한 실제 사용자 확인만 있어야 한다.

## 최종 관찰 항목

- `Legal initial reviewed`
- `Legal recovery reviewed`
- `Total legal review events`
- `Legal ACCEPT`
- `Final portfolio`
- `show_legal_resolutions`의 initial route, recovery action, final route, final resolution

## 중단 조건

동일 schema/identity 실패가 여러 Candidate에서 반복되거나 global Legal dependency 장애가 나오면 해당 실행을 중단하고 trace, Provider failure, pre-Legal exclusions를 함께 보존한다. Codex가 LIVE를 대신 반복 실행하지 않는다.
