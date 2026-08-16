# V2-0 — Authoritative Contract Reset User Verification

## 목적

V2-0이 제품 코드를 변경하지 않고 일곱 상위 계약을 동일한 V2 방향으로 정렬했는지 확인한다.

## 1. 변경 범위 확인

Repository root에서 실행한다.

```powershell
git status --short
git diff --stat
```

예상 결과:

- 상위 계약 7개와 V2-0 RESULT/USER_VERIFICATION 문서만 V2-0 변경으로 보인다.
- backend, frontend, AI, migration 파일은 V2-0 변경에 포함되지 않는다.

## 2. 최소 Seed와 source/authority 확인

```powershell
rg -n "ideaOverview|problem|targetUsers|USER_INPUT|AI_DERIVED|AI_HYPOTHESIS|LOCKED|REVIEWABLE" docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md
```

예상 결과:

- 초기 필수값은 `ideaOverview`, `problem`, `targetUsers` 정확히 세 개다.
- optional Seed는 입력 시 `USER_INPUT + LOCKED`, 미입력 시 `OPEN`이다.
- AI Interpretation은 `AI_DERIVED + REVIEWABLE`, AI 가설은 `AI_HYPOTHESIS + PROPOSED`로 구분된다.

## 3. Concept와 Legal 순서 확인

```powershell
rg -n "EXPLORE|REFINE|AS_IS|ConceptCandidateV2|DISTINCTNESS|Legal Fact Pattern|LEGAL REVIEW|INSUFFICIENT_DISTINCT_CONCEPTS" docs/rebuild
```

예상 결과:

- AS_IS도 Concept Factory를 생략하지 않고 Candidate 1에 원안을 보존한다.
- 목표는 distinct/legal eligible 5개지만 중복으로 채우지 않는다.
- 검증 순서는 schema → LOCKED/origin → distinctness → legal이다.
- Legal Fact Pattern은 Concept가 생성하고 가설이 Legal Review보다 먼저 완성된다.

## 4. Snapshot과 downstream 확인

```powershell
rg -n "MarketAnalysisSeedSnapshot|TechOpsInputSnapshot|FinancialInputSnapshot|MarketingSourceSnapshot" docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md docs/rebuild/EXTERNAL_MODULE_HANDOFF_CONTRACT_v1.0.md
```

예상 결과:

- Market은 `MarketAnalysisSeedSnapshot`만 정식 입력으로 받는다.
- TechOps와 Finance는 분석 직전 Preparation과 개별 immutable Snapshot을 사용한다.
- Marketing Source는 Selected Concept, final accepted hypotheses, Legal Result다.

## 5. 구 active 계약 제거 확인

```powershell
rg -n "planningChangeProposals|planning_change_proposals|FinalizedPlanningSnapshot|시장분석 반영안" docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md docs/rebuild/EXTERNAL_MODULE_HANDOFF_CONTRACT_v1.0.md docs/rebuild/NEW_PIPELINE_UI_UX_SPEC_v1.0.md
```

예상 결과:

- 검색 결과가 없거나, 모든 결과가 “active contract가 아님”, “필수가 아님”, “제공하지 않음”, “제거” 문맥이다.
- planning-change workflow를 요구하는 문장은 없어야 한다.

## 6. 문서 whitespace 확인

```powershell
git diff --check
```

예상 결과: 출력 없이 exit code 0.

## 7. 승인 기준

- 일곱 문서에서 Seed → Safety → Interpretation → Concept → distinctness → Legal → selection → hypothesis decision → Delta Legal → Market Snapshot 순서가 동일하다.
- Market Result가 Concept/Planning을 자동 변경하지 않는다.
- TechOps stage가 존재하고 Finance가 상위 값을 승계한다.
- Marketing이 Market Result나 `FinalizedPlanningSnapshot`을 필수 입력으로 요구하지 않는다.
- V2-1 제품 코드 구현은 포함되지 않는다.
