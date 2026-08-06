# Final Report Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Logical report identity, immutable snapshots, HTML view and PDF artifact
- Supersedes: Browser-composed runtime report
- Implementation Status: NOT_STARTED

## FinalReport

Project당 최대 하나의 logical FinalReport를 둔다(`1:0..1`). 보고서 재생성·수정은 FinalReport identity를 바꾸지 않고 immutable FinalReportVersion을 추가한다.

| Concern | Logical contract |
|---|---|
| Identifier/owner | FinalReport identifier; Project composition과 owner scope |
| Cardinality | Project `1:0..1` logical report; FinalReport `1:N` versions |
| Semantics | current FinalReportVersion reference와 report lifecycle |
| Mutability | logical identity/current pointer/status mutable |
| Lifecycle | `DRAFT`, `GENERATING`, `READY`, `FAILED`, `ARCHIVED` |
| Time/concurrency | 생성·마지막 생성/갱신 시각; status/current pointer에 optimistic concurrency |
| Provenance | Project identity; content provenance는 version에 고정 |
| Delete | version/export history가 있으면 archive; retention 후 Spring만 artifact/reference 삭제 |
| Uniqueness | Project당 logical FinalReport 최대 하나, current version 최대 하나 |

`READY`는 current immutable version과 필요한 PDF artifact가 검증됐음을 뜻한다. browser에서 화면을 조립했다는 사실만으로 report를 `READY`로 만들지 않는다.

## FinalReportVersion

| Concern | Logical contract |
|---|---|
| Identifier/owner | FinalReportVersion identifier; FinalReport composition과 Project scope |
| Cardinality/version | FinalReport `1:N`; report version number 유일·단조 증가 |
| Upstream snapshot | exact IdeaVersion, accepted LegalReviewRun/source refs, ConceptVersion, Quick/Detailed results, ShortlistDecision, ConceptSelection, PersonaStudy/Card/Interview/Synthesis, MarketingWorkspaceVersion/AssetVersion/Comparison refs 중 report에 포함된 집합 |
| Content categories | facts, legal sources, AI proposals, assumptions, research needs, user decisions를 분리 |
| Report decision | `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP` |
| Decision boundary | report decision actor와 rationale을 보존; AI recommendation은 별도 category이며 user decision으로 가장하지 않음 |
| Mutability | immutable structured snapshot. 수정·재생성은 새 version |
| Lifecycle/validity | `AVAILABLE`, `WITHDRAWN`; 생성 당시 source validity를 고정하고 이후 upstream 변경 시 `STALE_AT_SOURCE` 표시 가능 |
| Time | snapshot 생성 시각, 사용자 report decision 시각, export 생성 시각 방향 |
| Concurrency | content update 없음; FinalReport current pointer와 artifact adoption에 필수 |
| Provenance | exact upstream refs와 input snapshot/hash, generator TaskRun/Result, user decisions |
| Delete | current/previous 조회와 audit에 필요한 동안 보존; hard delete는 retention 정책 |
| Uniqueness | FinalReport + version number; version당 accepted PDF export role 최대 하나 방향 |

FinalReportVersion 생성은 current pointer를 동적으로 따라가는 runtime composition이 아니다. Spring이 포함할 exact reference 집합의 owner, lifecycle, validity와 provenance를 검증하고 RDB에 structured snapshot으로 저장한다.

## View and export

- HTML view는 저장된 structured snapshot의 표현이며 별도 report source of truth가 아니다.
- PDF export는 Spring이 snapshot에서 생성·검증하고 Object Storage에 저장한 artifact다. RDB의 report/artifact reference가 lifecycle source of truth다.
- AI Server는 PDF object key, Storage URL, presigned URL 또는 local artifact path를 생성·보유하지 않는다.
- Markdown export는 초기 범위에서 제외한다.
- PDF 생성 실패는 immutable snapshot을 손상시키지 않는다. FinalReport lifecycle/export 상태로 분리해 재시도한다.

## Stale and version rules

- FinalReportVersion은 생성 후 자동 수정하지 않는다.
- upstream 변경은 기존 version/history를 보존하고 `STALE_AT_SOURCE`로 표시한다. 새 current report가 필요하면 새 version을 생성한다.
- 새 FinalReportVersion이 current가 되어도 이전 version은 조회 가능 history로 유지한다.
- report decision 변경은 기존 version을 덮어쓰지 않고 새 FinalReportVersion 또는 별도 후속 user decision contract로 남긴다. 선택은 P2.3 public/report contract에서 확정한다.
