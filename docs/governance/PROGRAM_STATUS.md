# Re-foundation Program Status

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Program-level status, next actions and blockers
- Supersedes: None
- Implementation Status: PARTIAL

## Program objective

Project 전체를 하나의 아이디어 검증 과정으로 재정립하고, 입력 일반화부터 한국 법률 검토, concept 생성·평가·선택, 독립 Persona 인터뷰, Marketing 비교, 저장 가능한 Final Report까지의 Target Workflow를 구현한다. 인증·owner scope·Spring의 RDB/Object Storage 소유권을 Stable Core로 보호한다.

## Current status

| Item | Value |
|---|---|
| Branch | implementation/phase3-taskrun-foundation |
| P2 starting commit | 6c43f97c884127257a5a733025475d60fd81ca21 |
| Code baseline | e16bd316ac881f4c5fab076e65c14657f6a8c7d4 |
| P1 documentation commit | 1549a8efa0aeb2ca400f4795c1c44b34868e4722 |
| P1.1 documentation commit | 80ce95bbf53bcc5faeae894abc37c8a4cac02222 |
| Current document phase | P3 COMPLETE |
| Current delivery state | Phase 3 stable platform and TaskRun foundation implemented; PR and Remote CI pending |
| P2.3 status | COMPLETE at `cd1c9816a5b716533e3a79c459f42ce09bde3671` |
| P2.4 status | COMPLETE at `2a667479ba37b3e6c0649124e750ff47f9718188` |
| P2.5 status | COMPLETE at `134c5acbf7d858934888fd468de3b7b7e2e2da78` |
| P2.6 status | COMPLETE at `3f33357f5ae4a604fa97ba7da87d9a3a53ad4d51` |
| Next phase | P4 — Idea Intake, Normalization and Korean Legal Review |
| Next action | Phase 3 PR 및 Remote CI; main merge 전 P4 시작 금지 |
| Governance phases completed | P0, P1, P1.1, P2, P3 |
| Implementation phases completed | 1 |
| Vertical slices completed | 0 |
| New product implementation | Platform foundation only; workflow vertical slices 0 |
| Primary blocker | 없음 |

## Stable Core

Auth, JWT/refresh, admin authorization, Project CRUD와 owner scope, cross-owner 404, Spring JPA/Flyway, Spring Object Storage, 공통 오류, audit를 유지한다.

## Target Workflow

Idea Intake → Idea Normalization → Korean Legal Review → Concept Builder → Quick Assessment → Shortlist → Detailed Analysis → Concept Selection → Three-Layer Persona Cards → Independent Persona Interviews → Marketing Workspace → Persona-Based Marketing A/B Comparison → Persisted Final Report.

## Roadmap

P2는 domain/contract, P3는 Stable Platform/TaskRun, P4~P10은 workflow vertical slices다. P11은 Admin과 Landing 전환, P12는 legacy 제거와 database cutover, P13은 통합 품질·수동 검증·release hardening을 담당한다.

## Recent decisions

- Spring이 RDB와 Object Storage를 전담한다.
- AI Server의 RDB, Storage, presigned URL, 업무 산출물 로컬 저장을 금지한다.
- AnalysisJob 확장 대신 신규 TaskRun 계열을 도입한다.
- Persona Card와 Independent Interview를 P7/P8로 분리한다.
- Admin/Landing, legacy cutover, integrated release를 P11/P12/P13으로 분리한다.
- P2.1에서 OD-001~OD-007과 OD-009를 ACCEPTED하고 OD-008을 provider-dependent slice 진입 전까지 DEFERRED했다.

## Recent verification

P2.6 closure commit `3f33357f5ae4a604fa97ba7da87d9a3a53ad4d51`은 fixture 151개, negative validation 실행 coverage 65/65, Bounds classification 362/362와 string literal equality 검증을 포함한 final validator `RESULT=PASS`를 기록한다. P2 domain/public/internal contract와 fixture 산출물은 완료됐지만 제품 구현은 시작되지 않았고, 구현 진행률과 vertical slice는 여전히 0이다. OD-008은 provider-dependent slice 진입 전까지 DEFERRED이며 다음 작업은 Phase 2 PR과 Remote CI다. 상세 이력은 [Verification Evidence](VERIFICATION_EVIDENCE.md)를 따른다.
