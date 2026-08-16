# Open Decisions

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Governance for P2 due decisions and later implementation-slice decisions
- Supersedes: Open questions dispersed across legacy documents
- Implementation Status: PARTIAL

| Decision ID | Accountable owner | Consulted owners | Due milestone | Status | Introduced Phase | Decision topic | Options | Required evidence | Affected documents | Downstream implementation Phase | Impact scope |
|---|---|---|---|---|---|---|---|---|---|---|---|
| OD-001 | Product Owner | Backend Owner, API Contract Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | 초기 FILE 지원 형식 | DOCX only; common office subset; extensible allowlist | parser 재사용성, upload 보안·크기 제한, UX와 테스트 비용 비교 | [Idea Model](../domain/IDEA_MODEL.md), [Public API Principles](../contracts/PUBLIC_API_PRINCIPLES.md), [Data and Storage](../architecture/DATA_AND_STORAGE_ARCHITECTURE.md) | P4 | parser, upload, UX, tests |
| OD-002 | Architecture Owner | Spring WAS Owner, AI Platform Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | 대용량 Spring–AI 전송 계약 방향 | bounded inline; chunk/stream; Spring-mediated temporary channel | payload 크기·timeout·memory 분석, binary 흐름과 금지 통신 준수 검토 | [Internal AI API Principles](../contracts/INTERNAL_AI_API_PRINCIPLES.md), [Spring WAS Boundary](../architecture/SPRING_WAS_BOUNDARY.md), [AI Server Boundary](../architecture/AI_SERVER_BOUNDARY.md) | P3, P4, P9 | internal API, memory, timeout, binary AI |
| OD-003 | Domain Owner | Product Owner, API Contract Owner, UX Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | Workflow 상태와 gate | strict sequential; optional gates; state + capability model | 사용자 결정·되돌아가기·stale 시나리오와 상태 전이 검토 | [Workflow State Model](../domain/WORKFLOW_STATE_MODEL.md), [Project Workflow](PROJECT_WORKFLOW.md), [Status and Error Contract](../contracts/STATUS_AND_ERROR_CONTRACT.md) | P3–P10 | domain, API, UI, stale |
| OD-004 | Product Owner | Domain Owner, AI Contract Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | Concept Quick/Detailed 분석 입력 | shared core + depth; analysis-specific inputs | 단계 목적·비용·provenance·재실행/stale 비교와 example fixture | [Analysis Model](../domain/ANALYSIS_MODEL.md), [Concept Model](../domain/CONCEPT_MODEL.md), [Provenance Contract](../contracts/PROVENANCE_CONTRACT.md) | P5, P6 | domain, AI contract, provenance |
| OD-005 | Product Owner | Persona Domain Owner, UX Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | Persona Three-Layer 상세 축 | needs/context/behavior 계열 후보; 다른 reviewed taxonomy | 축의 중복·설명 가능성·interview 독립성 검토와 representative fixture | [Persona Interview Model](../domain/PERSONA_INTERVIEW_MODEL.md), [Workflow UX](../uiux/WORKFLOW_UX.md) | P7, P8 | domain, UI, interview |
| OD-006 | Product Owner | Report Domain Owner, Storage Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | Final Report 초기 export | PDF; Markdown; HTML; staged subset | 사용자 view/export 요구, renderer 운영비용, Storage 무결성·보안 비교 | [Final Report Model](../domain/FINAL_REPORT_MODEL.md), [Data and Storage](../architecture/DATA_AND_STORAGE_ARCHITECTURE.md), [Product Scope](PRODUCT_SCOPE.md) | P10 | renderer, Storage, UI |
| OD-007 | Platform Owner | Backend Transaction Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | TaskRun transaction과 attempt 경계 | single aggregate; separated attempt transaction; outbox/event wake | 동시성·retry·timeout·idempotency·감사 시나리오와 transaction sequence 검토 | [Workflow State Model](../domain/WORKFLOW_STATE_MODEL.md), [Internal AI API Principles](../contracts/INTERNAL_AI_API_PRINCIPLES.md), [System Architecture](../architecture/SYSTEM_ARCHITECTURE.md) | P3 | DB, concurrency, retry |
| OD-008 | AI Platform Owner | — | 각 provider-dependent implementation slice 진입 전 | DEFERRED | P1 | AI model/provider/library | provider abstraction 후보; model per task | 품질·비용·latency·보안·운영성 benchmark와 fallback 검토 | [Internal AI API Principles](../contracts/INTERNAL_AI_API_PRINCIPLES.md), [Analysis Model](../domain/ANALYSIS_MODEL.md), [Contract Overview](../contracts/CONTRACT_OVERVIEW.md) | P3–P10의 해당 AI slice | AI Server, cost, quality tests |
| OD-009 | Legal Integration Owner | Architecture Owner | P2 completion — resolved P2.1 | ACCEPTED | P1 | 법령 MCP·법제처 API 연동 방식 | MCP primary/API fallback; API primary/MCP enrichment; coordinated source adapter | 출처·최신성·가용성·오류/수정 loop·법률 UX 검토 | [Legal Review Model](../domain/LEGAL_REVIEW_MODEL.md), [Provenance Contract](../contracts/PROVENANCE_CONTRACT.md), [AI Server Boundary](../architecture/AI_SERVER_BOUNDARY.md) | P4 | provenance, availability, legal UX |

## Resolution summary

| Decision ID | Adopted decision | Rationale |
|---|---|---|
| OD-001 | 초기 FILE은 DOCX와 일반 텍스트를 지원하고 extensible allowlist로 제한한다. Spring이 수신·저장·검증·추출하며 AI Server에는 추출된 내용만 전달한다. PDF, XLSX, PPTX는 초기 범위에서 제외한다. | 기존 DOCX parser 재사용 가능성을 보존하면서 Workflow를 DOCX에 결합하지 않고, 파일 보안과 Storage 소유권을 Spring에 유지한다. |
| OD-002 | Spring–AI 기본 전송은 bounded inline JSON이다. Spring이 추출 text의 크기 제한과 chunk 배열을 구성하며 초과 시 `PAYLOAD_TOO_LARGE` 또는 chunked text contract를 사용한다. Storage/presigned URL과 임시 공유 Storage는 금지하고 streaming/binary protocol은 후속 확장으로 둔다. | 단순하고 검증 가능한 초기 계약을 제공하면서 AI Server의 Storage 접근 금지와 Spring의 데이터 소유권을 지킨다. |
| OD-003 | Workflow는 state + capability model을 사용한다. Project stage, resource/run status, 사용자 선택·확정 gate와 AI 실행 capability를 분리하고 backtracking 및 upstream 변경에 따른 downstream `STALE`을 지원한다. | 순차 기본 여정을 유지하면서 단일 stage enum이 실제 접근 가능성과 version 유효성을 과도하게 대표하지 않게 한다. |
| OD-004 | Quick/Detailed는 shared core provenance와 input snapshot을 재사용하되 analysis-specific input/output contract를 분리한다. Quick는 모든 후보의 저비용 평가, Detailed는 shortlist 전용이며 Quick 결과를 Detailed 사실로 자동 승격하지 않는다. | 비용과 깊이가 다른 두 분석을 구분하고 동일 상류 입력에 대한 추적 가능성을 유지한다. |
| OD-005 | Persona Card의 세 layer는 `Role and Context`, `Problem and Needs`, `Behavior and Decision`이다. demographic label만으로 구성하지 않으며 구매확률·시장점유율·실제 고객 통계로 표현하지 않고 Persona별 독립 Interview에 연결한다. | concept 맥락, 문제, 행동·의사결정 근거를 분리하면서 합성 Persona를 실제 조사 결과로 오인하는 것을 방지한다. |
| OD-006 | 초기 Final Report는 HTML view와 PDF export를 지원한다. RDB에는 versioned structured snapshot, Object Storage에는 Spring이 생성·저장한 PDF를 둔다. Markdown export와 browser runtime 조립 report는 초기 범위에서 제외한다. | 조회성과 휴대 가능한 export를 제공하면서 재현 가능한 persisted snapshot을 보고서 source of truth로 유지한다. |
| OD-007 | TaskRun과 TaskAttempt를 분리한다. TaskRun은 업무 요청과 현재 최종 상태, TaskAttempt는 개별 실행·retry·timeout·오류·응답을 소유한다. 외부 호출 중 DB transaction을 유지하지 않고 claim/lease 또는 동등한 동시성 제어, idempotency key, input snapshot/hash를 사용한다. polling과 event wake를 모두 수용하며 outbox 선택은 P3로 둔다. | 긴 외부 호출을 DB transaction과 분리하고 중복 실행·network ambiguity·retry 이력을 명시적으로 관리한다. |
| OD-008 | AI model/provider/library 결정은 각 provider-dependent implementation slice 진입 전으로 연기한다. P2 계약은 provider/model/SDK/library-neutral로 유지한다. | task contract를 특정 공급자에 고정하지 않은 상태에서 slice별 품질·비용·latency·운영성 근거를 평가하기 위함이다. |
| OD-009 | AI Server의 coordinated source adapter가 법령 MCP와 법제처 API를 조정한다. 법제처 API는 원문·식별자·현재성 확인의 authoritative source, MCP는 검색·탐색·연관 법령 발견에 사용한다. 한쪽 실패는 degraded result로 표시하고 출처·조회 시각·법령 식별자·조문·source channel을 반환하며 `EXPERT_REVIEW_REQUIRED`와 환경변수 기반 secret을 지원한다. | 공식 근거 확인과 탐색 기능을 구분하고 부분 장애·출처·비자문 한계를 추적 가능하게 한다. |

## Governance

결정은 due milestone 전에 ACCEPTED, REJECTED 또는 DEFERRED로 갱신하고 [Decision Log](../governance/DECISION_LOG.md)에 기록한다. 상세 schema를 결론 전에 canonical 사실로 서술하지 않는다. 이전 Phase 결정을 변경하는 경우에는 [Change Impact Ledger](../governance/CHANGE_IMPACT_LEDGER.md)에 새 CHG ID를 기록한다. 단순한 OPEN 항목의 최초 확정은 기존 확정 결정을 뒤집는 변경이 아니므로 ledger 대상이 아니다.

OD-008의 DEFERRED는 미결정 방치가 아니라 due milestone 변경이다. 각 provider-dependent implementation slice 진입 전에 근거를 검토해 결정하며, 그동안 P2의 public/internal AI, provenance, analysis 계약은 특정 provider·model·SDK·library의 고유 타입이나 동작을 전제로 하지 않는 provider-neutral contract여야 한다.
