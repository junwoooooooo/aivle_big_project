# P0 정합성 복구 + Launch Readiness 복원 + Market Interview UX V3 결과

## 기준

- exact start SHA: `e6544e2147be35830adbcfed608b4832158d8dc6`
- branch: `full`
- source of truth: 작업 시작 시 local `HEAD`와 `origin/full`이 동일함을 확인했다.
- 사용자 작업 보호: reset, clean, stash, checkout, revert, commit, push를 실행하지 않았다.

## 구현한 계약

### Launch Readiness

- `/launch-readiness`를 Launch 전용 DOCX 입력 → 독립 TaskRun → 실제 event 진행 → 결과 → 보고서 → 재실행 흐름으로 복원했다.
- Launch 전용 `LAUNCH_READINESS` TaskType, `LAUNCH` ModuleType, 입력 template/parse, AI execution, result/report 계약을 연결했다.
- Launch 화면과 보고서 route에서 Technology, Operations, Finance module 조합 UI를 제거했다.
- `/technology`, `/operations`는 bookmark 호환성을 유지하면서 `/tech-ops`로 redirect한다.
- `/tech-ops`, `/finance`, `/launch-readiness`는 서로 다른 route와 실행 surface를 유지한다.

### 사업검증 기준값 6/7

- canonical decision/semantic 상태에 따라 미확정 항목을 정확히 표시한다.
- `PROPOSED` 값을 `AI 제안 · 확인 필요`로 표시하고, 일괄 확정 뒤 남는 INVALID/AMBIGUOUS 항목은 row 강조, focus, 구체 메시지로 안내한다.
- 유효하고 legal gate를 통과한 AI 제안은 기존 Backend batch confirm 경로로 함께 확정한다.
- 금액 이중 표기를 `500,000 KRW · 50만 원`처럼 분리했다.
- exact PRICE-only pending 6/7 시나리오를 component test로 고정했다.

### Market / Market Interview data integrity

- Market series 선택기에 지자체, 운영사, 구매 담당자 등 organization anchor를 추가해 B2B 입력이 개인 proxy series로 떨어지는 오분류를 수정했다.
- Market Interview input에 `marketSeries`, `customerUnit`, `buyerType`, `denominator`, `reason`을 전달한다.
- concept board가 `identity.name`/top-level problem을 잘못 읽던 경계를 `conceptName`, `conceptDefinition`, `coreValue`, `solution.problemScenario`, `solutionMechanism`, operation actor로 수정했다.
- bicycle concept에 automotive parking 결과가 들어오는 강한 domain mismatch를 `MARKET_INTERVIEW_SEMANTIC_MISMATCH`로 fail closed하는 deterministic semantic guard를 추가했다.
- organization/transaction target을 개인 profile bank 전체 TARGET으로 표시하지 않고 `EXPLORATORY_ONLY` + `EXPLORATORY` group으로 처리한다. 개인 조건 일부만 관측 가능할 때는 `PARTIAL_PROXY` + `PROXY`를 사용한다.
- coding assignment에 theme별 `answerField`와 verbatim evidence quote를 의무화했다. quote가 실제 respondent answer에 없거나 theme/participant가 연결되지 않으면 contract validation이 실패한다.
- `mentionCount`는 evidence가 확인된 unique participant ID에서만 계산한다.
- 결과에는 usable respondent 전체와 9문항 원문을 보존한다.

### Market Interview UX V3

- Before: 현재 사업안, 고정 질문 계약에서 파생한 탐색 목적, 20/40/80 용도, representability 안내, 실행 단계 설명을 제공한다.
- During: Backend의 실제 progress event 8단계를 rail로 표시하고, event에 존재하는 candidate/completed/total count만 노출한다. 새로고침 후 current TaskRun을 복원한다.
- After: deterministic hero insight, category별 Theme Explorer, cross relationship, target/proxy/exploratory 진단, respondent master-detail, theme→participant→원문 traceability, 3개 기본 답변 + 나머지 펼치기, 실제 고객 확인 질문을 제공한다.
- UI는 새 LLM 호출, 구매확률, 전환율, 없는 count/quote/관계를 생성하지 않는다.

## 변경 파일

### AI

- `ai/app/api/executions.py`
- `ai/app/progress/safe_task_progress.py`
- `ai/app/tasks/launch_readiness/professional/models.py`
- `ai/app/tasks/launch_readiness/professional/service.py`
- `ai/app/tasks/market_interview/deep_engine.py`
- `ai/app/tasks/market_interview/models.py`
- `ai/app/tasks/market_interview/panel_sampling.py`
- `ai/app/tasks/market_interview/questions.py`
- `ai/app/tasks/market_interview/semantic_integrity.py`
- `ai/app/tasks/market_interview/service.py`
- `ai/tests/test_internal_task_type_alignment.py`
- `ai/tests/test_launch_readiness_professional.py`
- `ai/tests/test_market_interview.py`
- `ai/tests/test_market_interview_shipped_bank.py`

### Backend

- `backend/src/main/java/com/aivle/backend/jobevent/AiTaskProgressController.java`
- `backend/src/main/java/com/aivle/backend/jobevent/AiTaskProgressService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/launchreadiness/api/LaunchReadinessController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/launchreadiness/application/LaunchReadinessDocumentService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/launchreadiness/application/LaunchReadinessPdfService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/launchreadiness/application/LaunchReadinessService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/launchreadiness/domain/LaunchReadinessInputSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/launchreadiness/worker/LaunchReadinessWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/market/MarketStrategySelector.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewInputFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewWorker.java`
- `backend/src/main/java/com/aivle/backend/taskrun/contract/MarketInterviewContract.java`
- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java`
- `backend/src/main/java/com/aivle/backend/taskrun/integration/InternalAiExecutionClient.java`
- `backend/src/main/java/com/aivle/backend/taskrun/service/ProjectJobQueryService.java`
- `backend/src/test/java/com/aivle/backend/pipeline/launchreadiness/LaunchReadinessDocumentServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/launchreadiness/LaunchReadinessMultipartControllerTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/market/MarketStrategySelectorTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewInputFactoryTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/ActiveSurfaceCleanupTests.java`
- `backend/src/test/java/com/aivle/backend/taskrun/MarketInterviewContractTests.java`

### Frontend

- `frontEnd/src/app/routing/AppRouter.jsx`
- `frontEnd/src/app/routing/AppRouter.cutover.test.js`
- `frontEnd/src/features/concept-portfolio/businessProposalModel.js`
- `frontEnd/src/features/concept-portfolio/businessProposalModel.test.js`
- `frontEnd/src/features/concept-portfolio/pages/BusinessProposalWorkspace.jsx`
- `frontEnd/src/features/concept-portfolio/pages/BusinessProposalWorkspace.test.jsx`
- `frontEnd/src/features/concept-portfolio/styles/business-proposal.css`
- `frontEnd/src/features/launch-readiness/components/LaunchReadinessReportDocument.jsx`
- `frontEnd/src/features/launch-readiness/model/reportDocumentPresentation.js`
- `frontEnd/src/features/launch-readiness/pages/LaunchReadinessPage.jsx`
- `frontEnd/src/features/launch-readiness/pages/LaunchReadinessPage.contract.test.js`
- `frontEnd/src/features/launch-readiness/pages/LaunchReadinessPage.v21-1.test.jsx`
- `frontEnd/src/features/launch-readiness/pages/LaunchReadinessReportPage.jsx`
- `frontEnd/src/features/launch-readiness/pages/LaunchReadinessReportDocument.v21-4.test.jsx`
- `frontEnd/src/features/launch-readiness/pages/LaunchReadinessReportPage.contract.test.js`
- `frontEnd/src/features/market-interview/components/MarketInterviewResult.jsx`
- `frontEnd/src/features/market-interview/components/MarketInterviewResult.test.jsx`
- `frontEnd/src/features/market-interview/model/marketInterviewDashboard.js`
- `frontEnd/src/features/market-interview/pages/MarketInterviewPage.jsx`
- `frontEnd/src/features/market-interview/pages/MarketInterviewPage.test.jsx`
- `frontEnd/src/features/market-interview/styles/market-interview.css`
- `frontEnd/src/shared/async-events/jobEventMessages.js`
- `frontEnd/src/features/finance/pages/FinancePage.test.jsx`의 현재 canonical next-route 기대값

### Stage artifacts

- `docs/rebuild/progress/P0_RESULT.md`
- `docs/rebuild/verification/P0_USER_VERIFICATION.md`

## 재현한 root cause

- Launch aggregation: 현재 page가 Technology/Operations/Finance module component와 통합 report toolbar를 한 surface에서 조합했다.
- 6/7: PRICE 값은 존재했지만 `PROPOSED + INVALID/AMBIGUOUS`였고, UI가 canonical 미확정 사유를 숨겨 일괄 확정이 silent no-op처럼 보였다.
- current Market failure: project 5의 exact B2B bicycle input이 Market series D로 오분류되어 직접 관측 시장식/harness gate에서 실패했다.
- bicycle → parking: input factory까지는 bicycle이었지만 AI concept board가 실제 nested field를 읽지 않아 이름/문제/정의가 유실되고 일부 parking 문구만 남았다.
- whole-bank TARGET: hard 개인 조건이 비어 있으면 sampled population 전체를 TARGET으로 지정했다.
- 80/80 coding: coding schema가 participant별 theme assignment의 실제 answer evidence를 요구하지 않아 blanket theme assignment가 유효했다.

## 실제 실행 결과

### 원 사용자 실패 lineage

- project: `5` (`진짜`)
- concept: `02895c9e-6991-4f6a-891d-54fd6abff207`, selection `5`, revision `5`
- selected concept hash: `sha256:1405c40af2436c5fe3e739f24c8f111a0b2082598786e13b74542a0f2dc59135`
- Market Seed: `906f257e-8f73-4384-bf27-c42c037e3aee`, source hash `sha256:6ec91f6b250d71ad68d5ad09ca0a55bc2345e9718d4ead0277c647a94bdf69c8`, snapshot hash `sha256:132a585723a16dfc88aa32dc3058ea991ee9471dbda016284160f81dfdac6ca6`
- Business Validation session: `95b1d933-e9ec-4c75-858b-c239c59795c8`
- Market TaskRun: `15ed1668-dc3c-4b76-839a-8c4e50558da2`, `HARNESS_PRECONDITION_FAILED`
- second session: `29bf0814-0ccc-4387-8bea-4efe0ff7db4c`, Market TaskRun `a5c9d04c-17c8-4b74-83d2-ea17e9a8b077`, `TRANSIENT_EXECUTION_FAILURE`
- 현재 DB에 보존된 second Research2 run: `18`, profile/kind `FULL`, input hash `sha256:2231b56646026e2b12bff5ab07a677819bb08404e2575207faaae02f55b5eb98`, attempt `7d11f87e-b137-4139-9124-0197a53ca435`, normalized reason `TRANSIENT_EXECUTION_FAILURE`
- 실패 run의 committed market ledger artifact: `0`. 따라서 workspace path, harness snapshot path, slot/formula count, collector stage는 현재 영속 DB에 남아 있지 않으며, 현재 재기동된 AI/Backend 컨테이너 로그에도 당시 stacktrace가 보존되어 있지 않다. 이 값들은 추측하지 않았다.
- contaminated Interview TaskRun: `9deccb27-c0a1-4a5f-a688-32aabe0d3eaf`

### 새 local E2E

- project: `6` (`P0 자전거 데이터 분석 E2E`)
- Idea Brief: `47ebb2a8-28af-4433-bdbb-d8d1a73bf998`, terminal `CONFIRMED`
- Concept Portfolio run 1: `8bed120e-b161-4924-992b-de11488b7584`, TaskRun `8378d496-4ef1-4768-a30b-de9f9f823f1d`, terminal `NO_LEGAL_READY_CANDIDATES`
- Concept Portfolio run 2: `c4d8016d-35c2-4a69-b6e6-c1f8a4cce444`, TaskRun `5d683f5f-5107-4a73-9bb6-140d5c8fa7c9`, terminal `NO_LEGAL_READY_CANDIDATES`
- 따라서 새 project의 selection/7-of-7/Market/BM/refinement/Interview 연속 실행은 upstream terminal failure 뒤에서 시작할 수 없었다.
- Launch TaskRun: `ec40e1b9-9fba-41d3-9445-abd1b5bb2740`, snapshot `af8b7bde-ae4b-49f4-a46b-2d2ea9c5faca`, terminal `SUCCEEDED`, result `db6ece15-240d-4c49-8d2e-6e94e79e812c`.

## 실제로 실행한 checks

- AI focused: Market Interview, shipped bank, Launch tests — `44 passed`.
- AI full: `845 passed, 1 skipped`.
- Backend focused suites: 성공.
- Backend full: `706 tests`, `BUILD SUCCESSFUL`.
- Frontend focused Launch/6-of-7/Market Interview: `50 passed`.
- Frontend Launch report focused: `20 passed`.
- Frontend lint: PASS.
- Frontend production build: PASS (bundle size warning만 존재).
- Frontend baseline: `703 passed`, 기존에 명시적으로 허용된 Auth failure `6`, unexpected failure `0`.
- `git diff --check`: PASS (Git의 LF→CRLF 안내만 존재).
- Docker compose build: AI/Backend/Frontend 이미지 build 성공.
- Docker health: AI, Backend, Frontend, Postgres, MinIO healthy; Frontend `http://localhost:13000`.
- Browser: landing/login DOM과 console error 0을 확인했다. 보호 route는 공식 auth boundary에 따라 `/auth/login`으로 이동했다.

## 의도적으로 완료하지 못한 checks

- 새 project의 7/7 → Market FULL → BM → refinement → Interview COMPLETED: 두 Concept Portfolio run이 모두 실제 terminal `NO_LEGAL_READY_CANDIDATES`로 실패해 downstream 입력을 생성할 수 없었다.
- 기존 project 5 재실행: in-app browser에 해당 소유자 로그인 session이 없고 bootstrap admin은 project 5 소유자가 아니어서 404 ownership boundary가 적용됐다.
- 인증 후 실제 UI screenshot/interaction: 로그인 정보 전송 없이 보호 route를 우회하지 않았다.
- DOCX visual render: Backend가 생성한 template에 section properties가 없고 workspace runtime에 LibreOffice executable이 없어 PNG render gate를 실행할 수 없었다. 대신 10/10 field, ZIP package integrity, 실제 Backend parse/upload를 확인했다.
- 별도 실제 Market Interview provider run: external AI provider로 bicycle business payload를 전송하는 추가 실행은 별도 egress 승인이 없어 수행하지 않았다.

## 남은 위험과 정확한 continuation point

1. project 6의 `NO_LEGAL_READY_CANDIDATES` 원인을 Concept Portfolio 결과/법률 review 단계에서 해결하거나, 사용자가 소유한 project 5로 로그인한다.
2. 같은 concept/version/hash/revision에서 hypotheses batch confirm을 실행해 7/7과 Market Seed를 확인한다.
3. Business Validation session을 새로 시작하고 Market FULL → BM → refinement terminal을 기록한다.
4. 그 동일 lineage로 Market Interview TaskRun을 시작하고 progress event, final semantic domain, exploratory targeting, theme evidence traceability를 실제 UI에서 확인한다.
5. 로그인 후 `/launch-readiness`, `/tech-ops`, `/finance`, `/market-interview`를 browser에서 최종 확인한다.
