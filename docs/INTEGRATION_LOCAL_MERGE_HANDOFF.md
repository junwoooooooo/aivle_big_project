# Integration-Local 병합 인수인계

작성: **2026-08-10** · 브랜치 `market-research-v2` · **병합 진행 중, 아직 커밋 안 함**

이 문서는 새 세션이 이어받기 위한 것이다. 사실만 적는다. 추정에는 「추정」이라고 쓴다.

---

## 0. 지금 한 줄

`origin/Integration-Local`(5a0d64a)을 `market-research-v2`(fb758ba)에 병합했다.
충돌 10개를 전부 해소했고, 시장조사 모듈을 새 아키텍처로 포팅했으며,
**실스택(docker compose)에서 시장분석·BM 분석이 웹으로 실제 동작함을 확인했다.**
커밋만 남았다.

---

## 1. 즉시 알아야 할 것 3가지

### 1-1. 병합이 커밋되지 않은 채 인덱스에 올라가 있다

```
HEAD        fb758ba  (market-research-v2)
MERGE_HEAD  5a0d64a  (origin/Integration-Local)
백업 태그    backup/pre-integration-merge = fb758ba
```

`.git/MERGE_HEAD`가 살아 있다. `git commit` 하면 병합 커밋이 된다.
되돌리려면 `git merge --abort`.

### 1-2. 손대면 안 되는 파일 5개 (다른 세션/IDE가 작업 중)

2026-08-10 14:25~14:28에 **이 세션 밖에서** 수정됐다(그 뒤로도 계속 바뀌고 있다 — 14:42 재수정 확인).
사용자가 「건들지 말 것」으로 확인해 줬다. 전부 워킹트리에만 있고 **인덱스에는 없다**.

⚠ `docs/mockups/market-bm.html` 은 **양쪽 브랜치 어디에도 없는 신규 파일**인데
`git add -- docs` 가 한 번 끌어들였다가 뺐다. 다시 add 할 때 조심할 것.

| 파일 | 변경 | 상태 |
|---|---|---|
| `docs/mockups/market-bm.html` | 신규 63KB | 미추적 |
| `ai/tests/test_bm_plan_material.py` | 신규 | 미추적 |
| `ai/app/research/research2/data/concept_beauty-noshow.json` | +34 | 미스테이징 |
| `ai/app/research/research2/service/bm_adapter.py` | +48 / −6 | 미스테이징 |
| `ai/app/research/pipeline.py` | +14 / −4 | 미스테이징 |
| `ai/tests/test_bm_pipeline.py` | +40 | 미스테이징 |
| `docs/architecture/AS_BUILT_ARCHITECTURE.md` | +43 | 미스테이징 |

내용은 BM 실행 제약(`execution_constraints`)과 컨셉 스냅샷 확장 필드를 모델 페이로드까지
전달하는 작업으로 **보인다(추정)**. **커밋 전에 이것들을 어떻게 할지 반드시 사용자에게 확인할 것.**
문서 작성 시점(14:45)에도 계속 늘고 있었으니, **목록을 믿지 말고 `git status` 로 다시 셀 것.**

### 1-3. 미추적으로 남겨야 하는 로컬 자료

`front+back_renew/` · `model/` · `문서/` · `법률/` · `시장조사/` · `ai/legal/` ·
`valid-mvp-v11.html` · `frontEnd/src/features/feasibility/model/financialViewModel*.js`

**`git add -A` 를 쓰지 말 것.** 이 세션에서 두 번 사고가 났다.
경로를 명시해서 add 하고, 매번 `git diff --cached --name-only HEAD | grep -E "^(front\+back_renew|model|문서|법률|시장조사|ai/legal)/"` 로 확인할 것.

---

## 2. Integration-Local 이 무엇이었나 — 「프론트 갱신」이 아니다

두 브랜치는 **다른 제품 베이스라인**이었다. 근거:

- **TaskType 집합이 완전히 서로소** — 겹치는 값 0개.
  내 14개(`IDEA_INTERPRETATION`·`LEGAL_REVIEW`·… `MARKET_RESEARCH`) 전부 사라지고
  저쪽 12개(`IDEA_ATTACHMENT_PARSE`·`CONCEPT_FACTORY_RUN`·… `MARKETING_CONTENT_GENERATION`)로 교체.
- **`journey` 아키텍처가 프론트·백 양쪽에서 통째로 삭제**. 대신 `pipeline/*` 모듈 +
  `app/project-shell/` + `app/module-status/` 구조.
- **Flyway baseline 교체**: `V1__baseline_schema.sql` → `V1__new_pipeline_baseline.sql`.
- 전체 1735 파일 차이, 프론트만 316 파일(`+6609 / −16146`).

병합 방침(사용자 결정): **Integration-Local 을 전부 정본으로 삼고, 시장조사 모듈만 이식한다.**

---

## 3. 해소한 충돌 10개와 그 방침

| 파일 | 해소 |
|---|---|
| `.env.example` | 저쪽 기준 + 시장조사 env 블록. `DOCUMENT_JOB_*` 는 서브시스템 삭제됐으므로 버림 |
| `.gitignore` | 양쪽 합침 (`research2/runs`·`outputs` 무시 규칙 유지 — 232MB) |
| `compose.yaml` | 저쪽 기준. `DOCUMENT_JOB_*` 버림. 시장조사 키 전달은 비충돌 구간이라 그대로 살아남음 |
| `backend/.../taskrun/domain/TaskType.java` | 저쪽 12개 + `MARKET_RESEARCH` = **13개** |
| `ai/app/api/executions.py` | 저쪽 dispatch 구조 + `MARKET_RESEARCH` 분기 |
| `ai/tests/test_internal_task_type_alignment.py` | 저쪽 기준 + `MARKET_RESEARCH`, 개수 13 |
| `backend/.../taskrun/service/TaskRunWorker.java` | **삭제 수용** → §4-1 참조 |
| `frontEnd/src/app/layouts/ProjectLayout.jsx` | 삭제 수용 (→ `app/project-shell/ProjectLayout.jsx`) |
| `frontEnd/src/app/router/AppRouter.jsx` | 삭제 수용 (→ `app/routing/AppRouter.jsx`) |
| `frontEnd/src/features/journey/journeyApi.js` | 삭제 수용 → `features/market/marketApi.js` 신설(필요한 4개만) |

---

## 4. 시장조사 모듈을 새 아키텍처로 포팅한 내역

### 4-1. 워커 — 여기가 가장 위험했다

Integration-Local 이 공용 `TaskRunWorker` 를 없애고 **모듈마다 자기 워커**를 두는 구조로 바꿨다
(`ConceptFactoryWorker`·`FinancialEstimateWorker` … + `TaskRunWorkerContext`).

- 삭제: `TaskRunWorker.java`, `MarketResearchWorkerScheduler.java`, `TaskRunWorkerIntegrationTests.java`
- 신설: **`backend/src/main/java/com/aivle/backend/journey/MarketResearchWorker.java`**
  - 폴링 2초(`app.task-run.market-research-poll-interval-ms`), 예산 6분 / lease 8분
    (시장조사 전 구간이 90~266초라 2분 예산으론 구조적으로 못 끝난다)
  - **결과 검증을 이 워커 안에서 한다**: `MarketResearchContract.validate()` + 금지필드 거부.
    옛 `TaskRunWorker.validateResult()` 의 `MARKET_RESEARCH` 분기가 하던 일이다.
    **이게 없으면 AI 비용만 쓰고 결과가 조용히 폐기된다.**
  - `failWithLegalAutoRetry` 가 사라져서 `TaskRunService.fail` 을 쓴다
- 테스트 `MarketResearchWorkerIntegrationTests` 를 새 워커(`worker.processOne()`)에 맞춰 수정.
  이 테스트가 「조용한 폐기」를 잡는 유일한 그물이다.

### 4-2. Flyway

`V2__market_research.sql` → **`V10__market_research.sql`** 로 개명.
(저쪽 V2 와 번호 충돌. 저쪽 최신이 V9)
참조 테이블 `projects`(BIGINT) · `task_runs`(VARCHAR(64)) 가 새 baseline 에도 있고 타입도 일치함을 확인.
**다음 빈 버전은 V11. V1–V10 은 immutable.**

### 4-3. 화면 — `/market` · `/business-model` 슬롯을 가져왔다

Integration-Local 에는 **외부 모듈 연동 창구**가 그 자리에 있었다
(`features/market-integration/`, `features/business-model/` + 백엔드 `pipeline/integration/*`).
사용자 결정: **내 자체 엔진으로 교체.**

- `app/routing/AppRouter.jsx`: `/market` → `MarketResearchPage`, `/business-model` → `BmCanvasPage`
- `features/market/marketApi.js` 신설 (`createMarketApi`, 4개 메서드).
  백엔드 엔드포인트는 그대로 `/api/v2/projects/{id}/market-research` · `/business-model`
- 두 페이지의 옛 journey 라우트 하드코딩을 `projectRoutes.market/businessModel` 로 교체
- `app/project-shell/ProjectModulePages.jsx`: market/businessModel 설명문 교체
  (덤으로 저쪽의 죽은 키 `businessPersonaTest` → `businessModel` 로 바로잡힘)
- `AppRouter.cutover.test.js`: 라우트 가드의 기대 컴포넌트명 갱신

### 4-4. 모듈 상태 배선

`ProjectModuleStatusService` 가 외부 핸드오프(`ModuleRun`) 대신 **`MarketResearchRun`** 을 본다.

```
researchOrGate(run, seed):
  run != null  → run.state 매핑 (QUEUED/RUNNING/SUCCEEDED→COMPLETED/FAILED)
  run == null  → seed == null ? NOT_READY : READY
```

⚠ **`selectedSnapshot` 으로 막지 않는 것이 핵심이다.** 처음엔 막아 뒀는데,
견본 컨셉으로도 도는 엔진이라 실행이 SUCCEEDED 인데 배지가 「준비 전」으로 남는
**거짓말**이 됐다. 실스택 검증에서 발견해 고쳤다.

`ProjectJobQueryService`: `TaskType.MARKET_RESEARCH` → `JobModule.MARKET("/market")` 추가.
(enum 에 값을 더하니 exhaustive switch 가 컴파일 에러로 잡아 줬다)

### 4-5. 그 밖에

- `InternalAiExecutionClient.clientFor` 가 `TaskRun` → `ExecutionRequest` 를 받도록 수정.
  **자동 병합이 충돌 표시 없이 깨뜨린 곳**이다(시장조사 긴 타임아웃 라우팅).
- `ai/app/api/executions.py` 에 **`validate_text_contents` 복원**.
  저쪽이 지웠는데 시장조사만 `textContents` 봉투를 쓴다(`MarketResearchInputFactory`).
  `import hashlib` 도 같이 추가.
- **`ai/Dockerfile` 의 `COPY prompts ./prompts` 제거.** Integration-Local 이 `ai/prompts/` 22개를
  지우면서 이 줄을 남겨, **그 브랜치는 ai-server 이미지를 아예 빌드하지 못한다.**
  병합본 코드가 그 경로를 안 읽는 것을 확인하고 지웠다.
- `ActiveSurfaceCleanupTests` 의 TaskType 가드를 실제 enum(13개)에 맞춤.
  이 테스트는 **병합 전 Integration-Local 에서도 이미 실패**하고 있었다(저쪽 목록이 8개로 낡음).
- `CLAUDE.md` 갱신: 마이그레이션 항목(V1 new baseline / 다음은 V11), 실행 패턴 B 설명
  (공용 `TaskRunWorker` 없음 · 모듈마다 자기 워커).

---

## 5. 검증 결과 — 측정한 것만 적는다

### 5-1. 기준선을 실제로 재서 비교했다

`origin/Integration-Local` 을 워크트리로 따로 체크아웃해 같은 명령을 돌렸다.

| 검증 | 병합본 | Integration-Local 원본 |
|---|---|---|
| 백엔드 컴파일(main+test) | 통과 | — |
| 백엔드 테스트 | 312개 중 **2 실패** | 같은 2개 + `ActiveSurfaceCleanupTests` = **3 실패** |
| AI 테스트 | **311개 전부 통과** | — |
| 프론트 빌드 / lint | 통과 / 통과 | — |
| 프론트 테스트 | **201 통과 / 30 실패** | **199개 전부 실패** |
| ai-server 이미지 빌드 | 통과(Dockerfile 수정 후) | **실패**(`/prompts` not found) |

### 5-2. 남은 실패는 전부 병합 밖의 것이다

- **백엔드 2개** — `ConceptFactoryReplacementIntegrationTests.failedRetry…`(NPE),
  `IdeaBriefControllerTests.derive…`(202 기대 → 400).
  워크트리 실측으로 **기준선에서도 동일하게 실패**함을 확인.
- **프론트 30개** — `App.test.jsx`·`AuthPages`·`LandingPage`·`FinancePage`·`shared/async-events` 등.
  해당 파일들이 Integration-Local 과 **바이트 단위로 동일**함을 확인.
  원인은 로케일(`PM` vs `오후`)과 구 라우트(`/projects`) 기대.

> Integration-Local 원본이 이 PC 에서 프론트 테스트 199개 전멸하는 이유:
> `setupTests.js` 의 `localStorage.clear()` 가 `TypeError` 로 죽어 **정리 단계**가 실패하고
> 그 파일의 모든 테스트가 연쇄로 빨개진다. 내 브랜치의 방어 수정이 병합에 살아남아 걷혔다.

### 5-3. 실스택 확인 (docker compose, 웹 브라우저)

`docker compose down` 후 **postgres 볼륨만 삭제**하고 재빌드
(옛 baseline 이력이 있으면 Flyway 가 기동을 거부한다. minio·research2-runs 볼륨은 보존).
병합 전 DB는 `pg_dump` 로 백업함 — §7.

| 확인 | 결과 |
|---|---|
| Flyway | **V1–V10 전부 성공**, `V10__market_research.sql` 포함. `ddl-auto=validate` 통과 |
| 프로젝트 생성 | `POST /api/v1/projects` → **201** |
| 시장조사 | `POST .../market-research` → **202** → run 1 `FULL` **SUCCEEDED**, 결과 채택 |
| BM 분석 | `POST .../business-model` → **202** → run 2 `BM` **SUCCEEDED**, 결과 채택 |
| 새 워커 | 큐를 집어 AI 호출 → `POST /internal/v1/ai/executions` **200** |
| 화면 왕복 | 새로고침 후 결과 복원 (DB→화면 성립) |
| 모듈 배지 | 4·5번 **완료**, 나머지 **준비 전** |
| 작업 센터 | `MARKET RESEARCH 완료` 표시 (`JobModule.MARKET` 동작) |

**산출물 성적** (견본 컨셉 `beauty-noshow`, 기준일 2026-08-09):
TAM 10.25억원 / SAM 1.69억원(둘 다 **추정**), 성장률 2.282%/년(확정),
근거 12건(관측 9 · 계산 3), 뒷받침 없는 입력 8, 아직 못 채운 것 26, 값이 갈린 것 5.
BM 캔버스 9칸: 확인됨 2(가치 제안·고객 세그먼트), 일부 보완 필요 1(수익원), 미확인 4, 계획(근거 없음) 3.
판정 「조건부 · 신뢰도 MEDIUM」.

경계 표시 전부 생존: 「요약 문장을 만들지 않았다 — 검사 미통과 3회(fail-closed)」,
「세그먼트비중 0.19 는 관측이 아니라 가정」, 「법률 검토 결과가 반영되지 않았다」.

### 5-4. 아직 안 돌린 것

마지막 두 수정(**`ai/Dockerfile`**, **모듈 배지 게이트 `researchOrGate`**) 이후
**백엔드 테스트를 다시 돌리지 않았다.** 컴파일만 통과 확인.
커밋 전에 `cd backend && ./gradlew.bat test` 를 한 번 더 돌릴 것 (약 11~13분).

---

## 6. 남겨둔 죽은 코드 (삭제하지 않음)

라우팅에서 떨어져 나가 **참조 0곳**이 됐지만, 되살릴 여지가 있어 그대로 뒀다.

- `frontEnd/src/features/market-integration/` (외부 시장분석 연동 화면)
- `frontEnd/src/features/business-model/` (저쪽 BM 화면. 테스트는 아직 통과함)
- `backend/src/main/java/com/aivle/backend/pipeline/integration/` 19개 파일
  (`MarketResultIntakeService`·`MarketResultController`·`LocalMarketResultFixtureController` 등)

정리할지 여부는 사용자 판단 사항이다.

---

## 7. 복구 지점

- **백업 태그** `backup/pre-integration-merge` = `fb758ba`
- **병합 전 DB 덤프** (계정 1 · 프로젝트 3 · 시장조사 실행 5건):
  `C:\Users\User\AppData\Local\Temp\claude\C--Users-User-Desktop-------main\4b2df156-64ba-47f4-8ea2-021fa0f986a3\scratchpad\db-backup-pre-merge\aivle-full.sql` (595KB)
  ⚠ **스크래치패드는 세션 임시 폴더다. 남겨야 하면 옮길 것.**
- `git merge --abort` 로 병합 자체를 취소 가능

---

## 8. 다음 세션이 할 일

1. §1-2 의 **파일 5개를 어떻게 할지 사용자에게 확인** (병합 커밋에 포함? 별도 커밋? 그대로 방치?)
2. `cd backend && ./gradlew.bat test` 재실행 → §5-2 의 2개 외 새 실패가 없는지 확인
3. 커밋 전 `git diff --cached --name-only HEAD` 로 §1-3 의 로컬 자료가 안 섞였는지 확인
4. 병합 커밋
5. (선택) §6 죽은 코드 정리
