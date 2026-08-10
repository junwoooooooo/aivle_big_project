# Concept Portfolio V2 — Production Path / Multi-Domain Readiness 결과

## [STATUS]

IMPLEMENTATION COMPLETE  
STATIC / TARGETED / MULTI-DOMAIN CONTRACT VERIFICATION COMPLETE  
LIVE STAGED E2E: ALREADY OBSERVED WORKING  
LIVE STRICT HYPOTHESIS HANDOFF: ALREADY OBSERVED WORKING  
LIVE ONE-CLICK `run_full`: PENDING USER RETEST AFTER ROOT-CAUSE FIX  
LIVE MULTI-DOMAIN: PENDING USER RETEST  
PRODUCTION ENTRYPOINT: CORE PARITY VERIFIED, REAL PRODUCTION INTEGRATION NOT STARTED

## [AUTHORITATIVE HEAD]

- Branch: `rebuild/new-pipeline-v1`
- 작업 시작 HEAD: `076c67958f53cc762f9656237c019272f199f43f`
- branch 전환, commit, push를 수행하지 않았다.

## [LATEST USER LIVE BASELINE]

- staged LIVE: Idea Brief부터 Candidate, Full Legal, Final Portfolio, 7 Hypothesis, Handoff까지 성공했다.
- Candidate 5/5 PASS, Legal Fact Completeness 5/5 COMPLETE였다.
- C1/C2/C3/C5 Legal ACCEPT, C4 candidate-scoped NEEDS_INPUT, Final Portfolio 4였다.
- 7 Hypothesis semantic VALID, Market/Marketing `CONTRACT_PASS`를 관찰했다.
- 동일 Notebook의 기존 One-click 출력은 `FAILED / finalPortfolio=0 / downstream=INVALID`만 남겨 하위 실패 정보가 소실됐다.

## [FROZEN WORKING PATH]

staged 성공 경로의 planning, candidate recovery/fidelity, Legal evidence/citation hardening, redesign/replan, 7 Hypothesis, Delta Legal, downstream snapshot 계약은 변경하지 않았다. 이번 변경은 V2 Core와 정본 Notebook, import entrypoint의 orchestration·diagnostic 경계에 한정했다. 현행 Backend active Concept Factory route는 교체하지 않았다.

## [ONE CLICK FAILURE ROOT CAUSE]

기존 출력만으로 실제 Provider 하위 오류 코드는 복원할 수 없으므로 임의 원인을 만들지 않았다. 코드와 Notebook에서 확인된 구조적 원인은 다음 세 가지다.

1. One-click cell이 staged 실행에 사용한 동일 `engine`/`gateway` instance를 다시 사용했다.
2. `review_legal()`의 후보 loop에 예외 경계가 없어 한 후보의 `ProviderFailure`가 최상위 `run_full()` catch로 전파됐다.
3. 실패 `_terminal()`이 `RunSummary`와 failure detail을 생성하지 않아 최종 출력이 `FAILED / 0 / INVALID`로 축약됐다.

Notebook은 fresh Engine을 사용하도록 수정했고 `_reset()`은 engine state뿐 아니라 `gateway.last_failure`와 usage를 초기화한다. 후보별 결과 계약 실패는 해당 후보의 `SYSTEM_FAILURE`로 격리한다. 정확한 과거 Provider 하위 코드는 기록되지 않았으므로 새 diagnostics를 통한 사용자 재실행에서 확인한다.

## [RUN_FULL PARITY]

- staged와 `run_full()` 모두 `prepare_portfolio_plans → prepare_candidate_portfolio → prepare_legal_candidates → review_legal → resolve_legal → hypothesis contract` 순서의 같은 public Core method를 사용한다.
- deterministic provider에서 final candidate IDs/lineages, Legal routes, required input, portfolio 상태와 hypothesis pending 상태가 동일함을 테스트했다.
- `auto_confirm_hypotheses=False`는 정상 Portfolio를 실패시키지 않고 `PENDING_HYPOTHESIS_CONFIRMATION`을 반환한다.
- fresh-engine production entrypoint MOCK smoke는 `READY_FULL / 5 / PENDING_HYPOTHESIS_CONFIRMATION`을 반환했다.

## [FAILURE DIAGNOSTICS]

`ConceptPortfolioResult.failureDiagnostics` optional field와 실패 terminal `RunSummary`를 추가했다. 실패 결과는 다음을 보존한다.

- failed/first failed stage, failure code/reason/entity
- provider failure safe diagnostics
- 마지막 성공 stage와 최근 trace 20개
- planned, selected plans, generated/accepted candidates, reviewed/accepted Legal, final portfolio
- provider usage, retries, duration

Notebook One-click은 실패 시 summary, run failure, provider failure, 최근 trace 20개, requiredInputs와 unresolvedCandidates를 자동 표시한다.

## [CANDIDATE FAILURE ISOLATION]

- 후보 1건의 Legal result schema/contract 실패는 `LegalReview(route=SYSTEM_FAILURE, sourceStatus=CANDIDATE_SYSTEM_FAILURE)`로 terminalize한다.
- 나머지 후보는 계속 검토하며 4 ACCEPT + 1 SYSTEM_FAILURE는 `READY_LIMITED 4`가 된다.
- redesign/replan child의 candidate-local Provider contract 실패도 해당 lineage만 종료한다.
- 동일 `failure.code + reason`이 2개 후보에서 반복되면 공통 계약 장애로 승격해 전체 run을 실패시킨다.
- silent drop은 없다.

## [GLOBAL FAILURE POLICY]

AI/Legal configuration, authentication·dependency outage, registry/input contract와 공통 source 장애는 즉시 global failure다. 이 경우 `runStatus=FAILED`와 failure diagnostics를 반환한다. authoritative 외부 상태명은 기존 `FAILED`를 유지하며 별도 `FAILED_SYSTEM` enum을 만들지 않았다.

Failure taxonomy는 다음과 같다.

| 분류 | terminal 표현 | Portfolio 영향 |
|---|---|---|
| Candidate Business Failure | validation/rejection | 해당 후보만 제외 |
| Candidate Legal Failure | REPLAN/REDESIGN/NEEDS_INPUT | 해당 lineage 복구 또는 후보 보류 |
| Candidate System Failure | SYSTEM_FAILURE | 해당 후보만 제외, partial Portfolio 허용 |
| Global System Failure | FAILED + failureDiagnostics | 전체 run 종료 |
| Needs Input | GLOBAL 또는 CANDIDATE scope | GLOBAL만 전체 NEEDS_INPUT |

## [LEGAL NEEDS INPUT UX]

`CurrentLegalAdapter`가 Legal Domain Result의 `unknownFacts`를 `LegalReview`에 보존한다. candidate-scoped NEEDS_INPUT은 candidateId, scope, unknownFacts, safeSummary, reason, possibleUserAction, currentValue, requiredLegalChange를 반환한다. action이 없으면 unknown facts를 그대로 연결한 결정론적 안내를 만들며 새 법률 사실을 생성하지 않는다. 4 ACCEPT + C4 NEEDS_INPUT은 `READY_LIMITED 4`로 진행한다.

## [LEGAL PRECHECK NEGATION]

`"판매" in sellerRole`, `"중개" in intermediaryRole` 검사를 제거했다. 공통 `classify_fact_presence()`와 역할 의미 판정을 재사용한다.

- `중개하지 않음` → intermediary false
- `플랫폼은 직접 판매하지 않음` → directSeller false
- `제3자 거래를 중개` → intermediary true

## [BUSINESS ROLE SEMANTIC CONSISTENCY]

Fact presence와 role semantic correctness를 분리했다. 각 역할은 `MATCH / EXPLICIT_ABSENCE / MISMATCH / AMBIGUOUS` 진단을 가진다. 명백한 mismatch만 generic한 소수 책임 표현으로 판정하고, 불명확하거나 mismatch인 역할은 Legal Fact Completion 대상으로 보낸다.

- `intermediaryRole="배송 담당"` → MISMATCH
- `sellerRole="앱 화면 운영"` → MISMATCH
- `providerRole="서비스를 직접 제공하지 않고 제휴 전문가가 제공"` → MATCH

## [HYPOTHESIS GENERIC SEMANTIC VALIDATION]

placeholder detector와 confirm-all 안전성은 유지했다. marker가 없다고 즉시 INVALID로 만들지 않고 `AMBIGUOUS`로 분리하며, 필요한 text hypothesis를 batch 1회로 `VALID / INVALID` 판단한다. strict batch는 값을 생성·수정하지 않는다.

다음 targeted regression이 VALID다: `일본`, `미국 캘리포니아`, `$19/month`, `고객별 견적`, `Slack 앱 디렉터리`. `미정`, `미제공`, `명시되지 않음`, `검증 필요`, `TBD`, `추후 결정`은 계속 UNRESOLVED다. Downstream second gate는 placeholder/invalid를 독립 차단하며 semantic batch로 검증된 ambiguous 값만 허용한다.

## [ARCHITECTURE QUALITY]

대규모 taxonomy 변경이나 architecture hard gate를 추가하지 않았다. 기존 system-owned batch classifier와 Candidate의 platform/provider/seller/intermediary/transaction/payment/partner/operating/revenue/channel 입력을 유지했다. Scenario tests는 descriptor family와 terminal 구조를 검사하며 OTHER만으로 후보를 거절하지 않는다.

## [MULTI DOMAIN TEST MATRIX]

| Scenario | Domain | Expected structural features | Tests passed | LIVE status |
|---|---|---|---|---|
| FOOD_PHYSICAL_COMMERCE | Food physical commerce | 물리 이행, 직접/파트너 역할 구분 | PASS | PENDING USER TEST |
| B2B_AI_SALES_ASSISTANT | B2B AI SaaS | 디지털 SaaS, 비마켓플레이스 | PASS | PENDING USER TEST |
| LOCAL_PETCARE_MARKETPLACE | Local marketplace | 중개, partner provider, 예약/결제 | PASS | PENDING USER TEST |
| AI_INTERVIEW_COACH | Digital education | 디지털 제공, 불필요 자격 비강제 | PASS | PENDING USER TEST |
| CAMPUS_SECONDHAND | Secondhand marketplace | 중개, 지역 거래, pickup | PASS | PENDING USER TEST |
| OFFICE_EQUIPMENT_SUBSCRIPTION | B2B physical subscription | 직접 운영, 구독, 물리 이행 | PASS | PENDING USER TEST |
| WEEKEND_TRIP_PLANNER | Travel planning | 계획 역할과 booking 중개 구분 | PASS | PENDING USER TEST |

Scenario catalog는 input fixture일 뿐이며 Core에 scenario/domain switch를 추가하지 않았다.

## [LOCK PROFILE TESTS]

- EXPLORE: optional LOCK 없음
- REFINE: targetRegion + channels LOCK
- AS_IS: targetRegion + revenueModel + price + channels LOCK

Provider가 다른 값을 제안하는 fixture에서도 최종 Candidate의 LOCK 값과 authority가 유지됨을 검증했다.

## [PRODUCTION ENTRYPOINT PARITY]

`ai/app/tasks/concept_portfolio_v2/service.py`는 Notebook과 동일한 `ConceptPortfolioEngine.run_full()`을 호출한다. injected provider로 READY_FULL, READY_LIMITED, PENDING_HYPOTHESIS_CONFIRMATION, global FAILED 계약을 검증했다. 실제 Backend route cutover와 production DB/job 통합은 시작하지 않았다.

## [TEST RESULTS]

- `python -m compileall -q ai/app` PASS
- Concept Portfolio V2 전체 targeted suite: 193 PASS
- 공유 Legal evidence: 26 PASS
- 최종 중복 없는 결합 실행: 219 PASS
- One-click parity / failure isolation / NEEDS_INPUT / negation / role semantic / generic hypothesis / 7-domain / LOCK / production entrypoint 포함
- production entrypoint injected MOCK smoke: READY_FULL, 5, PENDING_HYPOTHESIS_CONFIRMATION
- Notebook JSON parse PASS
- Notebook code cell 47개 compile PASS
- `git diff --check` PASS

## [NOT RUN]

- AI Provider LIVE
- MOLEG LIVE
- 전체 PostgreSQL/Docker/browser/frontend production build
- 실제 Backend route 교체 또는 production migration

## [USER LIVE TEST PLAN]

1. `FOOD_PHYSICAL_COMMERCE` + `ONE_CLICK`로 기존 실패를 먼저 재검증한다.
2. 실패하면 같은 cell에서 자동 출력되는 failedStage/failureCode/providerFailure/최근 trace/requiredInputs를 보존한다.
3. `B2B_AI_SALES_ASSISTANT`: CORE → LEGAL_C1 → FULL_E2E 순서로 하나씩 실행한다.
4. `LOCAL_PETCARE_MARKETPLACE`: CORE → LEGAL_C1 → FULL_E2E 순서로 실행한다.
5. `AI_INTERVIEW_COACH`: ONE_CLICK을 실행한다.
6. CAMPUS_SECONDHAND → OFFICE_EQUIPMENT_SUBSCRIPTION → WEEKEND_TRIP_PLANNER를 순차 검증한다.
7. 모든 scenario를 한 번에 LIVE 실행하지 않는다.

## [FILES MODIFIED]

- V2 models/engine/adapters/providers/schema preflight
- Legal fact completeness와 hypothesis/language policy
- Notebook diagnostics와 정본 Notebook source cell
- 7-domain LIVE scenario fixture와 production-path targeted tests
- 본 결과, progress, 사용자 검증 문서
- 사용자 checkpoint와 recordings는 수정·삭제하지 않았다.

## [GIT DIFF --STAT]

최종 stat은 구현 파일, 정본 Notebook source 변경, 신규 fixture/tests/docs를 포함한다. 기존 사용자-owned Notebook outputs/checkpoint/recordings는 working tree에 그대로 보존되어 전체 stat에 함께 나타날 수 있다.
