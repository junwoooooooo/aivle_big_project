# TaskType · API · 비동기 실행 전환표

## 1. 판정 기준

- Target의 `TaskRun → Worker → Internal AI Execution → Materialization → JobEvent → SSE → canonical REST refresh` 흐름을 정본으로 유지한다.
- donor의 polling, 조회 시 동기화, 컨트롤러 직접 실행, 로컬 파일 저장은 분석 기능이 아니라 교체 대상 integration seam으로 본다.
- 아래 endpoint는 Session 1 감사 결과에 따른 이식 후보다. 이번 세션에서는 enum, route, worker, DB를 수정하지 않는다.
- Persona 관련 TaskType/API는 조사·복원 대상에서 제외했다.

## 2. TaskType 현황

### 2.1 Target 현재 TaskType

| 기능군 | 현재 값 |
|---|---|
| Idea | `IDEA_ATTACHMENT_PARSE`, `IDEA_BRIEF_DERIVATION` |
| CPV2 | `CONCEPT_PORTFOLIO_V2_RUN`, `CONCEPT_PORTFOLIO_V2_CONTINUE`, `CONCEPT_PORTFOLIO_V2_SELECTION_ACTION` |
| Concept 하위 실행 | `CONCEPT_FACTORY_RUN`, `CONCEPT_CANDIDATE`, `CONCEPT_DISTINCTNESS_JUDGE`, `CONCEPT_LEGAL_REVIEW`, `CONCEPT_REDESIGN`, `CONCEPT_HYPOTHESIS_ALTERNATIVE`, `CONCEPT_DELTA_LEGAL_REVIEW` |
| TechOps | `TECH_OPS_PROPOSAL` |
| Finance | `FINANCE_ESTIMATE` |
| Marketing Content | `MARKETING_CONTENT_GENERATION` |

### 2.2 donor별 추가 값

| TaskType | donor-main | donor-market | donor-mini | donor-aidev | donor-integration-local | Target |
|---|---:|---:|---:|---:|---:|---:|
| `MARKET_RESEARCH` | O | O | O | O | X | X |
| `TWIN_SURVEY` | O | O | O | X | X | X |
| `TWIN_STIMULUS_DRAFT` | X | O | X | X | X | X |
| `FINANCE_ESTIMATE` | O | O | O | O | O | O |
| `MARKETING_CONTENT_GENERATION` | 브랜치별 기반 차이 | O | O | O | O | O |

`donor-market`만 Twin stimulus draft를 독립 TaskType으로 등록한다. BM은 별도 TaskType을 만들지 않고 `MARKET_RESEARCH` 입력의 `mode=BM`으로 실행한다.

### 2.3 Target 추가 후보

| 기능 | 추가 후보 | 판정 | 보존/전환 이유 |
|---|---|---|---|
| Market/BM | `MARKET_RESEARCH` | 필수 | FULL과 BM 모드를 같은 donor pipeline 계약으로 보존한다. mode는 TaskRun 입력 snapshot에 고정한다. |
| Twin 본 실행 | `TWIN_SURVEY` | 필수 | 외부 Twin Bank, provider 호출, sampling/aggregate를 장시간 작업으로 실행한다. |
| Twin stimulus draft | `TWIN_STIMULUS_DRAFT` | 필수 | donor-market에 실제 등록된 값이며 stimulus 생성 실패·재시도 상태를 보존한다. |
| Finance estimate | 기존 `FINANCE_ESTIMATE` | 신규 값 불필요 | Target 정본 흐름이 이미 존재한다. mini 계산/리포트 이식 시 기존 값과 역할을 먼저 분리한다. |
| AIdev Visual | `MARKETING_VISUAL_GENERATION` | 통합 설계 시 검토 | donor에는 TaskType이 없고 AI API를 직접 호출한다. 이미지 생성은 Target 장시간 실행 모델상 별도 TaskType이 합리적이지만 donor 원형 값은 아니다. |
| Finance deterministic/Monte Carlo | 없음 | 기본적으로 불필요 | 외부 호출이 없는 빠른 결정론 계산은 동기 domain service로 유지 가능하다. AI report가 장시간이면 별도 TaskRun 경계를 검토한다. |

## 3. API 전환표

### 3.1 Target 공식 패턴

| 목적 | Target 패턴 |
|---|---|
| 프로젝트 소유권 경계 | `/api/v3/projects/{projectId}/...` |
| 장시간 실행 생성 | `POST`, idempotency key, `202 Accepted`, TaskRun/Job 식별자 반환 |
| 현재 정본 조회 | 모듈 `current` 또는 canonical resource GET |
| 개별 실행/버전 조회 | 안정적인 run/version 식별자 기반 GET |
| 작업 스트림 | `/api/v2/jobs/{jobId}/events` SSE |
| 프로젝트 스트림 | `/api/v2/projects/{projectId}/events` SSE |
| 재시도·이력 | Work Center 및 Target retry/stale/history 모델 |
| AI 내부 실행 | `/internal/v1/ai/executions` 계열의 내부 인증 경계 |

### 3.2 기능별 map

| 기능 | Donor API | 필요한 Target endpoint 후보 | 중복/개발 endpoint 및 처리 |
|---|---|---|---|
| Market | `POST /api/v2/projects/{projectId}/market-research`, `GET .../current` | `POST /api/v3/projects/{projectId}/market-research-runs`, `GET .../market-research-runs/{runId}`, `GET .../market-research/current` | donor `current`의 조회 시 TaskRun 동기화 제거. 재시도는 별도 donor route를 복원하지 않고 Target Work Center 사용. |
| BM | `POST /api/v2/projects/{projectId}/business-model`, `GET .../current`, BM plan 조회/수정 | `GET/PATCH /api/v3/projects/{projectId}/business-model/preparation`, `POST .../business-model-runs`, `GET .../business-model-runs/{runId}`, `GET .../business-model/current` | BM 실행은 `MARKET_RESEARCH(mode=BM)`로 유지하되 API DTO에서 명시적으로 캡슐화한다. Market result/version ID를 서버가 snapshot으로 결속한다. |
| Twin | `POST /api/v2/projects/{projectId}/twin-survey`, `GET .../current`, `POST .../stimulus-draft` | `POST /api/v3/projects/{projectId}/twin-survey-runs`, `GET .../twin-survey-runs/{runId}`, `GET .../twin-survey/current`, `POST .../twin-survey/stimulus-drafts` | donor polling/current 동기화 제거. stimulus draft도 TaskRun/worker로 실행하고 Job/Project SSE를 사용한다. |
| Finance 공식 | mini의 `/api/v3/projects/{projectId}/finance` 아래 preparation, AI assistance, finalize, reopen, current snapshot, analysis, demo | Target 기존 preparation/estimate/finalize/current API 유지. donor의 reopen은 기존 snapshot 권위·stale 규칙에 맞는 명시적 command로 추가 검토. 분석은 `GET .../finance/current` 또는 별도 version resource로 materialize | `/analysis`가 provider를 직접 기다린다면 async run으로 분리. `/demo`는 공식 제품 API에 승격하지 않는다. |
| Finance sandbox | `POST /api/finance/analysis`, `/api/v1/modules/financial/preview` | 없음 | 초기/샌드박스/중복 API로 기록만 하고 이식 여부는 후속 세션에서 결정한다. 공식 project finance를 대체하지 않는다. |
| Marketing Content | Target/donor의 project Marketing Content API | 기존 Target API 유지 | donor-aidev core가 Target과 파일 해시 기준 동일하므로 중복 이식하지 않는다. |
| AIdev Visual | AI 직접 `POST /api/v1/marketing/banners/generate` multipart | `POST /api/v3/projects/{projectId}/marketing-visual-runs`, `GET .../{runId}`, `GET .../current` 또는 artifact 목록/상세 endpoint | AI 직접 endpoint와 브라우저 타이머 mock을 제품 API로 노출하지 않는다. 생성물은 Artifact/MinIO 식별자로 반환한다. |
| Internal AI | Market/Twin worker별 donor 전용 client 호출, Finance `/internal/v1/financial/report`, Visual AI 직접 route | Target `InternalAiExecution` 계약과 인증을 통해 task별 입력·출력 adapter 등록 | 외부 provider API key를 Backend/Frontend에 노출하지 않는다. donor 내부 endpoint는 호환 adapter로만 사용하거나 통합한다. |

Endpoint 명칭은 구현 세션에서 Target 기존 controller naming과 충돌을 재확인해야 한다. 표의 핵심은 project ownership, 비동기 생성, canonical GET, SSE의 네 경계를 보존하는 것이다.

## 4. 비동기 실행 seam map

| 기능 | Donor 실제 실행 방식 | 보존할 분석/결과 | 교체할 seam | Target 완료 조건 |
|---|---|---|---|---|
| Market FULL | Backend run 생성 → worker → AI pipeline. Product adapter는 A1~A3을 실행하지 않고 저장된 `sourceRun`을 읽으며, UI는 current를 polling한다. GET이 TaskRun을 동기화하고 version을 materialize한다. | A1~A4 구현, ledger/evidence/grade, scorecard, formula, market/canvas/summary 계약, degradation 의미 | concept/source snapshot 생성, A1~A3 wiring, TaskRun worker 등록, worker 종료 시 materialization, JobEvent, ProjectModuleStatus, SSE | 성공/부분/실패 version이 한 번만 materialize되고 REST current가 정본이 된다. 조회 GET은 상태를 변경하지 않는다. |
| BM | `MARKET_RESEARCH` worker의 `mode=BM`; Market 결과와 plan을 input에 조합. current polling 및 조회 시 동기화 | MarketJoinData, 9 BMC cells, evidence/caveat, consistency/market fit, 강점·약점·위험, financial handoff | upstream Market version과 BM plan의 immutable snapshot binding, Target worker/materialization/SSE | 실행 당시 Market version/plan을 재현할 수 있고 stale/history가 Target 규칙대로 표시된다. |
| Twin survey | run/worker가 Twin Bank 로드 → gate → stratified sampling → provider → aggregate. UI polling, current GET 동기화 | gate, 50/100/300 sampling, X/Y 양방향 질문, caveat, MDE, profile, interviews, result UI | bank mount 검증, TaskRun input snapshot, worker/materialization, event/SSE, retryable 안전 오류 | Bank 부재를 성공으로 가장하지 않고 `TWIN_BANK_UNAVAILABLE` 계열 안전 실패로 남긴다. canonical version은 immutable하다. |
| Twin stimulus draft | donor-market TaskType은 있으나 API client가 완료까지 기다리는 결합이 남아 있음 | stimulus 초안 계약과 유효성 검사 | 독립 TaskRun + worker + JobEvent/SSE | 초안 생성 상태·실패·재시도를 Work Center에서 관찰 가능하다. |
| Finance estimate | Target에 이미 TaskRun/Worker/SSE 정본이 존재 | mini의 BM/Market input 보강, AI estimate 의미, Tavily fail-open caveat | 기존 Target finance worker의 upstream snapshot adapter만 확장 | 기존 current/stale/retry/idempotency를 훼손하지 않는다. |
| Finance deterministic | 서비스 호출 중 즉시 계산 | 입력 scaling, 손익/현금흐름/회수기간 계산식 | 필요 시 DTO와 snapshot 저장만 연결 | 계산식을 바꾸지 않고 같은 입력에 같은 출력이 나온다. |
| Finance Monte Carlo | 요청 스레드에서 seeded `SplittableRandom` 계산 | P10/P50/P90, 손실 확률, 회수 확률, seed 재현성 | 대량 실행일 때만 worker 경계 검토; 공식 snapshot에 seed/input 저장 | 결정론 재현성과 partial/not measurable 의미가 유지된다. |
| Finance AI report | Backend가 AI report client를 직접 호출하고 fallback report를 구성 | report 항목, safe fallback, evidence 부족 표현 | 장시간이면 TaskRun/Internal AI/materialization으로 이동 | provider 실패를 계산 성공으로 오인하지 않고 fallback 출처를 명시한다. |
| Marketing Content | Target 정본 worker 흐름 | 기존 copy/revision/legal 결과 | 변경 없음 | 기존 TaskType/API/SSE 유지 |
| AIdev Visual | Frontend는 타이머 mock, AI FastAPI는 provider 직접 호출 후 `ai/outputs` 로컬 JPG 저장; Backend client는 product route에 연결되지 않음 | copy, prompt, 이미지 생성·합성, 글꼴, tone/format/input/result UI | project API → TaskRun → worker → Internal AI → Artifact/MinIO → events/SSE → canonical refresh | local AI file이 정본이 되지 않고, 이미지/메타데이터/실패 이유가 프로젝트 소유권 아래 저장된다. |

## 5. 상태·오류 의미 보존

| Donor 의미 | Target 표현 원칙 |
|---|---|
| `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED` | TaskRun/TaskAttempt 상태와 ProjectModuleStatus에 매핑 |
| retryable provider/transport failure | 안전한 `lastErrorReason`/attempt reason과 retry 가능 여부로 기록 |
| partial result | 성공으로 평탄화하지 않고 version의 partial/missing/degradation을 유지 |
| input needed | BM plan, Finance preparation 등 준비 상태로 명시하고 실행 불가 사유 반환 |
| not measurable / gate failure | Twin gate/result의 별도 상태와 caveat 유지; 빈 성공 결과로 변환 금지 |
| evidence unavailable | evidence grade, missing evidence, caveat를 UI까지 전달 |
| stale/current/history | Target current-state authority와 Work Center history를 사용 |

## 6. 구현 전 확인 항목

- 새 TaskType이 TaskRun 생성, worker dispatch, budget, retry policy, JobEvent payload, ProjectModuleStatus에 모두 등록되는지 확인한다.
- 요청 DTO가 임의의 local path를 받지 않도록 하고, upstream concept/Market/BM/Finance 버전은 서버가 ownership 검증 후 snapshot으로 결속한다.
- POST idempotency와 worker materialization idempotency를 분리해 검증한다.
- SSE 수신 후 Frontend는 이벤트 payload를 결과 정본으로 사용하지 않고 canonical REST를 다시 조회한다.
- polling interval, GET-side synchronize, local async executor, provider 직접 호출이 남아 있지 않은지 기능별로 검색한다.
- Work Center retry가 동일 snapshot을 재사용하는지, 새 입력은 새 TaskRun이 되는지 확인한다.
