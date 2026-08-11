# Runtime · dependency · environment map

## 1. Target runtime 정본

장시간 작업은 기능과 관계없이 다음 경계를 따른다.

```text
Frontend
  → Backend Product API
  → TaskRun / TaskAttempt
  → Worker
  → Internal AI Execution
  → Materialization / Artifact persistence
  → JobEvent
  → Job SSE + Project SSE
  → canonical REST refresh
  → Project Module Status / Work Center
```

- Frontend는 provider 또는 AI FastAPI를 직접 호출하지 않는다.
- SSE event는 갱신 신호이며 결과 정본이 아니다. 수신 후 canonical REST를 다시 조회한다.
- current/history/stale/retry/idempotency는 Target 모델을 유지한다.
- 파일 결과의 정본은 MinIO/Object Storage 기반 Artifact다. AI 컨테이너의 local output이나 donor fixture/run 디렉터리를 제품 정본으로 사용하지 않는다.

## 2. 기능별 runtime·환경 변수 map

| 기능 | donor runtime/dependency | donor 환경 변수·설정 | Target 이식 원칙 | 부재/실패 의미 |
|---|---|---|---|---|
| Market collection/model | `ai/app/research/**`, KOSIS/DART/Web adapter, OpenAI-compatible model, 저장된 research run | `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `AI_MODEL`, `BM_MODEL`, `KOSIS_API_KEY`, `DART_API_KEY`, `TAVILY_API_KEY`, `RESEARCH2_RUNS_DIR` | API key는 AI 실행 환경에만 둔다. concept/upstream run은 서버 검증 snapshot/Artifact로 전달한다. A1~A3를 실제 worker execution에 연결한다. | source/evidence 부재, adapter 실패, rate limit을 degradation/partial/retryable로 구분한다. |
| BM | Market result, BM prompt/model, BM plan preparation | `BM_MODEL` 또는 `AI_MODEL`; OpenAI-compatible provider 설정 | 선택된 Market version과 plan을 TaskRun 입력에 고정한다. BM result version을 materialize한다. | Market join 불완전, evidence 부족, plan input needed를 별도 상태로 보존한다. |
| Twin | `ai/app/twin/**`, 외부 Twin Bank, provider client, concurrency/retry | `TWIN_BANK_DIR`, `TWIN_CONCURRENCY`(donor 기본 32), `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL` | Bank는 read-only mount, survey는 worker 실행, sample size/task/gate 입력은 immutable snapshot으로 기록 | Bank 부재는 `TWIN_BANK_UNAVAILABLE` 안전 실패. gate 불가/측정 불가를 성공 결과로 가장하지 않는다. |
| Finance estimate | 기존 Target Finance worker + mini Market/BM source, prompt, Tavily context | `TAVILY_API_KEY`, 기존 AI provider/model/base URL, internal AI 인증 설정 | 기존 `FINANCE_ESTIMATE` 실행 경계에 upstream Market/BM snapshot adapter를 추가한다. | Tavily는 fail-open이며 검색 결과를 검증된 사실로 표시하지 않는다. provider 실패와 deterministic 계산 결과를 구분한다. |
| Finance report | mini `/internal/v1/financial/report`, AI report client, fallback report | `AI_INTERNAL_SERVICE_TOKEN` 및 AI server 연결 설정 | Target Internal AI Execution 인증/관찰성 경계로 통합한다. fallback report의 출처와 caveat를 유지한다. | AI report 실패 시 전체 계산 성공으로 위장하지 않고 fallback/partial을 명시한다. |
| AIdev Visual | OpenAI image generation/edit, structured copy, Pillow 합성, 로컬 fonts/output | `OPENAI_API_KEY`, `MARKETING_COPY_MODEL`; AI server URL/timeout; donor local `ai/outputs` | Product API에서 TaskRun 생성, Internal AI로 실행, 결과 이미지와 metadata를 Artifact/MinIO에 저장한다. | 생성/합성/검증/업로드 실패 단계를 구분하고 안전 오류 및 retry 가능 여부를 기록한다. |
| Marketing Content | Target과 donor-aidev가 동일한 task pipeline | 기존 Target Marketing provider 및 내부 AI 설정 | 변경 없이 Target 정본 유지 | 기존 legal guard/revision failure 의미 유지 |
| TechOps | Target과 donor-mini 파일 해시 동일 | 기존 Target 설정 | 덮어쓰기·migration 재적용 없음 | 기존 Target 동작 유지 |

환경 변수의 실제 이름은 donor code에서 확인된 이름을 기록했다. Target 설정 클래스에 이미 같은 의미의 표준 키가 있으면 새 키를 병렬로 만들지 않고 adapter/config alias 여부를 결정한다.

## 3. 파일·mount·Artifact 권위

| 자원 | donor 계약 | Target 계약 | Git 포함 여부 |
|---|---|---|---|
| Market research runs | `RESEARCH2_RUNS_DIR`, compose read-only mount, sample/saved run을 source로 사용 | 개발 fixture는 test 전용. 제품 실행 입력·결과는 upstream version snapshot과 Artifact로 보존 | fixtures/tests만 허용; 사용자 실행 결과 금지 |
| Market sample concepts | beauty-noshow, household-ledger, pet-treat mapping | 샘플은 회귀 테스트/데모로만 유지. 임의 선택 Concept는 서버가 concept snapshot을 만들어 전달 | fixture로 허용 |
| Twin Bank | `./ai/app/twin/bank:/app/app/twin/bank:ro`, `TWIN_BANK_DIR=/app/app/twin/bank` | 동일한 외부 read-only mount 계약 유지. 이미지/DB에 내장하거나 Artifact로 복제하지 않음 | 금지. `.gitignore`와 `ai/.dockerignore` 유지 |
| Twin Bank 파일 | `twin_cards_generic.jsonl`, `twin_frame.csv`, `twin_bank_manifest.json` | manifest/필수 파일 검증 후 worker 시작 | 금지 |
| AIdev font | `NotoSansKR-Regular.ttf`, `NotoSansKR-Bold.ttf`, `OFL.txt` | 이미지 합성 runtime asset으로 포함하고 라이선스 고지 유지 | 허용 |
| AIdev generated image | `ai/outputs/banner_<id>.jpg` | MinIO/Object Storage의 project-owned Artifact, DB에는 artifact ID/metadata | local output은 정본으로 금지 |
| Finance report script output | root script가 특정 Windows 절대 경로에 `.docx` 생성 | 제품 runtime과 분리. 공식 report가 필요하면 Artifact 생성 workflow로 별도 이식 | 기존 개인 절대 경로 출력 금지 |

Twin Bank 현재 규칙 확인 결과:

- `ai/app/twin/bank/`는 Git ignore 대상이다.
- `ai/.dockerignore`도 `app/twin/bank`를 제외한다.
- Docker Compose가 host의 bank 디렉터리를 AI 컨테이너에 read-only로 bind mount한다.
- 따라서 Bank 자체를 Git에 추가하거나 Docker image에 포함하지 않는다.

## 4. 코드 dependency inventory

### 4.1 AI/Python

| 기능 | 주요 코드 dependency | 이식 메모 |
|---|---|---|
| Market | FastAPI/Pydantic 기반 계약, provider client, KOSIS/DART/Web adapter, JSON fixture/rule/scorecard | adapter별 timeout/retry와 evidence 원문/등급을 보존한다. |
| Twin | Pydantic models, provider client, JSONL/CSV Bank loader, 통계 aggregate | gate와 계산 상수를 수정하지 않는다. sample 50/100/300 및 1~4 pair 제한 유지. |
| Finance | financial models/prompts/API, Tavily client | Tavily 미설정/실패 시 fail-open. 계산식은 Java deterministic service와 역할을 혼합하지 않는다. |
| AIdev Visual | `Pillow==11.3.0`, `python-dotenv==1.1.1`, OpenAI image/copy client, font assets | dependency 버전 충돌과 image codec를 AI image build에서 검증한다. `.env` 파일은 커밋하지 않는다. |

### 4.2 Backend/Java

| 기능 | donor 구성 | Target 연결점 |
|---|---|---|
| Market | Run/Version entity·repository, input factory, service, worker, AI client, controller/DTO | TaskType 등록, worker dispatch, Internal AI adapter, materializer, ProjectModuleStatus, JobEvent |
| BM | BM plan entity/repository/service/controller, MarketJoin DTO, Market worker BM mode | Market version snapshot binding, BM plan ownership, BM version materialization |
| Twin | TwinSurveyRun/Version, service/worker/input factory/controller/contract, stimulus draft service | Twin TaskTypes, external bank preflight, TaskRun budget/retry, SSE/status |
| Finance | 공식 pipeline finance + mini `finance/**` deterministic/Monte Carlo/report/demo package | Target Finance current-state와 official project API를 유지하고 중복 controller는 분리 판단 |
| AIdev Visual | `AiServerMarketingClient.java`만 존재하며 product controller/service 사용처 없음 | project-owned API/service/worker/materializer를 새 seam으로 연결; 기존 orphan client 직접 노출 금지 |

### 4.3 Frontend/Node

| 기능 | donor 구성 | Target 연결점 |
|---|---|---|
| Market/BM | page, sections/cards/table/chart, API hooks, current polling | UI 정보는 전부 유지하고 실행/상태 hook만 TaskRun/SSE/canonical refresh로 교체 |
| Twin | `twin-survey/**` page, preparation, run state, result/profile/interviews UI | polling 제거, SSE 및 Work Center 링크 추가; gate/caveat/MDE 표시 보존 |
| Finance | 공식 `FinancePage`와 별도 `features/financial` sandbox page | 공식 project shell 안에 보존할 항목을 우선 이식; `/module` 샌드박스는 중복으로 별도 기록 |
| AIdev Visual | legacy `VirtualMarket.jsx/.css` | 제품 Marketing Content shell과 충돌 없이 Visual 하위 흐름으로 이식. 현재 timer mock을 실제 API로 교체 |

## 5. 저장소/보안 설정

Target의 기존 Object Storage 설정을 정본으로 사용한다. 실제 property 이름은 구현 시 Target configuration class와 compose를 기준으로 확정하되 의미상 다음 값들이 필요하다.

| 범주 | 설정 의미 | 원칙 |
|---|---|---|
| Object Storage | provider, internal endpoint, public endpoint, bucket, access key, secret key | Backend/worker만 쓰기 권한을 가진다. UI에는 서명/공개 URL 또는 다운로드 endpoint만 노출한다. |
| Internal AI auth | Backend↔AI service token | 로그/JobEvent/DTO에 token을 기록하지 않는다. |
| Provider keys | OpenAI-compatible, KOSIS, DART, Tavily | Frontend에 노출 금지. 기능별 최소 환경에만 주입한다. |
| Project ownership | project/user 인증 및 resource ownership | 모든 upstream version/artifact ID를 서버에서 재검증한다. donor의 path/ID 신뢰 금지. |
| Observability | task/run/attempt/execution correlation ID | 로그, JobEvent, materialized version을 연결하되 provider 응답 원문에 비밀정보가 없는지 정제한다. |

## 6. 기능별 preflight

| 기능 | worker 시작 전 검사 | 실패 시 처리 |
|---|---|---|
| Market | 선택 Concept 존재/ownership, snapshot 생성, 필요한 adapter key, 실행 budget | input needed 또는 retryable config/provider failure. sample run으로 자동 대체 금지 |
| BM | Market version current/ownership, BM plan 준비, 4개 planned cell 및 constraints | 준비 미완료를 명시하고 실행하지 않음 |
| Twin | Bank 디렉터리/manifest/필수 파일, task type, sample size, stimulus pair 수, provider 설정 | Bank unavailable, invalid input, unmeasurable을 구분 |
| Finance | preparation 완료, upstream BM/Market source, estimate 입력/단위, provider 선택 | deterministic validation과 provider failure를 분리 |
| AIdev Visual | source image MIME/크기, tone/format, prompt/copy 입력, font, storage write 가능 | validation/generation/composition/storage failure를 분리하고 부분 local file을 정본화하지 않음 |

## 7. 이식 검증 경계

Codex 구현 세션에서 수행할 수 있는 검증은 다음으로 제한한다.

- 기능별 targeted tests와 contract/golden fixture tests
- Backend compile/test
- Frontend production build 및 관련 테스트
- migration validate와 필요한 로컬 통합 테스트
- `git diff --check`

사용자가 직접 수행할 검증:

- Provider LIVE
- MOLEG LIVE
- 실제 Twin Bank 대규모 실행
- 전체 Browser E2E
- 실제 사용자 Docker 전체 검증

Session 1은 문서만 변경하므로 provider 호출, Docker 전체 구동, 실제 Bank 실행, product build를 수행하지 않는다.

## 8. 금지 사항 재확인

- donor polling/GET synchronize/local async를 Target runtime 정본으로 가져오지 않는다.
- MinIO/Artifact 권위를 `RESEARCH2_RUNS_DIR`, `ai/outputs`, 개인 Windows 경로로 대체하지 않는다.
- Twin Bank를 Git, Docker image, migration seed에 포함하지 않는다.
- Market/BM/Twin/Finance 계산·gate·formula를 integration 편의로 수정하지 않는다.
- Persona code, API, migration, asset, prompt를 검색·복원 범위에 넣지 않는다.

## 9. CUTOVER-R1 환경 보정

- `AI_SERVER_TWIN_SURVEY_READ_TIMEOUT`: Backend Twin 전용 AI HTTP read timeout. 기본 `14m`이며 general `AI_SERVER_READ_TIMEOUT`과 CPV2 전용 timeout을 변경하지 않는다.
- `TWIN_BANK_HOST_DIR`: 저장소 밖 Twin Bank host 디렉터리. 기본 예시는 `../aivle_private_assets/twin-bank`이며 container의 `/app/app/twin/bank`로 read-only mount한다.
- `TWIN_BANK_DIR`: AI container 내부 경로 `/app/app/twin/bank`를 유지한다.
- `KOSIS_API_KEY`, `DART_API_KEY`: Market FULL 공식 data adapter key다. 누락 시 해당 evidence channel이 실패하거나 partial result가 될 수 있다.
- `BM_MODEL`: 비어 있으면 `AI_MODEL`을 사용하며 별도 BM 모델이 필요할 때만 설정한다.
- `AI_CONCEPT_TEST_FAILURE_INJECTION=false`, `AI_CONCEPT_TEST_FAILURE_PLAN=`: 개발·회귀 fault injection 전용 normal-runtime 값이다.
