# Product Scope

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Target product boundaries and phase allocation
- Supersedes: Legacy feature inventory and product requirement documents
- Implementation Status: NOT_STARTED

## Project definition

Project는 IdeaSource 수집부터 FinalReportVersion까지 하나의 검증 계보를 소유하는 owner-scoped workspace다. 문서나 analysis job 하나가 Project를 대표하지 않는다. Project 안의 versioned 결과는 upstream version과 사용자 결정을 추적한다.

## In scope

- TEXT와 FILE logical type의 IdeaSource와 IdeaVersion. 질문 응답 UI 입력은 TEXT source로 수집하며, 초기 FILE은 DOCX와 일반 텍스트이고 extensible allowlist를 사용한다.
- Idea Normalization과 사용자 검토
- 법제처 API의 공식 근거 확인과 법령 MCP 탐색을 조정하고 degraded/전문가 검토 상태를 지원하는 LegalReviewRun
- 복수 concept 생성/version, Quick Assessment, shortlist
- 시장·BM·기술운영·재무 Detailed Analysis
- AI 권고와 분리된 ConceptSelection
- Role and Context, Problem and Needs, Behavior and Decision의 Three-Layer Persona Card와 독립 Persona Interview
- Marketing asset 생성·편집·version과 Persona 기반 상대 A/B 비교
- persisted FinalReportVersion의 current/previous HTML view와 초기 PDF export
- Stable Core auth/owner/audit/data/storage
- Target Admin과 범용 Service Policy

## Out of scope

- 완성 사업계획서 또는 고정 12개 section 필수화
- 기존 legacy data 보존·변환
- Persona 토론 또는 panel consensus
- 시장반응 예측, 구매확률, 실제 전환율 주장
- 실제 사용자 traffic을 사용하는 A/B experiment
- 법률 자문 또는 법적 결론 보장
- 초기 FILE의 PDF, XLSX, PPTX 지원
- 초기 Final Report Markdown export와 browser runtime 조립 report
- P2.1의 상세 field, DB table/column, 전체 JSON/API schema, prompt, model/library, 세부 UI 확정

## Phase allocation

| Phase | Scope |
|---|---|
| P2 | domain, state, provenance, public/internal contract |
| P3 | Stable Core regression, /api/v2, TaskRun foundation |
| P4 | Idea/Normalization/Korean Legal |
| P5 | Concept Builder/Quick Assessment |
| P6 | Shortlist/Detailed Analysis/Selection |
| P7–P8 | Persona cards/independent interviews |
| P9 | Marketing Workspace/comparison |
| P10 | persisted Final Report |
| P11 | Admin과 Landing content 전환 |
| P12 | legacy 제거와 database cutover |
| P13 | 통합 품질, 수동 테스트, release hardening |

초기 FILE, 전송, workflow gate, 분석, Persona, report, TaskRun과 법령 연동 방향은 [Open Decisions](OPEN_DECISIONS.md)와 [Decision Log](../governance/DECISION_LOG.md)에 확정했다. P2.2 logical domain schema는 이 경계를 반영하며, 상세 public/internal JSON contract와 fixture는 P2.3에서 이를 입력 조건으로 사용한다.
