# Branch Transplant Matrix

## 1. main 대 market-research-v2

### 1.1 계보 판정

- `donor-main` HEAD `06d1947`: `Merge pull request #35 from junwoooooooo/market-research-v2`
- `donor-market` HEAD `f3d6dbd`
- merge-base: `06d1947`
- 좌/우 커밋 수: `0 / 12`
- 결론: `donor-market`은 main 병합 이후 계속 발전했으며 Market/BM/Twin의 source of truth다. relevant 삭제 파일은 0개다.

### 1.2 main 병합 뒤의 12개 커밋

| 커밋 | 내용 | 이식 판단에 미치는 영향 |
|---|---|---|
| `58ffedb` | Twin 결과를 사람의 말로 표현 | 프로파일, 대표 인터뷰, caveat, 결과 계약/UI 최신화 |
| `0ce2d36` | Twin handoff 문서 갱신 | 운영 계약 최신화 |
| `c928765` | CPV2 계보 반영 | Target에 이미 반영된 CPV2와 중복 |
| `9acbc1c` | Integration-Local 병합 | CPV2 제품 기반; Target 정본 우선 |
| `1166b88` | Concept에서 Twin 자극 초안 생성 | `TWIN_STIMULUS_DRAFT`와 초안 API/UI 추가 |
| `70a043f` | Twin 화면 value-first 개편 | 결과 UI와 편집 UI 보존 기준 |
| `bb046db` | Frontend 배포 배선 수정 | 기능 seam만 참고; Target 배포 기반 우선 |
| `b6780d1` | BM 실행계획 4칸 입력/저장/캔버스 연결 | BM 최신 정본 결정의 핵심 |
| `888c467` | main 재병합 | main과의 재동기화 |
| `4e087c9` | Integration-Local 병합 | CPV2 이식 계보 포함 |
| `09780a4` | Journey 단계 문서 8칸 정렬 | 제품 Journey 재설계에는 사용하지 않음 |
| `f3d6dbd` | 화면 단계 번호 8칸 정렬 | UI 제목/번호 참고, Target Shell 정본 우선 |

### 1.3 market에만 추가된 핵심 파일

main에 없고 market에 추가된 범위는 다음과 같다.

- Twin AI: `ai/app/twin/profile.py`, `stimulus_draft.py`
- Twin fixture/test: `stimulus_draft.json`, `test_twin_interviews.py`, `test_twin_profile.py`, `test_twin_stimulus_draft.py`
- Twin Backend: `TwinSurveyStimulusDraftService.java`, `TwinStimulusDraftContract.java`와 계약 테스트
- Twin Frontend: `PairEditorDialog*`, `StimulusDraftPicker*`, `draftFailureText*`, `PairPanel.test.jsx`
- BM Backend: `BmPlanPreparation.java`, repository, service, test, `V12__bm_plan_preparation.sql`
- BM/Market Frontend: `AssumptionLedger*`, `BmPlanForm.jsx`, `BmPlanPreview.jsx`, `bmPlan.js`와 관련 테스트, `emphasis.jsx`

기존 Market/Twin pipeline, serialize, contract, controller, page도 market에서 수정됐으므로 main 버전을 부분 정본으로 삼으면 안 된다.

## 2. Market 전체 inventory

### 2.1 AI

| 층 | 파일/디렉터리 | 실제 역할 | 보존 |
|---|---|---|---|
| Product orchestration | `ai/app/research/pipeline.py` | FULL/BM/RESCORE 봉투, stage/degradation, 저장 원장 결합 | YES |
| 실행 경계 | `ai/app/research/runner.py` | `RESEARCH2_RUNS_DIR`, run id, 오류 변환 | seam 교체 가능 |
| 계약 직렬화 | `ai/app/research/serialize.py` | evidence/scorecard/market/canvas/BM/summary allowlist 및 caveat 전파 | YES |
| 계약 스키마 | `research2/schema.py` | Concept→Formula→Slot→Route→Candidate/Document/Finding→Fact/Ledger→Estimate/Report | YES |
| A1 | `blocks/a_design.py` | 식·슬롯 설계, 사람 슬롯 overlay, 기간/단위/guard 감사 | YES |
| A2 | `blocks/a_desk.py` | KOSIS/DART/Web 라우팅 및 fallback | YES |
| A3 | `adapters/base.py`, `kosis.py`, `dart.py`, `web.py` | 수집, 검색, fetch, extract, adapter state | YES |
| A4/원장 | `fillaxis.py`, `runlog.py`, `run.py` | 정규화·채점·격리·coverage·원장 저장 | YES |
| 추정 | `blocks/b_estimate.py` | top-down/bottom-up 추정, 가정/관측 분리 | YES |
| 체인/일관성 | `blocks/c_chain.py`, `rules/consistency.v1.json` | 계산 체인과 violation | YES |
| 판정/카드/UI 변환 | `service/verdict.py`, `canvas.py`, `cards.py`, `summary.py` | 판정, BMC 재료, 카드, 요약 | YES |
| scorecard | `tools/scorecard.py` | 7과목 FILLED/PARTIAL/MISSING/REPORTED | YES |
| 규칙 | `research2/rules/*.json` | scoring, guards, units, trust, whitelist, fail-open, BM gate | YES |
| 데이터/fixtures | `research2/data/*.json`, `runs/*`, `ai/tests/fixtures/market_research/*` | 샘플 Concept, 식/슬롯, 저장 ledger/result, golden 계약 | YES, 단 runtime 저장소는 Artifact로 전환 |
| 테스트 | `research2/tests/test_step*.py`, `test_harness.py`, `test_failopen.py`, `test_verdict_canvas.py`, `ai/tests/test_market_research.py`, `test_bm_*`, `test_no_duplicate_research2.py` | 단계·fail-open·계약·중복 방지 | YES |

`research2/run.py`에는 A1~A3 배선이 실제로 존재한다. 그러나 Product `pipeline.py::_full()`은 `harness/dryrun/collect`에 `NOT_WIRED`를 남기고 저장된 원장만 읽는다. 이 차이는 이식 시 숨기면 안 된다.

### 2.2 Backend

- Run: `MarketResearchRun` — `FULL/BM`, `QUEUED/RUNNING/SUCCEEDED/FAILED`, source run, TaskRun, input hash, error code.
- Version: `MarketResearchVersion` — 결과 JSON, version number, filled/partial/missing, decision/confidence, evidence/caveat 수.
- API: `MarketResearchController` — start/current for Market과 BM, BM plan GET/PATCH.
- Input adapter: `MarketResearchInputFactory` — Concept JSON, label, 기준일, mode, LLM budget, BM plan/constraint.
- Worker: `MarketResearchWorker` — TaskType `MARKET_RESEARCH`, 내부 AI 실행, JobEvent.
- 상태/구체화: `MarketResearchService` — donor current GET에서 TaskRun을 synchronize한 뒤 Version을 생성.
- 계약: `MarketResearchContract` — exact envelope 및 evidence reference/caveat 전파 검증.
- DB: donor V10 `market_research_runs`, `market_research_versions`; donor-market V12 `bm_plan_preparations`.

Target 이식에서는 donor Run/Version 의미를 보존하되 별도 current GET synchronize를 제거하고 Worker 완료 시 materialization해야 한다.

### 2.3 Frontend

- `MarketResearchPage.jsx`: 실행/재실행, sample Concept 선택, loading/active/failure/empty, KPI, 7과목 결과, 다음 BM action.
- `marketResult.js`: grade/state/source-kind/not-found taxonomy, 9칸 BMC layout, normalize 및 evidence bucket.
- `AssumptionLedger.jsx`: TAM/SAM/SOM 등 계산 항별 값·판정·근거, 전체 caveat.
- `BmCanvas*.jsx`: BM 9칸, 칸별 상태/내용/사유/근거/caveat/missing evidence.
- `marketApi.js`, `useMarketPolling.js`: donor polling seam이며 Target SSE로 교체 대상.
- `market.css`, `emphasis.jsx`: 정보 의미를 살리는 강조/상태 표현.

### 2.4 sample/saved ledger/A1~A3/arbitrary Concept 판정

| 질문 | 실제 판정 | 근거 |
|---|---|---|
| sample Concept 의존인가 | YES | `pipeline.CONCEPTS`가 3개 label을 `(concept file, source run)`에 고정 |
| saved ledger 의존인가 | YES | Product `_full()`은 `runs/<sourceRun>/result.json`, `run.jsonl`을 읽음 |
| A1~A3 구현이 있는가 | YES | `research2/run.py`에서 A1 설계, A2 route, A3 병렬 collect 수행 |
| Product 실행에 A1~A3가 연결됐는가 | NO | `pipeline.py`가 세 stage를 `SKIPPED/NOT_WIRED`로 반환 |
| arbitrary selected Concept 지원인가 | AI 함수 수준 조건부 / Product E2E NO | 명시 `sourceRun`+기존 directory+`conceptPath`면 가능하지만 Backend가 보내지 않고 저장 ledger 생성도 안 함 |

## 3. BM 전체 inventory

### 3.1 Market join과 분석 계약

- `bm/contracts.py::MarketJoinData`: `concept_snapshot`, market size(TAM/SAM/SOM), growth, competitor, price, demand, market-size calculation, execution constraints, evidence list.
- evidence 식별자는 `evidence_list[].id`가 정본이다. `card_id`로 바꾸면 인용이 조용히 탈락한다.
- `bm/analyze.py`는 허용 evidence id와 source label만 남기며, 인용 없는 값을 근거처럼 만들지 않는다.
- `bm/normalize.py`는 노트북 전역 탐색을 제외하고 명시 입력으로 정규화한다.
- `bm/prompt.py`는 9칸, market fit, consistency, evidence/caveat 규율을 정의한다.
- `bm/finalize.py`는 PASS/PARTIAL/FAIL과 legal 결과를 최종 decision/confidence에 결합한다.
- `bm/handoff.py`는 TAM/SAM/SOM, 성장률, 가격, revenue model과 누락 입력을 `READY/PARTIAL/BLOCKED` financial handoff로 만든다.

### 3.2 BMC 9칸과 의미

| 묶음 | 칸 | 종류/근원 |
|---|---|---|
| 고객과 가치 | 고객 세그먼트, 가치 제안, 채널, 고객 관계 | 앞 3개 관측, 고객 관계 계획 |
| 실행 구조 | 핵심 활동, 핵심 자원, 핵심 파트너 | 계획 |
| 수익과 비용 | 수익원, 비용 구조 | 수익원 관측, 비용 구조 입력 제약 |

`BmPlanForm`은 고객 관계, 핵심 활동, 핵심 자원, 핵심 파트너를 받으며 비용 구조용 예산/기간/인원을 별도로 받는다. 빈 값은 키 자체를 보내지 않는다. 계획 칸은 evidence 0이 정상일 수 있고, 관측 칸의 evidence/caveat는 칸 옆에 유지한다.

### 3.3 보존할 결과

- `decision`, `confidence`, summary
- `marketFitStatus`, `marketFitSummary`
- `consistencyStatus`, `consistencySummary`
- strengths, weaknesses, risks
- legal used/status/summary/risks/requiredActions
- 각 canvas cell의 status/content/reason/sourceLabels/marketEvidenceIds/missingEvidence/caveats
- financial handoff와 missing financial inputs

Market→BM 연결에서 MarketJoinData의 evidence id, grade/caveat, 계산식/가정, not-found 의미, Concept snapshot과 실행 제약을 그대로 유지해야 한다.

## 4. Twin Survey 전체 inventory

### 4.1 AI

- `twin/models.py`: strict side/pair/input, 1~4쌍, sample 50/100/300.
- `twin/task_type.py`: IDENTICAL, DOMINANCE, PRICE, ETHICAL_VALUE, UNMEASURABLE 분류와 serviceable gate. 현재 서비스 가능 유형은 DOMINANCE이며 gate 수정 금지.
- `twin/bank.py`: 카드/표집틀 로드, 성별×연령 band 층화표집, short cell 보고.
- `twin/stimuli.py`: X/Y 양방향 자극, 위치 편향 상쇄, 2회+불일치 시 3회 적응 반복.
- `twin/runner.py`: 공급자 호출, rate limit/retry, `TWIN_CONCURRENCY`.
- `twin/aggregate.py`: Δ, λ, 분산, 신뢰구간, MDE(2.80×SE), MDE floor, 판정.
- `twin/profile.py`: 카드 본문에서 인터뷰용 프로파일 추출.
- `twin/__init__.py`: 결과 조립, 대표 응답 5개 이내, labels/profiles/winner/response classes/caveats.
- `twin/caveats.py`: 유형별/측정불가 경계 문구.
- `twin/stimulus_draft.py`: 확정 Concept 입력으로 1~4개 serviceable 자극 초안 생성, drop 이유 보존.

### 4.2 Backend/DB/runtime

- `TwinSurveyRun`, `TwinSurveyVersion`, repositories, `TwinSurveyService`, `TwinSurveyWorker`, `TwinSurveyInputFactory`.
- `TwinSurveyController`: stimulus draft POST, survey POST 202, current GET.
- `TwinSurveyContract`: exact result, sample, telemetry, interview/profile, respondent class, caveat 검증.
- `TwinStimulusDraftContract`: exact draft/pair/side/dropped 검증.
- donor V11: run/version table과 sample size constraint.
- TaskType: `TWIN_SURVEY`, `TWIN_STIMULUS_DRAFT`.

### 4.3 Bank와 인프라

- 외부 파일: `twin_cards_generic.jsonl` 8,604명, `twin_frame.csv`, `twin_bank_manifest.json`.
- Git 제외: `.gitignore`의 `ai/app/twin/bank/`.
- 이미지 제외: `ai/.dockerignore`의 `app/twin/bank`.
- compose: `TWIN_BANK_DIR=/app/app/twin/bank`, `./ai/app/twin/bank:/app/app/twin/bank:ro`.
- 미마운트/누락/빈 frame은 `TWIN_BANK_UNAVAILABLE`; Git에 Twin Bank를 넣지 않는다.

### 4.4 테스트/fixtures/UI

- AI: aggregate, gate parity, golden, interviews, profile, runner, stimuli, stimulus draft, survey, task type 테스트.
- Backend: TwinSurvey/TwinStimulusDraft contract 테스트.
- fixtures: `gate_cases.json`, `survey.json`, `stimulus_draft.json`.
- Frontend: 초안 선택, pair 편집, gate, sample size/MDE, result normalize, 대표 인터뷰, caveat 누락 경고 테스트.
- UI 보존 상세는 `02_DONOR_UI_INFORMATION_INVENTORY.md` 참조.

## 5. mini Finance inventory

### A. 공식 project Finance

- `backend/.../pipeline/finance/**`: preparation, immutable snapshot, AI estimate assistance, finalize/reopen, BM run source 연결.
- mini가 추가한 공식 endpoint: `/api/v3/projects/{projectId}/finance/input-snapshots/current/reopen`, `/analysis`; `/demo`는 개발용.
- `FinancialSnapshotAnalysisService`: 확정 snapshot을 계산 계약으로 변환.
- frontend `features/finance/**`: BM 근거, 입력, AI 도움말, snapshot, 분석/보고서 화면.

### B. deterministic calculation

- `FinancialCalculationService`: repository/HTTP/random/AI가 없는 순수 계산기.
- one-time/subscription/mixed revenue, 월별 매출·변동비·고정비·영업이익·세금·순이익·누적현금흐름, BEP/payback/운전자금, sensitivity.
- `FinancialInputScaler`: KRW/천원/백만원 입력 경계 변환, 내부 KRW 정본.

### C. Monte Carlo

- `FinancialMonteCarloService`: seed 고정 가능한 `SplittableRandom`, volume/price/cost shock, P10/P50/P90 profit, loss/payback probability.
- `FinancialModuleRequest`: simulation count, volatility 3종, random seed.

### D. AI estimate/report

- 기존 공식 `FINANCE_ESTIMATE` TaskType: 필드별 proposal, assumptions/explanation/confidence/source=`AI_ESTIMATE`.
- `FinancialAiReportClient` + `ai/app/api/financial.py`: 집계 계산 결과로 headline/findings/cautions/recommended actions/disclaimer 생성.
- AI 실패 시 `FinancialModuleService`가 deterministic fallback report를 유지한다.

### E. Tavily

- `ai/app/tasks/finance_estimate/tavily.py`: `TAVILY_API_KEY`가 있을 때 기본 검색 최대 3건, fail-open.
- 검색 결과는 검증된 사실이 아니라 외부 context이며 prompt가 가정 표기를 강제한다.

### F. Frontend

- 공식 `FinancePage`: 고정비/초기투자/3개년 목표/수익모델/CAC/AI 추천/snapshot/handoff/분석 결과.
- 별도 `FinancialModulePage`: 로그인 없는 `/module` sandbox, 자체 입력·계산·보고서.

### G. report

- 분석 응답 자체의 headline/findings/cautions/actions/disclaimer.
- prompt: `ai/prompts/financial_report_generation/{system,user}.md`.
- repo 루트의 `create_financial_modularization_guide.py`, `create_financial_module_report.py`는 절대경로 `C:\dev\aivle_big_project\*.docx`를 쓰는 문서 생성 스크립트다. 제품 runtime이 아니며 결과 docx는 Git에 없다.

### H. sandbox/demo/duplicate API

- `/api/v1/modules/financial/preview`: 로그인 없는 module preview.
- `/api/v3/projects/{projectId}/finance/demo`: project ownership만 확인하는 fixture 실행.
- `/api/finance/analysis`: 초창기 단순 DTO/service API로 Monte Carlo가 아직 없다는 주석이 있는 중복/불완전 경로.
- `/module`: 별도 sandbox UI.
- 삭제 여부는 Session 1에서 결정하지 않는다. 공식 이식 경로와 분리 표기한다.

## 6. TechOps 파일 단위 비교

비교 범위는 Backend main 14개, Backend test 7개, Frontend 9개다. SHA-256 비교 결과 30개 모두 동일하다.

| 분류 | 파일 |
|---|---|
| 동일 | `pipeline/techops/api/*` 2, `application/*` 5, `domain/*` 3, `repository/*` 3, `worker/*` 1 |
| 동일 테스트 | `TechOpsControllerAsyncTests`, `TechOpsEvidenceArtifactTests`, `TechOpsHandoffTests`, `TechOpsPreparationContractsTests`, `TechOpsProposalCompletionServiceTests`, `TechOpsServiceAsyncTests`, `TechOpsV2ContractTests` |
| 동일 Frontend | `features/tech-ops/api`, hook+test, model+test, page+test, index, CSS |
| Target이 더 최신 | 관련 파일 기준 0 |
| mini에만 존재 | 관련 파일 기준 0 |

따라서 TechOps를 mini로 덮어쓸 이유가 없으며 Target 파일을 그대로 유지한다.

## 7. AIdev inventory

### 7.1 Target과 겹치는 Marketing Content

다음 세 범위는 Target과 AIdev가 파일별 SHA-256까지 동일하다.

- `ai/app/tasks/marketing_content/**`
- `backend/.../pipeline/marketing/**`
- `frontEnd/src/features/marketing-content/**`

즉 source snapshot, content/revision, legal guard, async worker, copy editor/list/style/source summary UI는 이미 Target에 있다.

### 7.2 AIdev에만 있는 Visual 기능

| 구성 | 파일 | 역할 |
|---|---|---|
| request/response model | `ai/app/models/marketing.py`, `marketing_copy.py` | banner form과 생성 copy strict model |
| copy | `marketing_copy_service.py` | OpenAI structured output으로 badge/headline/subheadline 생성 |
| prompt | `prompt_service.py` | 상품/프로모션/톤/형식/키워드 기반 이미지 prompt |
| image generation/edit | `openai_banner_service.py` | 업로드 이미지를 `gpt-image-2`로 edit, base64 수신 |
| image composition | `banner_text_service.py` | Pillow로 자동 줄바꿈/폰트 축소/명암 대비/배지·제목·보조문구 합성 |
| validation | `ai/app/utils/image_validator.py` | 업로드 이미지 검증 |
| fonts/assets | `ai/assets/fonts/NotoSansKR-{Regular,Bold}.ttf`, `OFL.txt` | 한글 합성 및 라이선스 |
| AI API | `ai/app/api/marketing.py` | `POST /api/v1/marketing/banners/generate`, multipart direct 장시간 호출 |
| Backend client | `AiServerMarketingClient.java` | multipart relay client. 호출하는 Product controller/service는 없음 |
| dependency | `Pillow==11.3.0`, `python-dotenv==1.1.1` | AIdev 추가 의존성 |
| local persistence | `ai/outputs/banner_<id>.jpg` | Target MinIO/Artifact로 교체할 seam |
| legacy Visual UI | `frontEnd/src/page/VirtualMarket.jsx/.css` | 상품/프로모션/톤/형식/업로드/생성/미리보기/PNG 저장 데모 |

중요: legacy `VirtualMarket`의 `handleGenerateBanner`는 실제 API를 부르지 않고 timer와 local state로 mock preview를 만들며, 저장도 browser canvas download다. FastAPI Visual API와 Backend client도 서로 제품 endpoint로 연결돼 있지 않다. 기능은 존재하지만 end-to-end wiring은 미완성이다.

## 8. 제외/비정본

- Persona 관련 과거 코드와 화면은 복원하지 않는다.
- donor의 legacy journey route와 `/api/v2/.../current` polling은 Target API 정본이 아니다.
- Finance sandbox/demo/초기 `/api/finance`는 기록은 보존하되 공식 product endpoint로 승격됐다고 보지 않는다.
- AIdev local `ai/outputs`는 결과 authority가 아니다.
