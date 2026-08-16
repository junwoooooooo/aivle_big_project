# V2-0 — Authoritative Contract Reset Result

## 상태

Contract reset complete. Product-code implementation has not started. Runtime acceptance is intentionally pending for V2-1 and later Units.

## 실행 기준

- Branch: `rebuild/new-pipeline-v1`
- Starting HEAD: `fbb6144`
- Starting worktree: clean
- Authority: 2026-08-08 `MASTER REBUILD DIRECTIVE — MARKET-SEED / CONCEPT / LEGAL / ANALYSIS INPUT PIPELINE V2`
- Boundary: V2-0 only

## 실제 수정 파일

- `docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/EXTERNAL_MODULE_HANDOFF_CONTRACT_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_UI_UX_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_IMPLEMENTATION_PLAN_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`
- `docs/rebuild/progress/V2-0-AUTHORITATIVE-CONTRACT-RESET_RESULT.md`
- `docs/rebuild/verification/V2-0-AUTHORITATIVE-CONTRACT-RESET_USER_VERIFICATION.md`

제품 코드, migration, schema, test code는 수정하지 않았다.

## 충돌 목록과 정정

| 기존 active 계약 | V2 정본 |
|---|---|
| 다수 Idea Brief field와 legal/operation detail을 초기 필수값으로 요구 | 초기 필수 Seed는 `ideaOverview`, `problem`, `targetUsers` 정확히 3개 |
| 지역·결제·개인정보·파트너 등을 초기 질문으로 확보 | optional LOCKED Seed 또는 AI Interpretation/Concept-generated structure로 이동 |
| Safety와 Legal이 명확히 분리되지 않음 | Safety Gate와 official-evidence Legal Review를 별도 의미·상태로 고정 |
| AI 해석·가설·사용자 사실의 authority 경계가 약함 | `USER_INPUT`, `AI_DERIVED`, `AI_HYPOTHESIS`, `CONCEPT_GENERATED`, `ANALYSIS_RESULT` 및 authority/decision 고정 |
| 고정 Slot 방향 중심 후보 생성 | 입력 구체화에 따른 `EXPLORE`, `REFINE`, `AS_IS`; AS_IS original Candidate 지원 |
| 후보 이름/Slot 차이에 의존 가능 | schema → LOCKED/origin → semantic distinctness → legal 순서와 fingerprint 고정 |
| Legal Context/Review가 Concept 가설보다 앞설 수 있음 | complete Concept와 legal-sensitive hypotheses 생성 후 Legal Review |
| 법률 구조 부족을 사용자 질문/`NEEDS_FACTS`로 전가 가능 | Concept가 Legal Fact Pattern을 생성; 실제 외부 현실 사실에만 `NEEDS_FACTS` |
| 선택 전 후보 가설 확정 또는 후보 내부 JSON에만 가설 저장 가능 | 선택 Concept에만 별도 `ConceptHypothesisDecision`과 alternative path 제공 |
| Legal Review 이후 가설 변경의 법률 상태 재사용 | legal-sensitive 변경은 Delta Legal Review, SOM은 non-legal |
| Market handoff가 SelectedConcept/Planning 중심 | `MarketAnalysisSeedSnapshot`만 Market의 정식 입력 |
| Market Result → planning proposal/decision → FinalizedPlanning | planning-change active workflow 제거; Market은 분석 결과만 반환하고 Concept를 변경하지 않음 |
| BM·Finance·Persona가 FinalizedPlanning을 공통 mandatory input으로 소비 | 각 분석 직전 Preparation/Snapshot boundary, 기존 Persona는 독립 계약 유지 |
| 기술·운영 단계 제외 | 독립 TechOps stage와 `TechOpsInputSnapshot` 재도입 |
| Marketing이 FinalizedPlanning/Market insight를 기다림 | Selected Concept + final accepted hypotheses + Legal Result를 mandatory Source로 사용 |
| 후속 모듈이 공통 Entity 정본을 공유 | 모듈별 immutable Snapshot, hash, schemaVersion, createdAt 고정 |

## 구현한 계약

- 최소 Seed 3개와 optional `USER_INPUT + LOCKED`
- Safety Gate, reviewable AI Interpretation, 최소 follow-up
- Concept generation strategy와 distinct eligible target 5
- bounded 5 initial / 15 inspected / replacement round 2 / redesign 1
- `ConceptCandidateV2`, structured pre-market SOM, LOCKED validation
- Concept-generated Legal Fact Pattern, distinctness-before-legal, hypothesis-before-legal
- eligible publication, selection, hypothesis decision, alternative proposal, Delta Legal
- immutable Market/TechOps/Finance/Marketing Snapshot과 external handoff
- planning-change removal, TechOps/Finance preparation, Marketing Source cutover
- V2 task types, terminal immutability, actionable `NEEDS_INPUT`, safe observability
- V2-0~V2-9 단계별 implementation boundary

## 실행한 검사

- `git branch --show-current`
- `git rev-parse --short HEAD`
- `git status --short`
- 대상 문서 전체 조사 및 heading/계약 검색
- V2 필수 용어 coverage 검색
- 구 planning/FinalizedPlanning 참조 문맥 검색; 모든 잔존 참조가 제거·비필수 문맥임을 확인
- `git diff --stat`
- `git diff --check` — 통과

## 의도적으로 생략한 검사

- backend compile/test/postgresTest/Testcontainers
- AI pytest/provider smoke
- frontend Vitest/ESLint/production build
- Docker/browser/E2E/CI

V2-0은 문서 계약만 변경했으므로 코드 실행 검사는 관련성이 없고 fast execution 규칙에 따라 생략했다.

## 남은 위험

- 현재 제품 코드와 DB에는 구 Idea, Concept, Planning, Marketing 계약이 남아 있을 수 있다.
- V2 API의 정확한 path와 physical table shape는 각 Unit에서 현재 convention/migration을 조사해 확정해야 한다.
- 기존 Persona 계약은 보존했지만 새 Snapshot 경계와의 실제 adapter 정합성은 아직 검증하지 않았다.
- 실제 provider output, browser UX, external module callback은 아직 V2 runtime acceptance를 받지 않았다.
- 문서 파일명 `v1.0`은 AGENTS.md와 기존 링크 호환을 위해 유지했으며 본문에서 V2 정본임을 명시했다.

## 정확한 continuation point

다음 Unit은 `V2-1 — MARKET-SEED-INTAKE-AND-INTERPRETATION`이다. 시작 시 현재 worktree의 V2-0 문서 변경과 사용자 변경의 overlap을 다시 확인한 뒤, Seed domain/API/frontend, current `IdeaBriefFieldCatalog`, Safety/Interpretation provider path, 관련 migration과 targeted tests만 조사한다.

V2-0 요청 경계에 따라 V2-1 구현은 시작하지 않았다.
