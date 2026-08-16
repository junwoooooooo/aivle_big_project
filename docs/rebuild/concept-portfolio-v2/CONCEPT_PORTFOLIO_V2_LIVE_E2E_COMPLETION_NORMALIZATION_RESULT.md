# Concept Portfolio V2 — LIVE E2E Completion Normalization 결과

## [STATUS]

- IMPLEMENTATION COMPLETE
- STATIC/MOCK/TARGETED VERIFICATION COMPLETE
- LIVE PLAN ACCEPTANCE: ALREADY OBSERVED BY USER
- LIVE CANDIDATE ACCEPTANCE: ALREADY OBSERVED BY USER
- LIVE FULL LEGAL ACCEPTANCE: PENDING USER RETEST
- LIVE FINAL PORTFOLIO: PENDING USER RETEST
- LIVE DOWNSTREAM CONTRACT_PASS: PENDING USER RETEST
- PRODUCTION INTEGRATION: NOT STARTED

## [AUTHORITATIVE BRANCH / HEAD]

- Branch: `rebuild/new-pipeline-v1`
- 기준 HEAD: `64a2c0e6fa527e713157c5b4fa8d6f5b2ed7c5b2`
- commit/push/branch 변경 없음

## [UPLOADED LIVE NOTEBOOK EVIDENCE]

- 사용자 LIVE Notebook에서 strict schema preflight, Idea Safety, Seed Analysis, Plan 5개 생성·검증, Candidate 5개 생성·검증, structural legal precheck까지 통과했다.
- 실제 Plan 예시는 `Smart Meal Kit Subscription`, `Leftover Recipe Generator`, `Eco-Friendly Meal Prep Service`, `Personalized Grocery Box`, `Meal Planning Assistant`였고 사용자-facing 설명은 대부분 영어였다.
- 실제 Candidate의 `targetRegion`에 governance state인 `OPEN`이 사업값으로 들어갔다.
- Plan 10쌍과 Candidate 10쌍이 모두 `DISTINCT / LEVEL_2 / semanticJudgeUsed=False`로 반복됐다.
- 첫 Full Legal C1은 `EVIDENCE_REFERENCE_INVALID`로 중단됐다.
- 실행 증거가 든 canonical Notebook과 checkpoint의 작업 전 SHA-256은 모두 `6f09b0fb9c5c2c97cea87620c3603edb5c5d71feca749d2d5c99dcd633fe18c4`였다.
- checkpoint와 `ai/recordings/` 6개는 사용자 증거로 수정하지 않았다. 기존 녹화는 버전 필드가 없어 `REPLAY_PARTIAL` 대상이다.

## [WHAT NOW WORKS]

- 한국어 content 검증과 1회 targeted language correction
- Idea Brief 1회 파생 결과의 Safety·Interpretation·Readiness 재사용
- Intent/ExplorationBreadth 및 실제 Plan content/LOCK 검증
- actual Candidate 필드 기반 mechanics 재분류·fidelity·distinctness
- reserve 부족 상태 공개 및 on-demand replacement replan
- 동적 Legal evidence reference schema, citation repair 1회, 안전 diagnostics
- candidate 범위 입력 대기 시 `READY_LIMITED`
- 버전 고정 REPLAY와 Idea Brief replay
- 수동 7 hypothesis 및 실제 Delta Legal 결과 기반 승인
- Market/Marketing `CONTRACT_PASS` handoff

## [CURRENT LIVE BLOCKER]

코드상 blocker는 수정됐으나 실제 Provider+MOLEG 조합의 Full Legal C1 재검증은 사용자가 실행해야 한다. 따라서 현재 acceptance는 `PENDING USER RETEST`이다.

## [LEGAL EVIDENCE ROOT CAUSE]

Provider가 반환한 top-level 또는 nested finding reference가 실제 `officialEvidence.referenceIndex` 집합 밖을 가리켰고, 기존 provider schema가 런타임 허용 인덱스를 제한하지 못했다. 사후 `_validate_coverage()`가 이를 `EVIDENCE_REFERENCE_INVALID`로 거부했다.

## [KOREAN CONTENT POLICY]

JSON key, enum, canonical code는 영어로 유지하고 Plan/Candidate의 사용자-facing 사업 설명은 ko-KR을 canonical로 검증한다. 영어 설명 중심이면 `CONTENT_LANGUAGE_MISMATCH`이며 LIVE provider는 기존 사업 의미와 code를 유지한 문구 교정만 1회 수행한다.

## [IDEA BRIEF INTERPRETATION]

`IdeaBriefLabContext`가 Safety, Interpretation, commitment candidates, readiness, summary, contradictions, questions를 보존한다. custom 입력은 Idea Brief derivation을 1회만 수행하고 결과 interpretation을 `CanonicalSeed.interpretation`과 Market Seed `aiInterpretation`까지 전달한다. 이미 interpretation이 있는 snapshot은 재호출하지 않는다.

## [ANCHOR / REFINE INTENT]

`assess_anchor()`가 problem/target뿐 아니라 solution content와 `EXPLORE/REFINE/AS_IS`를 함께 평가한다. REFINE/AS_IS에서 원래 식재료 공급·연결 intent가 사라진 recipe-only 결과는 자동 PASS하지 않는다.

## [PLAN LOCK VALIDATION]

system-owned metadata 일치와 실제 business content를 분리했다. 실제 Plan의 가격·수익·채널·차별화 설명이 사용자 LOCK과 충돌하는지 별도로 검사한다.

## [PLAN POOL / RESERVE]

최대 5개 요청 시 desired pool은 7개다. 요청 7/반환 5는 `RESERVE_SHORTFALL`로 공개한다. Legal replan 시 reserve가 없으면 기존/rejected mechanics context를 포함한 targeted replacement plan 생성으로 Plan 검증부터 재진입한다.

## [CANDIDATE GOVERNANCE]

`OPEN/LOCKED/MISSING/TBD` 등 governance state를 사업값으로 허용하지 않는다. 미확정 targetRegion은 Legal KR 범위와 맞는 `대한민국` 가설로, 나머지 필드는 한국어 검증 필요 값으로 정규화한다.

## [7 HYPOTHESIS PROVENANCE]

TARGET_REGION, REVENUE_MODEL, PRICE, CHANNELS, DIFFERENTIATORS, PRE_MARKET_SOM_SHARE, PRE_MARKET_SOM은 사용자 LOCK이면 `USER_INPUT|USER_CONFIRMED / LOCKED / ACCEPTED`, 아니면 `AI_HYPOTHESIS / OPEN / PROPOSED`이다.

## [ACTUAL CANDIDATE MECHANICS]

Plan mechanics 객체를 Candidate envelope에 복사하지 않는다. Candidate의 solution, operation, partner, platform, role, transaction, payment, revenue, channel, physical/qualification 필드에서 8개 controlled code와 `labelKo/detailKo`를 새로 도출한다.

## [PLAN FIDELITY]

Plan controlled mechanics와 actual Candidate mechanics를 비교한다. 단순 문장 token 우연 일치가 아니라 구현된 구조 code를 사용하고 경계선에서만 semantic judge로 보낸다.

## [DISTINCTNESS]

free-form label 차이는 deterministic 차이로 세지 않는다. 비교는 controlled code와 canonical business fingerprint를 사용하며, 명확한 2개 이상 축 차이만 LEVEL_2 DISTINCT, 경계선은 semantic judge로 처리한다. Redesign/Replan은 최종 portfolio comparison context를 받으며 같은 lineage의 직접 부모만 self-duplicate 검사에서 제외한다.

## [LEGAL EXTERNAL FACTS]

사용자가 확정·LOCK한 targetRegion은 `fixedJurisdiction`, `USER_INPUT`, `LOCKED`로 `externalFactContext.facts`에 들어가며 실제 facts의 canonical hash를 `sourceSnapshotHash`로 사용한다.

## [LEGAL EVIDENCE BINDING]

실제 official evidence 생성 후 허용 인덱스를 top-level과 모든 nested `EvidenceBackedFinding` schema enum에 주입한다. material finding reference는 최소 1개다. Provider 입력에도 `allowedEvidenceReferenceIndexes`를 전달한다. binding 오류에 한해 판단·문구·상태를 바꾸지 않는 citation repair를 정확히 1회 허용하고 동일 validator를 다시 실행한다. 재실패 시 allowed/returned/invalid/duplicate index와 finding 위치를 안전 diagnostics로 남긴다.

## [REDESIGN]

Redesign 결과에서 mechanics를 다시 도출하고 Opportunity, Plan fidelity, hard LOCK, 한국어, 최종 portfolio distinctness를 재검증한 뒤에만 Legal을 다시 호출한다.

## [REPLAN]

reserve 또는 on-demand replacement를 actual Plan 검증부터 다시 통과시킨다. 기존 최종 portfolio와 deterministic/semantic distinctness를 모두 적용하고 Candidate 검증 통과 후에만 Legal을 실행한다.

## [PARTIAL PORTFOLIO]

후보 하나만 외부 사실 입력이 필요한 경우 해당 후보를 unresolved로 분리하고 나머지 4개로 `READY_LIMITED`를 반환한다. conflicting LOCK 등 전역 의사결정이 필요한 경우에만 전체 `NEEDS_INPUT`이다.

## [REPLAY]

record key는 operation, operationVersion, promptVersion, schemaVersion, canonicalInputHash를 포함한다. Idea Brief 성공 결과도 녹화/재생한다. 모델 목록도 JSON 객체 배열로 직렬화하도록 보완했다. fresh fixture에서 MOCK 13개 recording 후 REPLAY `READY_FULL / CONTRACT_PASS / topLevelExternalOperations=0 / REPLAY_READY`를 확인했다.

## [MANUAL HYPOTHESIS]

Core `run_full(auto_confirm_hypotheses=False)`가 기본이다. Notebook도 `CONFIRM_ALL_PROPOSED=False`이며 사용자가 True로 바꾸거나 `HYPOTHESIS_EDITS`를 제공해야 한다.

## [DELTA LEGAL]

법률 민감 가설 편집은 changed Candidate를 구성해 실제 Legal adapter를 실행한다. `mark_delta_legal_reviewed()`는 승인된 hash-bound `DeltaLegalResult` 객체 없이는 상태를 변경하지 않는다.

## [MARKET/MARKETING HANDOFF]

Interpretation, 선택 Concept ACCEPT Legal, 7개 확정 hypothesis, 필요한 Delta Legal 완료가 모두 있어야 `CONTRACT_PASS`다. Market/Marketing snapshot shape와 canonical hash를 MOCK E2E에서 확인했다.

## [TESTS PASS]

- `compileall`: PASS
- 기존 Concept Portfolio V2 40 tests: PASS
- 신규 41~79 normalization tests: PASS
- shared Legal + Idea Brief targeted 포함 선택 실행: `103 passed`
- Notebook JSON 4.5, 코드 셀 47개 syntax: PASS
- canonical Notebook outputs/execution count clear: PASS
- strict nbformat cell ID normalization/validation: PASS
- fresh MOCK Notebook Run All: PASS, `CONTRACT_PASS`
- fresh versioned MOCK recording → REPLAY full run: `READY_FULL / CONTRACT_PASS / REPLAY_READY / 외부 상위 작업 0`
- `git diff --check`: PASS (줄바꿈 변환 안내 외 whitespace error 없음)

## [NOT RUN]

- AI Provider LIVE
- MOLEG LIVE
- Docker/browser/provider smoke
- full regression/full postgresTest
- frontend production build
- V1 production route/DB/frontend integration

## [FILES MODIFIED]

- V2 Core: models, engine, providers, adapters, anchor/governance/distinctness/fidelity 정책
- 신규 V2 Core: `language_policy.py`, `mechanics.py`, `plan_policy.py`
- shared AI Legal: provider failure diagnostics, evidence model/service
- Notebook diagnostics, canonical Lab Notebook, README
- 기존 40 tests 보정, 신규 41~79 tests, shared Legal tests
- 이 결과 문서와 progress/verification 문서
- 사용자 소유 checkpoint 및 `ai/recordings/`는 수정하지 않음

## [GIT DIFF --STAT]

최종 수치는 작업 종료 시 `git diff --stat`와 `git status --short`로 재확인했다. untracked 신규 파일은 `git diff --stat`에 포함되지 않는 점을 구분한다.

## [USER NEXT LIVE TEST ORDER]

1. checkpoint/recordings를 보존하고 canonical Notebook을 새 커널로 연다.
2. `MODE='MOCK'` Run All → 44번 `CONTRACT_PASS` 확인.
3. `MODE='LIVE'`, 03 환경 확인, 04 Schema Preflight PASS/외부 작업 0 확인.
4. 06~18 Idea/Plan 단계 실행 → 한국어 Plan, 7개 요청, reserve 상태 확인.
5. 19~27 Candidate 1 → 나머지 Candidate, actual mechanics, Legal 입력 확인.
6. 28에서 `RUN_FULL_LEGAL_C1=True` → 허용 index와 C1 Full Legal 재검증.
7. C1 성공 후 29 `RUN_REMAINING_LEGAL=True` → 30~33 Redesign/Replan/부분 portfolio 확인.
8. 34 수동 선택, 35 일곱 가설 확인, 36 승인/편집.
9. 법률 민감 편집이 있으면 37 `RUN_DELTA_LEGAL=True`로 실제 결과 확인.
10. 38~40 Market/Marketing `CONTRACT_PASS` 확인.
11. 43 Replay manifest를 확인하고, 모든 staged LIVE가 성공한 뒤에만 46 one-click LIVE 가드를 켠다.
