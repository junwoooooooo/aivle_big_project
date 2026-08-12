# 03. 시스템 아키텍처

> ⚠ **이 문서는 `AS_BUILT_ARCHITECTURE.md`의 오류를 교정한 판본이다.**
> 원문서 §5·§6·§7이 낡았다 — 마이그레이션 범위, AI 서버 파일 구조, TaskType 개수가 틀리다.
> 교정 내역은 `99_MISSING_MATERIALS.md` E절. **발표에는 이 문서를 쓴다.**

---

## 1. 프로세스와 포트

```
브라우저
  │ HTTPS
  ▼
frontend        React 19 + Vite        :3000 (compose) / :5173 (dev)
  │ /api/v1, /api/v2
  ▼
backend         Spring Boot 4.1 (Java 17)      :8080
  ├── JPA / Flyway ──▶ PostgreSQL 17           :5432
  ├── S3 SDK ────────▶ MinIO                   :9000
  └── POST /internal/v1/ai/executions
      Bearer AI_INTERNAL_SERVICE_TOKEN
      ▼
    ai-server   FastAPI                        :8000
      ├── OpenAI 호환 /chat/completions ──▶ 모델 provider
      ├── 법제처 Open API ────────────────▶ law.go.kr/DRF
      ├── KOSIS 국가통계 API
      ├── DART 전자공시 API
      └── Tavily 웹 검색
```

### 금지된 연결 — 코드로 강제된다

| 금지 | 어떻게 막나 |
|---|---|
| 프론트 → AI 서버 직접 호출 | AI 서버에 **브라우저용 public endpoint가 없다** |
| AI 서버 → DB / Object Storage | AI 서버에 **드라이버·SDK 자체가 없다** |
| 파일 bytes·presigned URL·JWT를 AI에 전달 | AI 서버가 받는 것은 **Spring이 추출한 텍스트뿐** |
| AI가 프롬프트·원본 응답을 결과에 실어 보내기 | `rejectForbiddenFields()`가 결과 JSON을 재귀 순회하며 `storageUrl`·`objectKey`·`presignedUrl`·`localPath`·`fileBytes`·`base64`·`prompt`·`rawProviderResponse`·`credential`을 발견하면 거부 |

> ⚠ **마지막 줄은 전역 장치가 아니다.** `FORBIDDEN_FIELDS` 검사는
> `journey/MarketResearchWorker.java`와 `journey/TwinSurveyWorker.java` **2곳에만** 있다.
> 다른 TaskType은 이 검사를 받지 않는다.
> AS_BUILT는 "`TaskRunWorker.rejectForbiddenFields()`가 한다"고 적었지만
> **`TaskRunWorker` 클래스는 존재하지 않는다.**

> 발표 포인트: **"경계를 문서로 약속한 게 아니라 구조로 막았다."**
> AI 서버에 DB 드라이버를 아예 안 넣은 것이 가장 강한 형태의 강제다.
> 단, 위 ⚠처럼 **일부는 모듈별로만 강제된다**는 점을 함께 말해야 정확하다.

---

## 2. compose 구성 (6개 서비스)

`compose.yaml` — healthcheck 의존 체인으로 순서가 강제된다.

```
postgres → minio → minio-init → ai-server → backend → frontend
```

| 서비스 | 이미지/빌드 |
|---|---|
| `postgres` | postgres:17-alpine |
| `minio` | minio RELEASE.2025-09-07 |
| `minio-init` | mc (버킷 초기화 1회성) |
| `ai-server` | build (FastAPI :8000) |
| `backend` | build (Spring :8080) |
| `frontend` | build (:3000) |

**외부로 노출되는 포트는 `frontend` 하나뿐**이다. 나머지는 내부망에만 있다.

필수 환경변수 (없으면 compose가 뜨지 않는다):
`AI_PROVIDER` · `AI_API_KEY` · `AI_MODEL` · `AI_INTERNAL_SERVICE_TOKEN` ·
`JWT_SECRET` · `POSTGRES_PASSWORD` · `MINIO_ROOT_PASSWORD` · `TAVILY_API_KEY` · `OPENAI_API_KEY`

추가 compose 2종: `compose.infrastructure.yaml`(postgres+minio만), `compose.e2e.yaml`.

---

## 3. AI 실행의 뼈대 — TaskRun

**모든 AI 호출은 예외 없이 `TaskRun` 한 줄을 남긴다.**
**Spring이 유일한 상태 소유자**이고 AI 서버는 상태를 갖지 않는 동기 실행기다.

### 3-1. 도메인 3층

| 엔티티 | 소유하는 것 |
|---|---|
| `TaskRun` | 업무 요청 1건과 **최종 상태**. 입력 스냅샷·입력 해시·멱등키·correlation |
| `TaskAttempt` | 개별 실행 1회. claim token · lease · timeout · 오류 |
| `TaskResult` | 검증 결과. **`ADOPTED` 또는 `REJECTED`** |

상태 전이:
`TaskRunState` = QUEUED → READY → RUNNING → SUCCEEDED / FAILED / TIMED_OUT / CANCELLED

### 3-2. ⭐ 채택은 정확히 한 번

`TaskRunService.adopt()`가
`state==RUNNING && finalResultId==null && attemptId==currentAttemptId`
셋을 모두 확인하고, 어긋나면 결과를 `REJECTED`로 저장한 뒤 `LATE_OR_DUPLICATE_RESULT`로 실패시킨다.

> **네트워크 모호성으로 AI 실행이 중복될 수 있음을 전제로 설계돼 있다.**
> "타임아웃이 났는데 사실은 성공했다"는 상황에서 결과가 두 번 반영되는 것을 막는다.

### 3-3. 실행 한 사이클

```
① 도메인 서비스     taskInput(key, text)      입력 텍스트를 chunk 배열 JSON으로 포장
                     hasher.hash(...)          CanonicalInputHasher — 정규화 해시
                     taskRuns.create(...)      멱등/중복 검사 후 TaskRun INSERT
② 클레임             TaskRunService.claim()    → TaskAttempt 생성, claimToken 발급
③ 실행 시작 표시     startExecution()          → attempt RUNNING
④ HTTP 호출          POST /internal/v1/ai/executions
⑤ 응답 검증          도메인별 validator + 봉투 검증
⑥ 채택 or 거부       adopt() / rejectAndFail()
⑦ 도메인 반영        XxxPersistenceService.complete(...)   ← 별도 트랜잭션
```

**④는 DB 트랜잭션 밖에서 돌아야 한다.**
`TransactionSynchronizationManager.isActualTransactionActive()`를 확인하고 켜져 있으면 예외를 던진다.
그래서 ①②③⑥⑦이 각각 짧은 트랜잭션으로 쪼개져 있다.

> ⚠ **이 가드도 전역이 아니다.** 코드에 실재하는 곳은 **3개뿐**이다 —
> `journey/MarketResearchWorker.java` · `journey/TwinSurveyWorker.java` ·
> `journey/TwinSurveyStimulusDraftService.java`.
> 나머지 경로는 **규율로만 지켜지고 있다.** 발표에서 "구조로 막았다"고 일반화하면 과장이다.

### 3-4. 실행 방식은 **하나** — 모듈마다 자기 워커

`@Scheduled` 폴러를 가진 **모듈 전용 워커**가 자기 TaskType을 집어
`claim → execute → validate → adopt`를 하고, 화면은 폴링한다. 현재 **워커 10개**:

`IdeaBriefDerivationWorker` · `ConceptPortfolio{,Continuation,Selection}Worker` ·
`ConceptFactoryWorker` · `ConceptSelectionActionWorker` · `MarketResearchWorker` ·
`TechOpsProposalWorker` · `FinancialEstimateWorker` · `TwinSurveyWorker` · `MarketingContentWorker`

⚠ **공용 `TaskRunWorker` 클래스는 존재하지 않는다.** 그래서 **결과 검증은 각 워커가 자기 안에서** 한다.
새 TaskType을 붙이면서 검증을 안 만들면 **AI 호출은 성공하고 결과만 조용히 버려진다.**

> ⚠ `AS_BUILT`는 오래 *"패턴이 A(동기 인라인)·B(워커)·C(인메모리 배치) 셋"*이라고 적고
> `JourneyAiService` · `PersonaJourneyService` · `MarketingReportJourneyService` ·
> `ConceptJourneyService` · `conceptEligibilityExecutor`를 예로 들었다.
> **그 다섯 개는 전부 존재하지 않는다.** → `99_MISSING_MATERIALS.md` **X-11**
>
> **발표에서 "실행 패턴이 세 가지"라고 말하면 안 된다.**

공용으로 남은 것은 `taskrun/service/TaskRunWorkerContext` 하나인데, 실행기가 아니라
**"TaskRun 영속 컨텍스트가 살아 있는 동안 떠 둔 불변 스칼라 스냅샷"** 레코드다.
트랜잭션 밖에서 AI를 부르기 위해 필요한 값을 미리 복사해 두는 장치다.

### 3-5. 다시 부르지 않고 복구하는 경로

`TaskResult`가 `ADOPTED`인데 도메인 run이 미완료면 **AI를 다시 호출하지 않고 결과만 재반영**한다
(`synchronize` → `materialize`). 지우면 재실행 비용이 샌다.

⚠ `AS_BUILT`가 예로 든 `JourneyAiService.recoverAdoptedResult()`와 `LegalPrecheckService.current()`는
**존재하지 않는다.** 설계 의도는 유효하니 새 모듈도 같은 성질을 갖게 할 것.

---

## 4. Spring ↔ AI 계약 v1

정본: `docs/contracts/INTERNAL_AI_API_V1_CONTRACT.md` + `docs/contracts/fixtures/internal-ai-v1/`

### 응답 성공 봉투는 **정확히 12개 필드**

```
contractVersion, taskType, taskSchemaVersion, taskRunId, taskAttemptId,
correlationId, canonicalInputHash, resultSchemaVersion, result, warnings,
provenance, usage
```

**하나라도 빠지거나 남으면 `RESULT_UNKNOWN_FIELD`로 거부.**
검사가 `Set.copyOf(result.propertyNames()).equals(expected)`라 초과도 부족도 안 된다.

### 양쪽이 강제하는 한계

| 항목 | 값 |
|---|---|
| 요청/응답 JSON | ≤ **2 MiB** (Spring·AI 양쪽) |
| `textContents` 개수 | 1–64 |
| content당 chunk | 1–64, **총합도 64 이하** |
| chunk 텍스트 | 1–16,384자 (Spring은 16,000 코드포인트로 자름) |
| `maxAttempts` | 1–20 |
| `deadlineAt` | `…Z` 형식, **미래여야 함** |
| `X-Correlation-Id` 헤더 | 본문 `correlationId`와 **일치해야 함** |

### ⭐ canonical input hash — 양쪽이 독립 계산해 대조

Spring `CanonicalInputHasher` ↔ AI `executions.py canonical_hash`

1. 해시 대상은 `{contractVersion, input, locale, taskSchemaVersion, taskType}` **5개뿐**
2. 객체 키는 **NFC 정규화 후 코드포인트 순 정렬**
3. 문자열도 NFC 정규화
4. 구분자 공백 없음
5. 숫자는 **BigDecimal로 정규화** — 후행 0 제거, 지수표기 제거, `-0` → `0`.
   **거부되는 것은 비유한(NaN·Infinity)뿐이다**

> ⚠⚠ **"부동소수점 금지"는 사실이 아니다.** `AS_BUILT §4` 규칙 5와 `CLAUDE.md` §5 규칙 2가
> *"task input에 부동소수점 금지 — canonical hash가 거부한다"*고 적고 있으나 **현재 코드는 허용한다.**
> - `CanonicalInputHasher.canonicalNumber()`는 `isFloatingPointNumber() && !Double.isFinite()`
>   일 때만 던진다 → **유한 소수는 통과**
> - 메서드 주석: *"finite JSON numbers are interpreted as decimal values"* — **의도적 지원**이다
> - AI 서버 오류 메시지도 `"canonical JSON with finite numbers and unique normalized keys"`
> - 문서가 인용한 `"floating-point JSON numbers are not canonical task input"` 문자열은
>   **코드에 없다**(grep 0건). git 이력상 과거에 있었고 이후 제거됐다
>
> → `99_MISSING_MATERIALS.md` **X-09**.

> 발표 포인트: 같은 입력이면 양쪽이 같은 해시를 낸다. **"AI가 받은 것이 우리가 보낸 것과 같다"를
> 신뢰가 아니라 계산으로 확인한다.**

---

## 5. AI 서버 내부 (실제 파일 기준)

```
ai/main.py                     FastAPI 앱. 2MiB 미들웨어 · 중복 키 거부 · 예외 핸들러
 ├ app/api/executions.py       POST /internal/v1/ai/executions   ← 여정 AI의 전부
 ├ app/api/financial.py        재무 모듈
 └ app/api/errors.py           오류 정규화
app/services/journey_provider.py   프롬프트 로드 → provider 호출 → Pydantic 검증
app/tasks/**                   TaskType별 태스크 구현
app/research/research2/**      시장조사 엔진
app/research/bm/**             BM 분석 (노트북 이관본 8파일)
app/twin/**                    패널 트윈 조사
app/legal/**                   법제처 Open API 실연동
ai/prompts/<task_folder>/{system.md,user.md}
```

⚠ `AS_BUILT §5`가 적은 `app/api/tasks.py` · `app/api/marketing.py` ·
`app/services/task_service.py` · `banner_service.py`는 **현재 존재하지 않는다.**

### provider 설정

- `AI_PROVIDER`는 `openai` 또는 `openai-compatible`만 허용
- `temperature=0.1`, `response_format={"type":"json_object"}` **고정**
- 등록 라이브러리: `openai 2.38.0`, `httpx`, `trafilatura 2.2.0` (본문 추출)

### ⭐ Mock이 없다

`.env.example`이 못 박고 있다 — `AI_FIXTURE_MODE=false`.
**키가 없으면 `DEPENDENCY_UNAVAILABLE` / `AI_CONFIGURATION_INVALID`로 실패하지,
가짜 결과를 만들지 않는다.** 이것은 사고가 아니라 명시적 결정이다.

---

## 6. 오류가 사용자에게 도달하는 경로

```
provider 오류 → ProviderFailure(code, reason, status, retryable)
             → HTTP 4xx/5xx + {"error": {...}}
             → ExecutionFailure → TaskRunService.mapPublic()
             → BusinessException → 프론트
```

| 내부 | 공개 |
|---|---|
| `PAYLOAD_TOO_LARGE` | `PAYLOAD_TOO_LARGE` |
| `DEADLINE_EXCEEDED` | `TASK_TIMEOUT` |
| `INVALID_REQUEST`, `UNSUPPORTED_*`, `RESULT_SCHEMA_INVALID` | `AI_RESULT_INVALID` |
| 그 외 | `AI_SERVICE_UNAVAILABLE` |
| (예외) `AI_CONFIGURATION_INVALID` | 그대로 — **키 미설정을 운영자가 구분할 수 있게** |

**provider 응답 원문과 비밀값은 절대 사용자에게 나가지 않는다.** 진단은 서버 로그로만.

---

## 7. 배포 — 있는 것과 없는 것 (정직하게)

| | 실재 | 비고 |
|---|---|---|
| 로컬 실행 | ✅ `compose.yaml` 6서비스 | |
| CI | ✅ `.github/workflows/ci.yml` — frontend·ai·backend **3잡 병렬** | |
| **CD / 배포 자동화** | ❌ **없음** | workflow는 ci.yml 하나뿐, deploy job 없음 |
| **AWS / Terraform / k8s** | ❌ **없음** | manifest·IaC 파일 자체가 없다 |
| 보안 스캔 설정 | ✅ `.gitleaksignore`, `.trivyignore.yaml` | |

`docs/architecture/DEPLOYMENT_ARCHITECTURE.md`는 `TARGET_CANONICAL` 상태이며
"배포 자동화 · secret manager · 수평 scaling · observability"를 **미구현 목표**로 명시한다.

> ⚠ **PIILOT 덱에는 AWS 3-AZ 아키텍처 그림이 있었다. 우리에겐 그 그림이 없다.**
> 없는 것을 그리면 안 된다. 별첨에서 **"현재(compose+CI) / 목표(클라우드)"를 나눠** 그린다.

---

## 8. 지뢰 10선 (별첨용)

1. ~~부동소수점 input 금지~~ → **거짓.** 유한 소수는 허용된다(§4). 다만 Java와 Python이
   **각자 정규화**하므로 표현이 갈리면 해시가 어긋날 수 있다 — 위험은 "금지"가 아니라 "정규화 불일치"다
2. **모듈마다 자기 워커** — 결과 검증을 안 넣으면 AI 호출은 성공하고 결과만 버려진다.
   공용 `TaskRunWorker` 클래스는 **존재하지 않는다**
3. **AI 호출은 트랜잭션 밖** — 서비스에 `@Transactional`을 통째로 붙이면 런타임 예외
4. **결과 필드 집합은 정확히 일치** — 프롬프트가 필드를 하나 더 만들어도 전체 거부.
   **프롬프트와 validator는 항상 같이 고친다**
5. `X-Correlation-Id` 헤더 ≠ 본문 `correlationId` → 400
6. `deadlineAt`은 미래여야 함 — 테스트에서 고정 시각을 쓰면 `DEADLINE_EXCEEDED`
7. `AiTaskType` ≠ `TaskType` (이름만 같은 다른 enum)
8. AI가 ID를 돌려주는 task는 **보낸 ID와 대조**한다 (환각 방지)
9. 로그 인코딩: Java/gradle = CP949, Python = UTF-8 — 잘못 읽으면 오진
10. PowerShell 5.1은 BOM 없는 `.ps1`을 ANSI로 읽는다 → 스크립트에 한글 경로 리터럴 금지
