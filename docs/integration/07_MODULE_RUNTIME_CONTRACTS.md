# Session 5 Module Runtime Contracts

## 1. TaskType support map

`SUPPORTED`는 Backend enum, AI 등록/dispatch, worker 경계가 모두 확인된 경우에만 사용한다. Parent worker 내부 leaf 실행은 worker 이름을 명시했다.

| TASK_TYPE | BACKEND_ENUM | AI_REGISTERED | AI_DISPATCH | WORKER | JOB_LABEL | MODULE | 판정 |
|---|---|---|---|---|---|---|---|
| IDEA_ATTACHMENT_PARSE | YES | NO | NO | NO | 아이디어 첨부파일 분석 | Idea | ENUM_ONLY / NOT_SUPPORTED |
| IDEA_BRIEF_DERIVATION | YES | YES | YES | IdeaBriefDerivationWorker | 아이디어 정리 | Idea | SUPPORTED |
| CONCEPT_PORTFOLIO_V2_RUN | YES | YES | YES | ConceptPortfolioWorker | 사업안 검토 | CPV2 | SUPPORTED |
| CONCEPT_PORTFOLIO_V2_CONTINUE | YES | YES | YES | ConceptPortfolioContinuationWorker | 추가 사업정보 반영 | CPV2 | SUPPORTED |
| CONCEPT_PORTFOLIO_V2_SELECTION_ACTION | YES | YES | YES | ConceptPortfolioSelectionWorker | 사업안 선택 후 검토 | CPV2 | SUPPORTED |
| CONCEPT_FACTORY_RUN | YES | N/A | N/A | ConceptFactoryWorker | 사업안 생성 | legacy Concept Factory | ORCHESTRATOR_ONLY, 4-way 미지원 |
| CONCEPT_CANDIDATE | YES | YES | YES | ConceptFactoryWorker 내부 | 사업안 후보 생성 | Concept Factory | SUPPORTED leaf |
| CONCEPT_DISTINCTNESS_JUDGE | YES | YES | YES | ConceptFactoryWorker 내부 | 사업안 차별성 검토 | Concept Factory | SUPPORTED leaf |
| CONCEPT_LEGAL_REVIEW | YES | YES | YES | ConceptFactoryWorker 내부 | 사업안 법률 검토 | Concept Factory | SUPPORTED leaf |
| CONCEPT_REDESIGN | YES | YES | YES | ConceptFactoryWorker 내부 | 사업안 재설계 | Concept Factory | SUPPORTED leaf |
| CONCEPT_HYPOTHESIS_ALTERNATIVE | YES | YES | YES | ConceptSelectionActionWorker | 사업가설 대안 생성 | CPV2 Selection | SUPPORTED |
| CONCEPT_DELTA_LEGAL_REVIEW | YES | YES | YES | ConceptSelectionActionWorker | 사업가설 변경 법률 검토 | CPV2 Selection | SUPPORTED |
| TECH_OPS_PROPOSAL | YES | YES | YES | TechOpsProposalWorker | 기술·운영 분석 | TechOps | SUPPORTED |
| MARKET_RESEARCH | YES | YES | YES | MarketResearchWorker | 시장 조사 / 비즈니스 모델(subject별) | Market/BM | SUPPORTED |
| TWIN_STIMULUS_DRAFT | YES | YES | YES | TwinStimulusDraftWorker | Twin 비교안 초안 | Twin | SUPPORTED |
| TWIN_SURVEY | YES | YES | YES | TwinSurveyWorker | Twin 조사 | Twin | SUPPORTED |
| FINANCE_ESTIMATE | YES | YES | YES | FinanceEstimateWorker | 재무 입력 AI 추정 | Finance | SUPPORTED |
| FINANCE_ANALYSIS_REPORT | YES | YES | YES | FinanceAnalysisWorker | 재무 분석 보고서 | Finance | SUPPORTED |
| MARKETING_CONTENT_GENERATION | YES | YES | YES | MarketingContentWorker | 마케팅 콘텐츠 준비 | Marketing | SUPPORTED |
| MARKETING_VISUAL_GENERATION | YES | YES | YES | MarketingVisualWorker | 마케팅 이미지 생성 | Marketing | SUPPORTED |

Backend→AI는 모두 `/internal/v1/ai/executions`와 `AI_INTERNAL_SERVICE_TOKEN` Bearer 경계를 사용한다. CPV2 progress callback도 동일 service-token boundary이며 사용자 JWT와 혼용하지 않는다.

## 2. 모듈별 runtime 계약

| 모듈 | upstream/readiness | Product API 및 TaskType | canonical result / stale | retry/failure/event/artifact |
|---|---|---|---|
| Idea | Project ownership, Idea 입력 | Idea API; `IDEA_BRIEF_DERIVATION` | current confirmed IdeaBrief snapshot | 새 TaskRun retry, 안전한 질문/실패, JobEvent |
| CPV2 | confirmed Idea snapshot | CPV2 run/continue/selection APIs | current portfolio run/selection/Market Seed; source mismatch STALE | TaskRun history, selection event; CPV2 알고리즘 frozen |
| Market | READY_FOR_MARKET selection + current Market Seed | Market start/current; `MARKET_RESEARCH` subject FULL | MarketResearchVersion; seed mismatch STALE | partial/missing/caveat 보존, dependency failure 안전화, Job/Project event |
| BM | current MarketResearchVersion + BM plan 4 cells/budget/duration/team | BM plan/run/current; `MARKET_RESEARCH` subject BM | BM version tied to Market version + plan revision | MarketJoin evidence/grade/caveat/assumption 유지, 새 TaskRun retry |
| Twin | current selection/seed, stimulus pairs, sample 50/100/300, external Bank | draft/survey APIs; `TWIN_STIMULUS_DRAFT`, `TWIN_SURVEY` | TwinSurveyVersion; seed mismatch STALE; draft만으로 완료 금지 | Bank unavailable=실패, not-measurable=정직한 완료 결과, JobEvent |
| TechOps | current Market Seed 및 필요한 사실/결정 | Target TechOps APIs; `TECH_OPS_PROPOSAL` | TechOps preparation/snapshot; source mismatch STALE | Target retry/event/evidence artifact authority 유지 |
| Finance | exact TechOps snapshot + Market version + BM version | initialize/patch/finalize/analyze/current; `FINANCE_ESTIMATE`, `FINANCE_ANALYSIS_REPORT` | immutable Financial snapshot/analysis; 3-source mismatch STALE | estimate/history, provider report fallback, deterministic 계산은 별도 authority |
| Marketing Content | current Marketing Source + legal controls | content generate/edit/finalize; `MARKETING_CONTENT_GENERATION` | Content/Revision current; source mismatch STALE | legal block/warn, revision history, copy/download |
| Marketing Visual | Content revision + source/legal + optional owned image Artifact | visual start/current/artifact; `MARKETING_VISUAL_GENERATION` | TaskResult metadata + Project Artifact | 생성/합성/저장 단계 실패 구분; Artifact 저장 실패는 Task 실패; Content 완료 상태 보존 |

## 3. 상태 및 live refresh

- 공통 표현: `NOT_READY`, `READY`, `QUEUED`, `RUNNING`, `NEEDS_INPUT`, `FAILED`, `COMPLETED`, 필요 시 `STALE`/`NOT_CONNECTED`.
- Market은 selection/seed/run/version, BM은 Market version/plan/run/version, Twin은 survey run/version, Finance는 preparation/snapshot/report, Marketing은 Content와 Visual Task를 각 current authority에서 계산한다.
- Worker는 실제 단계에서만 JobEvent를 기록한다. 가짜 percentage는 없다.
- Job/Project SSE는 cursor로 재연결하며 REST polling fallback은 없다. 이벤트 뒤 화면은 canonical GET을 다시 호출한다.
- Work Center의 실패 정보는 safe reason, 실패 시각, retryable/action만 노출하며 raw provider body/stack/secret은 노출하지 않는다.

## 4. Artifact 계약

- Market evidence 및 Marketing Visual은 project ownership을 통과한 Artifact API를 사용한다.
- Visual 성공은 image bytes 생성만으로 성립하지 않는다. MinIO object와 canonical Artifact metadata가 저장된 뒤 Task가 성공한다.
- download/open은 Backend ownership 경계를 거치며 MinIO internal URL을 직접 노출하지 않는다.
- AI local file은 temporary일 뿐 current authority가 아니다.

## 5. CUTOVER-R1 subordinate task overlay

- Twin의 기본 authority는 current seed와 canonical survey run이다. 활성 `TWIN_SURVEY`가 최우선이고, survey가 활성 상태가 아닐 때만 `TWIN_STIMULUS_DRAFT`의 QUEUED/RUNNING을 Journey에 overlay한다. Draft 실패·취소·timeout은 기존 READY/상태를 FAILED로 덮지 않는다.
- Finance의 Preparation/Snapshot/Analysis authority는 그대로 유지한다. 활성 `FINANCE_ANALYSIS_REPORT`가 최우선이며, 없을 때 current preparation의 활성 `FINANCE_ESTIMATE`만 QUEUED/RUNNING으로 overlay한다. Estimate 실패는 기존 READY/NEEDS_INPUT을 유지한다.
- Marketing Visual 실패가 완료된 Marketing Content를 FAILED로 덮지 않는 기존 규칙을 유지한다.
- `activeTaskRunId`는 실제 우선순위에 따라 선택된 활성 subordinate task만 가리킨다.

Marketing Visual worker progress는 `INPUT_VALIDATING → VISUAL_GENERATING → RESULT_STORING → COMPLETED`의 coarse truthful 경계다. copy/image/composition 세부 callback은 Visual 구현 안정화 뒤 generic safe progress에 연결하는 후속 seam으로 남긴다.
