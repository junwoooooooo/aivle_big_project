# Concept Portfolio Engine V2 LIVE 정규화 결과

## 상태

**IMPLEMENTATION COMPLETE**  
**MOCK/STATIC/TARGETED VERIFICATION COMPLETE**  
**LIVE PROVIDER/MOLEG ACCEPTANCE PENDING USER TEST**

기존 V1 production 경로는 변경하지 않고 `ai/app/concept_portfolio_v2` 격리 패키지와 canonical Notebook만 정규화했다.

## 1. 실제 LIVE 실패 증거

사용자 LIVE Notebook 실행에서 Safety는 통과했으나 Planning 첫 구조화 출력 요청이 HTTP 400으로 종료되었다. 실패 코드는 `PROVIDER_RESPONSE_SCHEMA_REJECTED`였고, Plan 응답 schema 안의 system-owned 동적 map 필드가 Provider strict `response_format` 계약에서 거부되었다. Plan은 한 건도 생성되지 않았으며 이 400은 transient 재시도 대상이 아니다.

## 2. Root cause

기존 `PortfolioPlan`은 Provider가 생성해야 할 business draft와 system이 소유해야 할 `planId`, `preservedAnchors`, `preservedLocks`를 하나의 strict schema에 혼합했다. 후자의 `dict[str, str]`는 arbitrary dynamic object schema를 만들었고 Provider strict schema acceptance 경계를 위반했다.

## 3. Provider Draft / Canonical 분리

- Provider 출력: `PortfolioPlanDraft`, `PlanDraftPool`
- 고정 8필드 `MechanicsDescriptor`로 mechanics를 구조화했다.
- Provider schema에서 `planId`, `preservedAnchors`, `preservedLocks`를 제거했다.
- system normalizer가 입력 순서에 따라 `P1..Pn`을 결정론적으로 부여하고 anchor/lock을 authoritative analysis에서 복사한다.

## 4. Schema preflight

- root object, required/properties 일치, `additionalProperties=false`, array items, union/ref, depth, unsupported keyword를 재귀 검사한다.
- dynamic map은 `DYNAMIC_OBJECT_NOT_STRICT_COMPATIBLE`로 Provider 호출 전에 실패한다.
- `PlanDraftPool`, 기존 accepted `ConceptCandidateDraft`, semantic distinctness/fidelity schema를 검사한다.
- 검사 자체의 Provider 호출 수는 0이다.

## 5. Candidate governance

- Provider는 `ConceptCandidateDraft`만 반환한다.
- system normalizer가 31개 semantics와 metadata를 부여한다.
- `targetRegion`, `revenueModel`, `price`, `channels`, `differentiators`의 direct user LOCK은 Provider 응답보다 우선하며 최종 Candidate에 결정론적으로 복원된다.
- budget/team/timeline/other 및 known competitor 제약은 `constraintCompliance`에 보존한다.

## 6. Provenance 수정

- EXPLORE/REFINE의 `conceptDefinition`은 `CONCEPT_GENERATED / REVIEWABLE / PROPOSED`다.
- AS_IS Candidate 1의 원본 `conceptDefinition`, `problemScenario`, `targetUsers`만 `USER_INPUT / LOCKED / ACCEPTED`로 유지한다.
- pre-market SOM 두 필드는 계속 `AI_HYPOTHESIS / OPEN / PROPOSED`다.

## 7. Anchor policy

- source authority(`sourceLocks`)와 business opportunity identity(`OpportunityAnchor`)를 분리했다.
- target/problem 문자열 exact equality를 제거했다.
- 동일 opportunity의 직장인 1인 가구 같은 하위 segmentation은 허용하고, 개인 가구에서 기업 구내식당으로의 domain drift는 `ANCHOR_DRIFT`로 거부한다.

## 8. diversityCapacity hard cap 제거

`diversityCapacity`와 `suggestedMaxConcepts`는 진단값으로만 유지한다. optional LOCK 수가 많더라도 실제로 서로 다른 유효 Plan 5개가 있으면 5개를 허용한다. 최종 hard cap은 사용자 요청 `maxConcepts <= 5`뿐이다.

## 9. Semantic distinctness

- Level 1: canonical mechanics가 같으면 `DUPLICATE`.
- Level 2: 2개 이상 mechanics 차이가 있으면 `DISTINCT`.
- Level 3: 한 차이만 있는 ambiguous pair만 semantic Provider judge로 보낸다.
- Plan과 Candidate 모두 동일한 8필드 mechanics descriptor를 사용한다.
- problem/target 공통성은 중복 근거로 사용하지 않는다.

## 10. Plan fidelity

문장 exact equality 대신 solution, operation, partner, transaction, commercial, fulfillment mechanics의 token/semantic key를 비교한다. 결정론적 판정이 ambiguous일 때만 semantic fidelity judge를 호출한다.

## 11. Legal redesign budget

재설계 횟수를 전체 portfolio가 아니라 `lineageId`별로 계산한다. 서로 다른 두 lineage는 각각 1회 재설계할 수 있다. 동일 lineage가 다시 재설계를 요구하면 `REDESIGN_BUDGET` system failure로 기록하며 2차 child를 만들지 않는다.

## 12. Replan full validation

대체 Plan은 기존/실패 Plan과 mechanics가 다른 미사용 Plan에서 고르고 다음 경로를 모두 재진입한다.

```text
Plan validation → Candidate expansion → governance → lock/anchor/fidelity/distinctness validation → Legal
```

lock violation, anchor drift, fidelity/duplicate 실패 Candidate에는 Legal을 호출하지 않는다. Legal-LOCK 충돌은 재생성하지 않고 `NEEDS_INPUT`으로 종료한다.

## 13. Downstream contract correction

- `market-analysis-seed-snapshot-v1.legalResult`에 `requiredPartnersAndQualifications`와 `deltaLegalReviews`를 포함했다.
- Market/Marketing required shape 검사를 분리해 `STRUCTURE_PASS/FAIL`을 만든다.
- 7개 hypothesis 확정, ACCEPT Legal, delta legal 완료까지 검사해 `CONTRACT_PASS/FAIL`을 만든다.
- 호환성 `PASS`는 `CONTRACT_PASS`일 때만 반환한다.

## 14. Snapshot hash compatibility

기존 Java `SnapshotHasher`와 같은 NFC 문자열 정규화, object key 정렬, array 순서 유지, bool/null, trailing-zero 제거 숫자 표현을 사용하는 Python canonical JSON을 재사용했다. Pydantic 중첩 모델은 primitive JSON 값으로 변환한 뒤 `sha256:` hash를 계산한다. 고정 cross-contract fixture, NFC 동등 문자열, `1.0 == 1` 표현을 검증했다.

## 15. Notebook UX

- 00~37 staged section으로 재구성했다.
- 기본 `MODE='MOCK'`, output clear, `execution_count=null`이다.
- LIVE 첫 단계는 Provider 0회 Schema Preflight다.
- Plan Draft/normalization, Candidate 1-only, Legal 1-only를 분리했다.
- schema/response-format failure 뒤 진행 금지 문구와 safe provider diagnostic을 표시한다.
- 논리 작업 수와 실제 외부 Provider 호출 수를 분리한다.
- `auto_confirm_hypotheses`는 MOCK 전용 Lab shortcut이며 LIVE 기본값은 False다.
- One-click LIVE는 `RUN_ONE_CLICK_LIVE=False`로 기본 비활성화했다.

## 16. 테스트

실행 완료:

```text
python -m compileall -q app/concept_portfolio_v2 app/providers/structured.py
python -m pytest tests/concept_portfolio_v2/test_engine.py -q --tb=short
40 passed
Schema preflight: ALL PASS, Provider Calls 0
Notebook JSON/코드 셀 syntax/output-clear 검사: PASS
fresh-kernel MOCK nbconvert execute: PASS
git diff --check: PASS
```

최종 상세 명령 결과는 progress 문서에 기록한다.

## 17. NOT RUN

- 실제 LIVE Provider 호출
- 실제 MOLEG 호출
- 전체 AI/backend/frontend 회귀
- 전체 postgresTest/Testcontainers
- Docker/browser smoke
- frontend production build

LOCAL FAST EXECUTION PROFILE 및 사용자 지시대로 실행하지 않았다.

## 18. 사용자 LIVE 검증 절차

1. Python 3.12 venv에서 Notebook Kernel Restart.
2. `MODE='MOCK'` Run All → 정상 terminal 상태와 `CONTRACT_PASS` 확인.
3. `MODE='LIVE'` 변경 후 04 Schema Preflight까지만 실행 → ALL PASS, Provider Calls 0.
4. Safety + Seed Analysis.
5. Plan Pool LIVE 1회 → 5~7 Draft, schema accepted, system normalization/locks 확인.
6. Plan Diversity 확인.
7. Candidate 1 LIVE → 31 semantics, locks, provenance, fidelity 확인.
8. remaining Candidates.
9. Legal Candidate 1 → evidence/route/controls/redesign requirements 확인.
10. remaining Legal, 필요 시 Redesign/Replan.
11. Final Portfolio에서 Concept 수동 선택.
12. 7 hypotheses 수동 확인 및 필요 시 delta legal.
13. Market/Marketing handoff `CONTRACT_PASS` 확인.
14. staged 결과가 모두 성공한 뒤에만 One-click LIVE를 명시적으로 활성화.

실패 시 `show_provider_failure(engine.gateway)`, Schema Preflight 표, Provider Usage, Trace를 함께 보존한다. 비밀키 값은 수집하지 않는다.
