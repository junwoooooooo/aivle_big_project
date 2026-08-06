# Phase Status Register

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: P0 through P13 status and handoff register
- Supersedes: None
- Implementation Status: PARTIAL

상태 값은 NOT_STARTED, IN_PROGRESS, BLOCKED, CORRECTION_REQUIRED, COMPLETE_WITH_CARRYOVER, COMPLETE만 사용한다.

## P0 — Repository Baseline and Re-foundation Audit

- 상태: COMPLETE
- 시작 branch/commit: main / e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- 완료 commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222에서 감사 문서 carryover 해결
- 범위/산출물: code-based inventory, Stable Core/legacy 분류, [audit](PHASE0_REPOSITORY_AUDIT.md)
- 실행 검증: branch/HEAD/status와 code/DB/API/document/test/CI 참조 검색
- 미해결 항목: 없음
- 다음 조건: 충족
- 받은 결정/전달 결정: repository baseline → P1 canonical reset

## P1 — Canonical Product and Architecture Documentation Reset

- 상태: COMPLETE
- 시작 branch/commit: refoundation/phase1-canonical-docs / e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- 완료 commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722, governance/detail 보완은 80ce95bbf53bcc5faeae894abc37c8a4cac02222에서 완료
- 범위/산출물: canonical docs 구조, legacy 문서 제거, design reference 분리
- 실행 검증: metadata/link/diff/machine input/design blob
- 미해결 항목: 없음
- 다음 조건: 충족
- 받은 결정/전달 결정: P0 분류 → Target Workflow/system boundary/TaskRun/api v2/report

## P1.1 — Documentation Hardening and Governance

- 상태: COMPLETE
- 시작 branch/commit: refoundation/phase1-canonical-docs / 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- 문서 hardening commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- closure commit: 41fd90e9fbbe63751ca42025551f11d17375d864
- PostgreSQL baseline test fix: PR #15 / commit c7baa9b4b466c9872dd66dc51526099e1a820412 / merge commit 19687dc0ae385d87c2369abd074eaf5cb32ffb89
- canonical docs merge: PR #14 / merge commit 6c43f97c884127257a5a733025475d60fd81ca21
- 범위/산출물: governance, operations, decision/change/evidence, canonical hardening
- 실행 검증: 1549a8e→80ce95b compare, links, metadata, governance columns, protected paths; PR #15의 backend, PostgreSQL, frontend, Docker E2E, contract-and-security, dependency-review 성공; PR #14 최종 CI 성공
- 미해결 항목: 없음
- 다음 조건: 충족
- 받은 결정/전달 결정: P1 Target/P0 baseline → P2 open decisions와 P0~P13 guardrails

## P2 — Domain and Contract Definition

- 상태: COMPLETE
- 시작 branch/commit: refoundation/phase2-domain-contracts / 6c43f97c884127257a5a733025475d60fd81ca21
- 완료 산출물 commit: 3f33357f5ae4a604fa97ba7da87d9a3a53ad4d51
- Subphase status: P2.1 `COMPLETE`, P2.2 `COMPLETE`, P2.3 `COMPLETE` (commit `cd1c9816a5b716533e3a79c459f42ce09bde3671`), P2.4 `COMPLETE` (final correction commit `2a667479ba37b3e6c0649124e750ff47f9718188`), P2.5 `COMPLETE` (final correction commit `134c5acbf7d858934888fd468de3b7b7e2e2da78`), P2.6 `COMPLETE` (closure commit `3f33357f5ae4a604fa97ba7da87d9a3a53ad4d51`)
- 범위/산출물: domain, workflow state/gate, provenance, public API contract, internal AI API contract, analysis input/output contract
- 허용 schema/contract: logical domain schema, state and cardinality definitions, public/internal API JSON schema, contract examples and fixtures
- 금지: Flyway migration, JPA entity implementation, physical production table creation, Controller/Service runtime implementation
- 실행 검증: Public/Internal Contract와 Fixture Validator 완료; P2.6 final validator `RESULT=PASS`
- 미해결 항목: P2 범위 차단 요소 없음; OD-008 provider 선택은 각 provider-dependent slice 진입 전 decision gate
- 완료 조건: P2 due decision 확정, implementation-ready schema/contract/fixture, 문서 간 consistency 검증
- 다음 조건: Phase 2 PR Remote CI 성공 및 main merge 후 P3 시작
- 받은 결정/전달 결정: P2.3 TaskRun binding/status/capability/error → P2.4 public API v2 contract → P2.5 internal contract와 P2.6 fixtures

## P3 — Stable Platform Guard and TaskRun Foundation

- 상태: COMPLETE
- 시작 branch/commit: `implementation/phase3-taskrun-foundation` / `2da4c9caa6f0f39f2fb642c6ce1dd79cc6464758`
- 완료 commit: 이 Phase 최종 commit에서 확정
- 범위/산출물: Stable Core guard, V27 Target TaskRun/TaskAttempt/TaskResult, claim/lease/heartbeat/recovery, retry/cancel/idempotency, Spring internal execution client, FastAPI `/internal/v1/ai/executions`, public v2 TaskRun GET/retry/cancel
- 실행 검증: H2 Flyway/Hibernate context, Target domain/client tests, AI pytest, P2 fixture validator, OpenAPI lint, frontend lint/test/build, Docker E2E, PostgreSQL 및 전체 backend 결과는 Verification Evidence에 기록
- 미해결 항목: Remote CI와 main merge만 남음. OD-008은 provider-dependent slice 전까지 DEFERRED
- 다음 조건: P3 PR CI 성공 및 main merge 후 P4 시작
- 받은/전달 결정: P2 exact contract → P4~P10이 재사용할 TaskRun·execution boundary. P4 제품 aggregate는 구현하지 않음

## P4 — Idea Intake, Normalization and Korean Legal Review

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: IdeaSource/IdeaVersion/Legal vertical slices
- 실행 검증: owner/file/legal provenance/error/E2E 예정
- 미해결/다음 조건: FILE·법령 integration / P3 COMPLETE
- 받은/전달 결정: TaskRun/API foundation → verified idea/legal inputs

## P5 — Concept Builder and Quick Assessment

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: concept/version/quick assessment slices
- 실행 검증: provenance/AI/owner/frontend 예정
- 미해결/다음 조건: Concept contract / P4 COMPLETE
- 받은/전달 결정: idea/legal inputs → shortlist candidates

## P6 — Shortlist, Detailed Analysis and Concept Selection

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: shortlist, detailed analysis, explicit user selection
- 실행 검증: analysis contracts, stale, AI-vs-user decision 예정
- 미해결/다음 조건: detailed inputs / P5 COMPLETE
- 받은/전달 결정: candidates → selected ConceptVersion

## P7 — Three-Layer Persona Cards

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: PersonaStudy와 Persona Card slice
- 실행 검증: provenance/owner/card validation 예정
- 미해결/다음 조건: Persona axes / P6 COMPLETE
- 받은/전달 결정: selected concept → interview-ready cards

## P8 — Independent Persona Interviews

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: Persona별 독립 interview TaskRun slices
- 실행 검증: isolation/retry/stale/owner/E2E 예정
- 미해결/다음 조건: interview contract / P7 COMPLETE
- 받은/전달 결정: Persona Cards → independent interview evidence

## P9 — Marketing Workspace and Persona-Based Comparison

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: asset workspace/version/comparison slices
- 실행 검증: Storage/AI binary/relative-claim/UI 예정
- 미해결/다음 조건: binary/asset contracts / P8 COMPLETE
- 받은/전달 결정: interview evidence → report-ready marketing evidence

## P10 — Persisted Final Report

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: report snapshot/version/view/export
- 실행 검증: version/provenance/storage/export/E2E 예정
- 미해결/다음 조건: initial export / P9 COMPLETE
- 받은/전달 결정: workflow evidence → persisted report baseline

## P11 — Admin and Landing Transition

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: Target Admin/Service Policy와 Landing content/workflow/demo/CTA 전환
- 실행 검증: admin authorization/policy/audit/frontend/accessibility 예정
- 미해결/다음 조건: Target slices ready / P10 COMPLETE
- 받은/전달 결정: complete Target workflow → removal-ready consumers

## P12 — Legacy Removal and Database Cutover

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: legacy API/route/code/test/artifact 제거와 신규 Flyway drop migration
- 실행 검증: replacement tests, Flyway fresh/upgrade/validate, reference scan 예정
- 미해결/다음 조건: FK/drop ordering / P11 COMPLETE와 대체 consumer/test
- 받은/전달 결정: transitioned Admin/Landing → clean Target runtime/schema

## P13 — Integrated Quality, Manual Testing and Release Hardening

- 상태: NOT_STARTED
- 시작/완료 branch·commit: 미정
- 범위/산출물: full integration, manual UX/operations, security, deployment/release evidence
- 실행 검증: full local suite, Docker E2E, manual scenarios, Remote CI/security 예정
- 미해결/다음 조건: deployment/release evidence / P12 COMPLETE
- 받은/전달 결정: clean Target system → release decision
