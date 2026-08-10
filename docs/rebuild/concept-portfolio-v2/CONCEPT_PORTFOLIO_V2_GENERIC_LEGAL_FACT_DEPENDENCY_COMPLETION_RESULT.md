# Concept Portfolio V2 Generic Legal Fact Dependency & Targeted Completion 결과

## [STATUS]

- IMPLEMENTATION COMPLETE
- STATIC / TARGETED / MULTI-DOMAIN CONTRACT VERIFICATION COMPLETE
- LIVE BUSINESS ROLE SEMANTIC: ALREADY OBSERVED WORKING
- LIVE ONE-CLICK: ALREADY OBSERVED READY IN MULTIPLE DOMAINS
- LIVE GENERIC LEGAL FACT DEPENDENCY: PENDING USER RETEST
- LIVE FACT COMPLETION COMPLIANCE: PENDING USER RETEST
- LIVE OFFICE EQUIPMENT LEGAL-READY: PENDING USER RETEST
- LIVE MULTI-DOMAIN FULL LEGAL: PARTIALLY OBSERVED, FURTHER RETEST PENDING
- PRODUCTION CUTOVER: NOT STARTED

## [AUTHORITATIVE HEAD]

- Branch: `rebuild/new-pipeline-v1`
- 작업 시작 HEAD: `1e026d5e2a0de7e99ad7a008680b6eeb831dc158`
- commit/push/branch 변경 없음

## [LATEST MULTI DOMAIN LIVE BASELINE]

| Scenario | Legal Ready | Legal ACCEPT | Final |
|---|---:|---:|---:|
| AI_INTERVIEW_COACH | 5 | 4 | 4 |
| WEEKEND_TRIP_PLANNER | 4 | 4 | 4 |
| CAMPUS_SECONDHAND | 3 | 1 | 1 |
| FOOD_PHYSICAL_COMMERCE | 2 | 1 | 1 |
| OFFICE_EQUIPMENT_SUBSCRIPTION | 0 | 0 | 0 / FAILED |

이전 completion 기준선은 attempted 11, Candidate validation PASS 11, completion accepted 0이었다. 실패 분포는 Campus 2, Office 5, Travel 1, Food 3, AI 0이었다.

## [FROZEN COMPONENTS]

Planning, Candidate fidelity, generic business-role semantic, official Legal evidence/citation repair, redesign/replan, hypothesis·delta legal·handoff 계약은 변경하지 않았다. Backend production route cutover도 수행하지 않았다.

## [ROOT CAUSE]

기존 completion Provider가 전체 `ConceptCandidateDraft`를 반환해 요청 필드를 실제로 보완하지 않으면서 관련 없는 필드를 바꿀 수 있었다. LIVE 기록에서 Office/Food의 `personalDataUsage`, Campus의 `partnerRequirements`는 요청 전후 모두 비어 있었고, Travel 역할 보완은 관련 없는 `physicalActivities`까지 변경했다. 또한 dependency 필요성, 사실 충분성, 보완 이행 여부가 하나의 완결성 결과에 섞여 원인별 실패 분리가 불가능했다.

## [LEGAL FACT DEPENDENCY MODEL]

`PERSONAL_DATA`, `PHYSICAL_ACTIVITY`, `BUSINESS_PARTNER`를 독립 dependency로 추가했다. 각 축은 deterministic `REQUIRED / NOT_REQUIRED / AMBIGUOUS`를 먼저 산출하고, AMBIGUOUS만 semantic batch에서 `REQUIRED / NOT_REQUIRED / UNKNOWN`으로 확정한다. 이는 법률 적용 판단이 아니라 Candidate 사업 사실구조 판정이다.

## [PERSONAL DATA DEPENDENCY]

개인 단위 식별·연락·계정·주소·기록의 실제 처리 구조와 명시적 비처리를 구분한다. 일반적인 개인화·추천 표현만으로 REQUIRED를 강제하지 않으며 불명확하면 semantic batch로 보낸다.

## [BUSINESS PARTNER DEPENDENCY]

외부 계약·운영·공급 파트너와 단순 거래 참가자를 구분한다. P2P 판매자·구매자는 별도 사업 파트너로 자동 승격되지 않는다. 고신뢰 `PARTNER_NETWORK / EXPERT_NETWORK`, 직접운영, P2P architecture는 진단 맥락으로 사용하되 Provider가 새 사실을 만들 수 없다.

## [PHYSICAL DEPENDENCY]

배송·방문·설치·회수·현장 등 실제 이행과 순수 디지털 제공을 구분한다. 일반 서비스 문구만으로 물리 활동을 생성하지 않는다.

## [DEPENDENCY SEMANTIC BATCH]

초기 Candidate batch와 completion child batch마다 불명확 dependency를 최대 한 번 묶어 판정한다. 최대 5개 Candidate × 3 dependency = 15개 strict batch이며 입력 순서와 `candidateId / dependencyType` identity를 Gateway에서 검증한다.

## [TARGETED COMPLETION PATCH]

Provider 반환 계약을 전체 Candidate에서 `LegalFactCompletionPatch`로 교체했다. 허용 필드는 역할 4개, 거래·결제, 개인정보, 물리 활동, 파트너, 지역, 채널뿐이며 모두 nullable이다. 요청되지 않은 값은 반드시 null이다. Concept identity 필드는 schema에 존재하지 않는다. System이 non-null allow-list 필드만 부모 draft에 적용하고 기존 normalizer와 LOCK을 다시 적용한다.

## [COMPLETION REQUIREMENT COMPLIANCE]

`LegalFactCompletionRequirement`가 필드, 사유 유형, dependency, 지시문을 구조화한다. completion 이후 다음 세 gate를 독립 적용한다.

1. 전체 Candidate validation PASS
2. 요청된 필드의 실제 변경과 의미 충족에 대한 completion compliance PASS
3. dependency/역할을 다시 판정한 최종 completeness COMPLETE

세 조건을 모두 만족해야 Legal-ready로 인정한다. 명시적 dependency 부재가 재검사에서 `NOT_REQUIRED`로 확정되면 정상 이행으로 인정한다.

## [COMPLETION FAILURE TAXONOMY]

- `LEGAL_FACT_COMPLETION_PROVIDER_NONCOMPLIANT`
- `LEGAL_FACT_COMPLETION_RECHECK_FAILED`
- `LEGAL_FACT_DEPENDENCY_UNRESOLVED`
- `LEGAL_FACT_COMPLETION_CANDIDATE_INVALID`
- `LEGAL_FACT_COMPLETION_SCOPE_VIOLATION`

기존 `LEGAL_FACT_COMPLETION_EXHAUSTED`는 상위 호환 umbrella metric으로 유지한다.

## [PRE LEGAL EXCLUSIONS]

실제 reject는 `preLegalExclusions`에 남긴다. 각 항목은 root `reasonCode`, dependency decisions, structured requirements, patch changed fields, compliance, recheck status, recovery resolution을 포함한다.

## [REQUIRED INPUT SEPARATION]

Provider 비준수, dependency 미해결, Candidate validation 실패, patch scope 위반은 사용자 입력 요구가 아니다. 따라서 `requiredInputs`와 `unresolvedCandidates`를 오염시키지 않고 Candidate-scoped pre-Legal exclusion으로 처리한다. `requiredInputs`는 공식 Legal 결과가 실제 사용자 사실 확인을 요구할 때만 사용한다.

## [LEGAL ROUTE OBSERVABILITY]

초기 Legal review, recovery review, 전체 review event를 분리 집계한다. `legalResolutions`는 초기 Candidate별 initial route, recovery action/child, final route, terminal resolution(`ACCEPTED / NEEDS_INPUT / EXCLUDED_LEGAL / SYSTEM_FAILURE`)을 제공한다.

## [NOTEBOOK METRICS]

Notebook에 dependency semantic batch calls, completion compliance 결과, Provider noncompliance, recheck failure, 초기/recovery/전체 Legal review event, 최종 lineage resolution 표를 노출했다. 기존 사용자 LIVE 출력은 보존하고 canonical Notebook source만 확장했다.

## [MULTI DOMAIN REGRESSION]

Food, B2B SaaS, local service marketplace, education, travel, secondhand marketplace, AI productivity 7개 fixture를 MOCK canonical engine으로 검증했다. 모든 scenario가 `READY_FULL` 또는 `READY_LIMITED`, Legal-ready 1개 이상, final lineage `ACCEPTED`, 사용자 required input 없음으로 통과했다.

## [TEST RESULTS]

- `python -m compileall -q ai/app`: PASS
- strict schema preflight: PASS (`LegalFactDependencySemanticBatch`, `LegalFactCompletionPatch` 포함)
- `ai/.venv/Scripts/python.exe -m pytest ai/tests/concept_portfolio_v2 -q`: **212 passed**
- Notebook JSON parse 및 47개 code cell `ast.parse`: PASS
- production entrypoint parity/smoke: 위 targeted suite에서 PASS
- `git diff --check`: PASS (줄바꿈 변환 warning만 존재)

## [NOT RUN]

- AI Provider LIVE
- MOLEG/공식 Legal LIVE
- Docker/browser smoke
- frontend production build
- full repository regression, full postgresTest
- production route cutover
- commit/push

## [USER LIVE RETEST ORDER]

1. Canonical Notebook kernel restart
2. `OFFICE_EQUIPMENT_SUBSCRIPTION`, `LIVE_TEST_LEVEL='ONE_CLICK'` 실행
3. dependency decisions와 structured completion requirements 확인
4. completion patch changed fields와 compliance PASS 확인
5. Legal Ready가 1개 이상인지 확인
6. `CAMPUS_SECONDHAND` 실행 후 P2P 참가자가 business partner로 자동 판정되지 않는지 확인
7. `FOOD_PHYSICAL_COMMERCE` 실행 후 실제 개인정보/물리/파트너 dependency만 보완되는지 확인
8. `WEEKEND_TRIP_PLANNER` 실행 후 역할 보완이 관련 없는 물리 활동을 바꾸지 않는지 확인
9. `AI_INTERVIEW_COACH` 실행 후 불필요 completion 증가가 없는지 확인
10. `B2B_AI_SALES_ASSISTANT`, `LOCAL_PETCARE_MARKETPLACE` 포함 7개 scenario 순차 실행
11. 각 결과의 pre-Legal exclusions, required inputs, Legal resolution 표 확인
12. 사용자 승인 후에만 전체 공식 Legal 재시험

## [FILES MODIFIED]

- `ai/app/concept_portfolio_v2/models.py`
- `ai/app/concept_portfolio_v2/legal_fact_completeness.py`
- `ai/app/concept_portfolio_v2/providers.py`
- `ai/app/concept_portfolio_v2/engine.py`
- `ai/app/concept_portfolio_v2/schema_preflight.py`
- `ai/app/concept_portfolio_v2/diagnostics/notebook_view.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`
- `ai/tests/concept_portfolio_v2/test_generic_legal_fact_dependency_completion.py`
- `ai/tests/concept_portfolio_v2/test_generic_role_semantic_recovery_round2.py`
- 본 결과/진행/사용자 검증 문서

사용자가 이미 변경한 Notebook output/checkpoint, 삭제된 scenario Notebook, recordings는 수정·복구하지 않았다.

## [GIT DIFF --STAT]

Task 범위의 tracked diff는 8 files changed, 984 insertions, 2,895 deletions이다. Notebook 수치에는 작업 시작 전부터 존재한 사용자 LIVE output 정리 차이가 포함된다. 여기에 신규 test 1개와 결과/진행/검증 문서 3개가 untracked 파일로 추가되었다. commit/push는 수행하지 않았다.
