# Concept Portfolio V2 KEEP / REWORK / REPLACE / DELETE Matrix

## 판정 기준

- KEEP: 현재 계약을 그대로 재사용한다.
- REWORK: 기반은 유지하고 CPV2 Product 계약이나 새 FK/API에 맞춘다.
- REPLACE: 공식 실행·화면 authority를 새 구현으로 대체한다.
- DELETE: caller 0과 migration/read-only history gate 확인 후 제거한다.

| path | current role | decision | target role | replacement/dependency | delete gate | notes |
|---|---|---|---|---|---|---|
| `ai/app/concept_portfolio_v2/**` | 검증된 V2 Core 알고리즘 | KEEP | Production facade가 호출하는 frozen authority | 없음 | 해당 없음 | P1 Core diff 없음; 알고리즘, prompt, selection/legal 정책 수정 금지 |
| `ai/app/tasks/concept_portfolio_v2/service.py` | Notebook과 같은 `run_full()` 호출 함수 | REWORK | thin facade, Product result/continuation export 진입점 | 신규 `models.py`, `observer.py` | 해당 없음 | P1 initial facade 완료; P4 task-layer Candidate-only continuation facade 추가 |
| `ai/app/api/executions.py` | 내부 AI task registry와 dispatcher | REWORK | CPV2 Run/Continuation/Selection Action 등록·호출 | facade result contract | 해당 없음 | P5+P6 단일 `CONCEPT_PORTFOLIO_V2_SELECTION_ACTION` strict dispatch까지 연결 완료 |
| `backend/.../pipeline/idea/**` | 13개 Idea field, confirmation, snapshot hash | KEEP | Confirmed Idea Brief canonical source | V2 seed adapter | 해당 없음 | 사용자 LOCK/provenance 재사용 |
| `backend/.../taskrun/domain/**`, `repository/**`, `service/TaskRunService.java` | durable task, claim, heartbeat, recovery, terminal history | KEEP | V2 Run/Continuation 공통 execution 기반 | 새 TaskType·worker | 해당 없음 | P4 새 continuation TaskRun/Job도 immutable terminal·claim·heartbeat·recovery 재사용 |
| `backend/.../taskrun/integration/InternalAiExecutionClient.java` | AI Server 동기 HTTP client | REWORK | V2 장기 실행에 안전한 전용 호출 정책 | configurable runtime/heartbeat 설계 | 해당 없음 | P4 initial/continuation 모두 V2 15분 long-read client 사용; 일반 30초 유지 |
| `backend/.../taskrun/domain/TaskType.java` | task type registry | REWORK | Portfolio initial/continuation/selection action 포함 | 공통 V2 worker 기반 | 해당 없음 | P5+P6 Selection 이후 5개 action은 하나의 TaskType/worker가 처리 |
| `backend/.../jobevent/**` | JobEvent 저장, sequence, job SSE/replay/poll | KEEP | job stream 유지 + project invalidation stream | `job_events.id` project cursor | 해당 없음 | Project stream은 terminal job 뒤에도 유지; REST state가 정본 |
| `backend/.../pipeline/concept/api/ConceptFactoryController.java` | Slot Factory 공식 API | REPLACE | `/concept-portfolio-runs` resource API | 새 `pipeline/conceptportfolio/api` | 새 Frontend caller 0 | P3 독립 공식 Run 생성·조회 API 완료; legacy history 조회만 adapter 가능 |
| `backend/.../pipeline/concept/application/ConceptFactoryService.java` | 5 Slot run 생성·retry·공개 | REPLACE | Portfolio Run service와 Product status mapper | 새 Portfolio persistence | controller/worker caller 0 | `COMPLETED` 전 concepts 빈 목록 규칙 폐기 |
| `backend/.../pipeline/concept/application/ConceptFactoryExecutionService.java` | Slot 상태와 attempt orchestration | REPLACE | V2 result materialization/continuation merge | V2 facade result | worker caller 0 | P4 독립 Candidate continuation 원자 merge·retry 구현; Spring 알고리즘 재구현 없음 |
| `backend/.../pipeline/concept/worker/ConceptFactoryWorker.java` | Slot별 Candidate/Legal worker | REPLACE | 단일 V2 durable run worker | long-run heartbeat/lease | V2 worker cutover | P4 initial/continuation 전용 worker 분리 완료; legacy task와 병렬 claim 없음 |
| `backend/.../pipeline/concept/worker/ConceptFactoryAiGateway.java` | 개별 legacy AI task 호출 | REPLACE | V2 full-run/continuation AI client | 새 task contracts | legacy worker caller 0 | business logic 복제 금지 |
| `backend/.../pipeline/concept/domain/ConceptFactoryLimits.java` | `SLOT_COUNT=5`와 bounded limits | DELETE | requested max는 Portfolio Run field | Amendment | caller 0 | max=5는 성공 gate가 아님 |
| `backend/.../pipeline/concept/domain/ConceptFactoryCompletionPolicy.java` | 정확히 5 Slot/Concept publish gate | DELETE | 1~5 결과 Product mapper | Portfolio materializer | caller 0 + 대체 tests | CPV2와 직접 충돌 |
| `backend/.../pipeline/concept/domain/ConceptSlot.java` 및 Slot repository | Slot 중심 persistence | DELETE | Candidate/lineage 중심 Portfolio Concept | 신규 tables + read-only legacy adapter | legacy data 조회 경로 확정 | 신규 Run 저장 금지 |
| `backend/.../pipeline/concept/domain/Concept.java` 및 repository | published legacy Concept | REPLACE | `ConceptPortfolioConcept` immutable result | P2 migration | selection/market FK 전환 | P2 V10 additive Portfolio resource 완료; 기존 데이터는 삭제하지 않음 |
| `backend/.../pipeline/legal/**` | assessment, official evidence, legal context | REWORK | Portfolio Concept Legal snapshot과 최종 Report 근거 | Portfolio concept adapter, report resource | 해당 없음 | Evidence 추출 기반은 재사용 |
| `backend/.../pipeline/selection/**` | legacy Concept selection/hypothesis authority | KEEP | 기존 Frontend 호환용 legacy history | P10 retirement | legacy caller 0 | V2 authority로 개조하지 않음; P5+P6 새 코드는 이 package/table을 호출하지 않음 |
| `backend/.../pipeline/conceptportfolio/selection/**` | P5+P6 신규 Portfolio selection 경계 | REWORK | 명시적 선택, 7 가정, Delta, Report, Market finalize authority | V12 + 단일 action worker | 해당 없음 | Engine default 비권위, active task/current selection/revision late-result guard 완료 |
| `backend/.../pipeline/marketseed/**` | immutable Market Seed materialization | REWORK | LEGACY/V2 source bridge와 stale history | Portfolio Concept/Report FK | legacy caller 유지 | V2는 canonical Core snapshot을 actual Product ID로 binding; fake legacy row 없음 |
| `backend/.../pipeline/module/ProjectModuleStatusService.java` | 프로젝트 module 상태 query | REWORK | Portfolio Product status와 InputRequest/Report 상태 반영 | 새 query services | 해당 없음 | current Run raw status 직접 매핑 금지 |
| `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`의 `task_runs`, `task_attempts`, `task_results`, `job_events` | async baseline | KEEP | 새 resources가 참조하는 공통 실행 이력 | additive migration | 해당 없음 | migration history 수정 금지 |
| 같은 migration의 `idea_briefs`, `idea_brief_fields` | confirmed seed snapshot | KEEP | V2 source snapshot | 신규 Portfolio Run FK | 해당 없음 | 기존 source/decisionState 보존 |
| 같은 migration의 `concept_factory_runs`, `concept_slots`, `concept_attempts`, `concepts` | legacy Slot persistence | REPLACE | Portfolio Run/Concept/Continuation/InputRequest tables | P2 additive migration | 새 caller 0 + legacy read policy | 과거 row 삭제 금지 |
| 같은 migration의 `concept_selections`, `concept_hypothesis_decisions` | legacy selection/hypothesis history | KEEP | 기존 legacy caller용 history | P10 retirement | legacy caller 0 | V2 row를 넣지 않으며 새 `concept_portfolio_*` tables가 별도 authority |
| 같은 migration의 `concept_legal_assessments`, `legal_evidence`, links | Concept legal 근거 | REWORK | immutable legal snapshots/report evidence | report tables | 해당 없음 | official evidence 유지 |
| 같은 migration의 `market_analysis_seed_snapshots` | Market canonical input | REWORK | LEGACY/V2 source invariant와 current Report FK | V12 additive bridge | legacy source adapter 유지 | 기존 history 유지; V2 legacy IDs NULL, Portfolio IDs/Report ID 필수 |
| `frontEnd/src/features/idea-intake/**` | Idea 입력·확인 UX | KEEP | CPV2 시작 전 Confirmed Brief UX | Portfolio start action | 해당 없음 | 필수 3개와 선택 field 구조 재사용 |
| `frontEnd/src/features/concept-factory/**` | Slot Workboard와 5개 reveal UI | REPLACE | Business Proposal Workspace | 새 Portfolio API/hooks/components | 새 route caller 전환 | 공식 `/concepts` caller 전환 완료; 파일 삭제는 Final Cutover Gate |
| `frontEnd/src/features/concept-factory/components/ConceptSlotCard.jsx` | Slot 상태 카드 | DELETE | 사업안 카드 | 신규 proposal card | caller 0 | 관리자 기술 상세에도 기본 재사용하지 않음 |
| `frontEnd/src/features/concept-factory/components/ConceptTimeline.jsx` | Slot별 기술 timeline | DELETE | 사용자 단계형 진행과 Job Center 상세 | trace stage mapper | caller 0 | Slot filter 제거 |
| `frontEnd/src/features/concept-selection/pages/ConceptComparisonPage.jsx` | 정확히 5개 비교·선택 화면 | REPLACE | Business Proposal Workspace compare mode | Portfolio query/API | 공식 route caller 0 | `/concepts/compare` URL만 호환 유지; 파일 삭제는 Final Cutover Gate |
| `frontEnd/src/features/concept-selection/model/conceptComparisonModel.js` | 비교 model, 최대 5 | REWORK | 최소 2·최대 3 비교 | `MAX_COMPARE_COUNT=3` | 해당 없음 | 임시 tray는 local state 가능 |
| `frontEnd/src/features/concept-selection/hooks/useConceptSelection.js` | session draft, selection/hypothesis/market query | REWORK | 서버 selection authority와 project live invalidation | Portfolio API/project events | 해당 없음 | session draft는 authority 아님 |
| `frontEnd/src/features/concept-selection/components/LegalDetailDialog.jsx` | 후보 Legal 상세 dialog | REPLACE | 독립 Legal Regulatory Report view | P6 report API/print CSS | report view 완료 | 후보 상세 보조로만 일부 추출 로직 재사용 가능 |
| `frontEnd/src/shared/async-events/**` | job SSE/replay/polling | KEEP | job event 공통 client | project event client 확장 | 해당 없음 | bounded fallback 유지 |
| `frontEnd/src/features/job-center/**` | 선택 job 하나의 상세/수동 refresh | REWORK | 프로젝트 전체 요약과 Bottom Drawer | project events + query invalidation | 해당 없음 | 선택 job 단일 추적 한계 해소 |
| `frontEnd/src/app/project-shell/ProjectLayout.jsx` | 큰 module sidebar와 중앙 Job Center | REWORK | 좁은 Journey Rail, 중앙 업무, 우측 Work Center | Project Event invalidation | 해당 없음 | Project-level 구독 1개와 drawer형 실제 JobEvent 상세 연결 완료 |
| `frontEnd/src/app/module-status/**` | mount/retry 기반 module 상태 | REWORK | project event 수신 후 canonical query 재조회 | project-level SSE | 해당 없음 | 수동 refresh는 오류 복구용만 유지 |
| `frontEnd/src/features/market-integration/**` | Market handoff/status UI | REWORK | current Market Seed/Report gate와 live refresh | P6/P7 APIs | 해당 없음 | Snapshot boundary 유지 |

## P0 결론

Cutover의 핵심은 frozen Python Core를 새 Product Integration 경계에 연결하고, legacy Slot persistence와 UI authority를 단계적으로 제거하는 것이다. 공통 async, ownership, immutable history, 명시적 selection, hypothesis/Delta Legal, Official Evidence와 Market Snapshot 기반은 보존한다.
