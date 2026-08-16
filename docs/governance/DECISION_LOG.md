# Decision Log

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Program decisions, deferred decisions and downstream impact
- Supersedes: Decisions dispersed across Phase 1 documents
- Implementation Status: PARTIAL

| Decision ID | Title | Status | Introduced Phase | Decided Phase | Decision | Rationale | Alternatives | Affected Documents | Downstream Code Impact | Test Impact | Supersedes | Superseded By |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DEC-001 | Spring data ownership | ACCEPTED | P0 | P1 | Spring이 RDB와 Object Storage를 전담한다. | 인증·owner·transaction·artifact 무결성을 한 경계에서 보장 | AI Server의 DB/Storage 접근 | architecture, contracts, quality | repository/storage access를 Spring에 한정 | owner/storage regression | 혼합 소유 가능성 | — |
| DEC-002 | AI Server storage prohibition | ACCEPTED | P0 | P1 | AI Server의 RDB, Storage, presigned URL, 업무 결과 로컬 저장을 금지한다. | 업무 상태와 데이터 소유권 회귀 방지 | presigned GET/PUT, shared volume | AI boundary, data architecture | 현재 presigned/outputs 경로 제거 | negative boundary/AI contract | P0의 presigned 허용 미결정 | — |
| DEC-003 | Generic TaskRun | ACCEPTED | P0 | P1 | AnalysisJob 확장 대신 TaskRun, TaskAttempt, TaskResult, TaskArtifact 방향을 채택한다. | legacy source FK와 분석 의미 분리 | AnalysisJob 일반화 | architecture, domain, migration | 신규 platform model 필요 | lifecycle/concurrency/contract | AnalysisJob 중심 | — |
| DEC-004 | No legacy data migration | ACCEPTED | P0 | P1 | 기존 데이터는 테스트 데이터이므로 이관하지 않는다. | 보존 요구가 없고 legacy schema 제거 단순화 | transform/archive | migration, quality | 신규 drop migration | fresh/upgrade/validate | legacy migration 검토 | — |
| DEC-005 | Workflow API v2 | ACCEPTED | P1 | P1 | 신규 Workflow public API는 /api/v2를 사용한다. | stable /api/v1과 계약 분리 | /api/v1 확장 | contracts, migration, UI route | 신규 controllers/clients | version/owner/contract | 기존 workflow API | — |
| DEC-006 | Persisted Final Report | ACCEPTED | P0 | P1 | Final Report는 RDB snapshot/version과 Storage export를 가진다. | 재현 가능한 현재·이전 보고서 | runtime-only view | product, domain, architecture | 신규 aggregate/API/export | version/provenance/integrity | runtime report | — |
| DEC-007 | Independent Persona interviews | ACCEPTED | P1 | P1 | Persona는 토론하지 않고 각각 독립 interview를 수행한다. | 실패·근거·관점 독립성 | panel discussion | product, persona domain | 독립 TaskRun orchestration | isolation/retry | fixed panel simulation | — |
| DEC-008 | Marketing Persona A/B | ACCEPTED | P1 | P1 | A/B는 Marketing Workspace의 Persona 기반 시안 상대 비교다. | 실제 사용자 실험·전환율 오인 방지 | market response/purchase probability | product, marketing domain, UX | comparison run/UI 용어 | claim/contract/UI | market response prediction | — |
| DEC-009 | Korean legal sources | ACCEPTED | P1 | P1 | 법률 검토는 한국 법령 MCP와 법제처 API를 사용한다. | 근거 추적 가능한 한국 법률 검토 | 모델 단독 생성, 일반 web | product, legal domain, AI boundary | MCP/API adapter와 provenance | source/error/availability | generic legal review | — |
| DEC-010 | Landing design/content split | ACCEPTED | P0 | P1 | Landing layout/design/common component는 유지하고 copy/workflow/demo/CTA는 교체한다. | 디자인 자산 재사용과 제품 사실 분리 | 전체 유지 또는 전체 교체 | product, UIUX, migration | 후속 frontend content 변경 | UI/content/accessibility | legacy Landing product copy | — |

Status는 ACCEPTED, DEFERRED, SUPERSEDED, REJECTED를 사용한다. 상세 implementation 선택은 [Open Decisions](../product/OPEN_DECISIONS.md)에 둔다.

## P2.1 open-decision resolutions

### DEC-011 — Initial FILE formats

- Source Decision: OD-001
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Product Owner
- Decision: 초기 FILE은 DOCX와 일반 텍스트를 지원하고 extensible allowlist로 제한한다. Spring이 파일을 수신·저장·검증·추출하며 AI Server에는 추출된 내용만 전달한다. PDF, XLSX, PPTX는 초기 범위에서 제외한다.
- Rationale: 기존 DOCX parser 재사용 가능성을 유지하되 DOCX 전용 Workflow 결합을 피하고 파일 보안과 Storage 소유권을 Spring에 유지한다.
- Alternatives: DOCX only; PDF/XLSX/PPTX를 포함한 common office subset; 확장자 무제한 수용.
- Affected Documents: [Product Scope](../product/PRODUCT_SCOPE.md), [Idea Model](../domain/IDEA_MODEL.md), [Public API Principles](../contracts/PUBLIC_API_PRINCIPLES.md), [Data and Storage Architecture](../architecture/DATA_AND_STORAGE_ARCHITECTURE.md)
- Downstream Code Impact: Spring upload allowlist, text extraction, IdeaSource 처리; AI Server에는 file/storage client를 추가하지 않는다.
- Test Impact: allowlist, content validation, DOCX/plain text extraction, excluded format, owner/storage integrity test.
- Follow-up Implementation Phase: P4
- Supersedes / Superseded By: OD-001 OPEN 상태 / —

### DEC-012 — Bounded Spring–AI transfer

- Source Decision: OD-002
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Architecture Owner
- Decision: bounded inline JSON을 기본으로 사용한다. Spring이 추출 text 크기 제한과 chunk 배열을 구성하고, 초과 입력은 `PAYLOAD_TOO_LARGE` 또는 chunked text contract로 처리한다. Storage/presigned URL과 임시 공유 Storage는 금지하며 streaming/binary protocol은 후속 확장으로 둔다.
- Rationale: 초기 계약을 단순하고 검증 가능하게 유지하면서 Spring 데이터 소유권과 AI Server Storage 접근 금지를 보장한다.
- Alternatives: streaming-first protocol; shared temporary channel; presigned GET/PUT; unbounded inline payload.
- Affected Documents: [Internal AI API Principles](../contracts/INTERNAL_AI_API_PRINCIPLES.md), [Spring WAS Boundary](../architecture/SPRING_WAS_BOUNDARY.md), [AI Server Boundary](../architecture/AI_SERVER_BOUNDARY.md), [Data and Storage Architecture](../architecture/DATA_AND_STORAGE_ARCHITECTURE.md)
- Downstream Code Impact: Spring payload builder/limit, AI request validation, chunk-aware task contract; 초기 binary transport 구현 없음.
- Test Impact: size boundary, chunk order/integrity, `PAYLOAD_TOO_LARGE`, URL/credential prohibition and timeout tests.
- Follow-up Implementation Phase: P3, P4; binary/streaming은 해당 후속 slice
- Supersedes / Superseded By: OD-002 OPEN 상태 / —

### DEC-013 — Workflow state and capability

- Source Decision: OD-003
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Domain Owner
- Decision: state + capability model을 채택한다. 기본 여정은 순차적이지만 Project stage, resource/run status, 사용자 gate와 AI 실행 capability를 분리한다. backtracking을 허용하고 upstream 변경 시 관련 downstream 결과를 `STALE`로 처리한다.
- Rationale: 단일 stage enum이 version 유효성, 실행 가능성, 사용자 결정을 모두 대신하지 않게 하고 수정 loop를 지원한다.
- Alternatives: strict sequential stage enum only; gate 없는 자유 접근; downstream 결과 자동 삭제.
- Affected Documents: [Project Workflow](../product/PROJECT_WORKFLOW.md), [Workflow State Model](../domain/WORKFLOW_STATE_MODEL.md), [Status and Error Contract](../contracts/STATUS_AND_ERROR_CONTRACT.md)
- Downstream Code Impact: capability evaluation, stale propagation, explicit user gates and separate run/resource status.
- Test Impact: transition, capability, backtracking, stale propagation, user gate and cross-owner tests.
- Follow-up Implementation Phase: P3–P10
- Supersedes / Superseded By: OD-003 OPEN 상태 / —

### DEC-014 — Quick and Detailed analysis inputs

- Source Decision: OD-004
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Product Owner
- Decision: shared core provenance/input snapshot과 analysis-specific input/output contract를 함께 사용한다. Quick는 모든 Concept 후보의 저비용 공통 평가이고 Detailed는 Shortlist 후보 전용이다. Quick 결과를 Detailed 사실로 자동 승격하지 않는다.
- Rationale: 공통 상류 근거를 재사용하면서 비용·깊이·산출물의 의미가 다른 두 분석을 분리한다.
- Alternatives: 하나의 분석 schema와 depth flag; Quick 결과를 Detailed 초기 사실로 승격; 완전 독립 input snapshot.
- Affected Documents: [Project Workflow](../product/PROJECT_WORKFLOW.md), [Analysis Model](../domain/ANALYSIS_MODEL.md), [Provenance Contract](../contracts/PROVENANCE_CONTRACT.md)
- Downstream Code Impact: shared snapshot reference와 Quick/Detailed별 command/result validator.
- Test Impact: 모든 후보 Quick 적용, shortlist-only Detailed, snapshot provenance, non-promotion and stale tests.
- Follow-up Implementation Phase: P5, P6
- Supersedes / Superseded By: OD-004 OPEN 상태 / —

### DEC-015 — Three-Layer Persona axes

- Source Decision: OD-005
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Product Owner
- Decision: Persona Card는 `Role and Context`, `Problem and Needs`, `Behavior and Decision` 세 layer를 사용한다. demographic label만으로 구성하지 않고 구매확률·시장점유율·실제 고객 통계로 표현하지 않으며 Persona별 독립 Interview에 연결한다.
- Rationale: concept에 대한 역할·문제·행동 관점을 분리하면서 합성 Persona가 실제 소비자 조사로 오인되는 것을 방지한다.
- Alternatives: demographic-first profile; fixed cluster attributes; 자유 형식 Persona; panel Persona discussion.
- Affected Documents: [Product Scope](../product/PRODUCT_SCOPE.md), [Persona and Interview Model](../domain/PERSONA_INTERVIEW_MODEL.md), [Project Workflow](../product/PROJECT_WORKFLOW.md)
- Downstream Code Impact: three-layer card contract와 Persona별 독립 interview input.
- Test Impact: three-layer completeness, prohibited claim terminology, independent interview isolation and provenance tests.
- Follow-up Implementation Phase: P7, P8
- Supersedes / Superseded By: OD-005 OPEN 상태 / —

### DEC-016 — Initial Final Report presentation and export

- Source Decision: OD-006
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Product Owner
- Decision: 초기 Final Report는 HTML view와 PDF export를 지원한다. RDB에는 versioned structured report snapshot, Object Storage에는 Spring이 생성·저장한 PDF를 둔다. Markdown export와 browser runtime 조립 결과는 초기 범위에서 제외한다.
- Rationale: 조회 가능한 표현과 휴대 가능한 export를 제공하되 persisted snapshot을 재현 가능한 source of truth로 유지한다.
- Alternatives: PDF only; HTML/Markdown/PDF 동시 export; browser runtime composition only.
- Affected Documents: [Product Scope](../product/PRODUCT_SCOPE.md), [Final Report Model](../domain/FINAL_REPORT_MODEL.md), [Public API Principles](../contracts/PUBLIC_API_PRINCIPLES.md), [Data and Storage Architecture](../architecture/DATA_AND_STORAGE_ARCHITECTURE.md)
- Downstream Code Impact: report snapshot/version API, server-side HTML view model and Spring-owned PDF generation/storage.
- Test Impact: current/previous snapshot, HTML view, deterministic PDF artifact, checksum/owner/provenance and Markdown exclusion tests.
- Follow-up Implementation Phase: P10
- Supersedes / Superseded By: OD-006 OPEN 상태 / —

### DEC-017 — TaskRun and TaskAttempt transaction boundary

- Source Decision: OD-007
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Platform Owner
- Decision: TaskRun과 TaskAttempt를 분리한다. TaskRun은 업무 요청과 현재 최종 상태, TaskAttempt는 개별 실행·retry·timeout·오류·응답을 소유한다. 외부 AI 호출 동안 DB transaction을 유지하지 않으며 claim/lease 또는 동등한 동시성 제어, idempotency key, input snapshot/hash를 보존한다. 계약은 polling과 event wake를 모두 수용하고 outbox 선택은 P3로 둔다.
- Rationale: 긴 외부 호출과 DB transaction을 분리하고 retry·network ambiguity·중복 채택을 추적 가능하게 관리한다.
- Alternatives: TaskRun 단일 aggregate transaction; 외부 호출 중 transaction 유지; outbox-only wake; polling-only contract.
- Affected Documents: [Workflow State Model](../domain/WORKFLOW_STATE_MODEL.md), [Internal AI API Principles](../contracts/INTERNAL_AI_API_PRINCIPLES.md), [Status and Error Contract](../contracts/STATUS_AND_ERROR_CONTRACT.md), [Spring WAS Boundary](../architecture/SPRING_WAS_BOUNDARY.md)
- Downstream Code Impact: separate run/attempt persistence, lease/claim, idempotent result adoption and input snapshot/hash.
- Test Impact: transaction boundary, concurrent claim, lease expiry, retry/timeout, duplicate response, polling/event-neutral contract tests.
- Follow-up Implementation Phase: P3
- Supersedes / Superseded By: OD-007 OPEN 상태 / —

### DEC-018 — AI provider implementation choice

- Source Decision: OD-008
- Decision Date: 2026-08-01
- Status: DEFERRED
- Introduced / Reviewed Phase: P1 / P2.1
- Accountable Owner: AI Platform Owner
- Decision: model/provider/SDK/library 선택을 각 provider-dependent implementation slice 진입 전으로 연기한다. P2 계약은 provider/model/SDK/library-neutral로 유지한다.
- Rationale: 공통 task/domain contract를 공급자 고유 타입에 결합하지 않고 slice별 품질·비용·latency·보안·운영 근거를 평가한다.
- Alternatives: P2에서 단일 provider/model 고정; 모든 provider를 동시에 지원; current Spring adapter를 Target으로 승격.
- Affected Documents: [Contract Overview](../contracts/CONTRACT_OVERVIEW.md), [Internal AI API Principles](../contracts/INTERNAL_AI_API_PRINCIPLES.md), [Analysis Model](../domain/ANALYSIS_MODEL.md), [AI Server Boundary](../architecture/AI_SERVER_BOUNDARY.md)
- Downstream Code Impact: P2 schema에 provider SDK type을 포함하지 않으며 해당 slice entry review에서 adapter/library를 선택한다.
- Test Impact: provider-neutral fixtures 우선; slice entry 시 benchmark, fallback, error normalization and contract conformance 추가.
- Follow-up Implementation Phase: P3–P10의 각 provider-dependent slice 진입 전
- Supersedes / Superseded By: OD-008 OPEN due milestone / —

### DEC-019 — Coordinated Korean legal source adapter

- Source Decision: OD-009
- Decision Date: 2026-08-01
- Status: ACCEPTED
- Introduced / Decided Phase: P1 / P2.1
- Accountable Owner: Legal Integration Owner
- Decision: AI Server가 법령 MCP와 법제처 API를 조정한다. 법제처 API는 법령 원문·식별자·현재성 확인의 authoritative source, MCP는 검색·탐색·연관 법령 발견에 사용한다. 한쪽 실패는 degraded result로 표시하며 출처·조회 시각·법령 식별자·조문·source channel과 `EXPERT_REVIEW_REQUIRED`를 지원한다. Secret은 환경변수로만 관리한다.
- Rationale: 공식 근거 확인과 탐색 기능을 구분하고 부분 장애, 출처와 비자문 한계를 결과에 보존한다.
- Alternatives: MCP primary/API fallback; API only; MCP only; model-only legal generation.
- Affected Documents: [Product Scope](../product/PRODUCT_SCOPE.md), [Legal Review Model](../domain/LEGAL_REVIEW_MODEL.md), [Provenance Contract](../contracts/PROVENANCE_CONTRACT.md), [AI Server Boundary](../architecture/AI_SERVER_BOUNDARY.md), [Security Architecture](../architecture/SECURITY_ARCHITECTURE.md)
- Downstream Code Impact: AI Server coordinated source adapters와 degraded outcome; Spring validation/persistence of legal provenance.
- Test Impact: authoritative source verification, MCP discovery, partial failure, provenance completeness, expert-review and secret/config tests.
- Follow-up Implementation Phase: P4
- Related / Superseded By: refines DEC-009 / —
