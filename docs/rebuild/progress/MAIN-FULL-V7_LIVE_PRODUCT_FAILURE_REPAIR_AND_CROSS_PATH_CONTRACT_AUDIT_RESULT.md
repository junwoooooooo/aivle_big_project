# MAIN-FULL-V7 LIVE 제품 실패 복구 및 교차 경로 계약 감사 결과

작성일: 2026-08-13  
Authority: 현재 `origin/full` 코드, `origin/main` donor, 사용자가 제공한 Docker LIVE 로그, 이번에 실행한 실제 seam 테스트

## A. REMOTE SHA

| 항목 | 값 | 판정 |
|---|---|---|
| branch | `full` | PASS |
| START/HEAD | `1ae29f456c31dbf0d0544bb5ee8c9b9731046e11` | origin/full과 동일 |
| origin/full | `1ae29f456c31dbf0d0544bb5ee8c9b9731046e11` | Target authority |
| origin/main | `ad7304756ba0845d6077a720fa083ac702a33811` | Donor authority |
| ahead/behind | `0 / 0` | PASS |
| 시작 worktree | clean | PASS |
| 금지 작업 | reset/restore/clean/merge/cherry-pick/commit/push | 실행하지 않음 |

## B. LIVE FAILURE ROOT CAUSE

| 모듈 | LIVE 증상 | 코드상 root cause | 복구 |
|---|---|---|---|
| Market | V6 timeout 경계를 넘어 AI 200 및 current materialization | V6에서 Market 전용 transport/budget 복구 | 사용자 LIVE 관측상 PASS 후보; V7 AI core 변경 없음 |
| TechOps | 즉시 `TypeError` | adapter는 async 3-인자 sink, 실제 `SafeTaskProgressSender.emit`은 sync one-dict | adapter를 공식 one-dict sync 계약으로 변경 |
| Finance report | provider 400 `response_format` | `safeFailureReason`이 property지만 required가 아니고 `default:null` 포함 | explicit required null + Literal, 공통 offline strict preflight |
| Marketing | TaskRun 전 `CANONICAL_INPUT_HASH_MISMATCH` | enqueue hash `2.0`, TaskRun 저장/재검증/AI 수락 `1.0` | donor main과 같은 `1.0` 복구 |
| Twin precheck | 올바른 변수 설정 후에도 label `TWIN_BANK_PATH MISSING` | consumer는 HOST_DIR지만 출력/실패 key만 PATH | `TWIN_BANK_HOST_DIR`로 통일, PATH-only는 명시 실패 |

## C. CALLBACK CONTRACT MATRIX

| Producer | Consumer | payload/signature | 오류 격리 | 판정 |
|---|---|---|---|---|
| CPV2 observer | `ProductionProgressSender.emit` | sync `ProductionTraceEvent` | sender/observer best-effort | ALIGNED |
| Product Market | `SafeTaskProgressSender.emit` | sync `dict` | sender queue/HTTP failure best-effort | ALIGNED |
| Twin | `SafeTaskProgressSender.emit` | sync `dict` | `_observe`와 sender best-effort | ALIGNED |
| TechOps runtime adapter | `SafeTaskProgressSender.emit` | sync `dict(stage/action/status/safeSummary)` | transport failure는 sender가 격리; programming TypeError는 숨기지 않음 | FIXED/ALIGNED |
| Finance | progress sink 없음 | N/A | TaskRun terminal/error contract | N/A |
| Marketing Content | progress sink 없음 | N/A | TaskRun/JobEvent worker contract | N/A |

Whole-repo 검색 결과 `await sink(...)` 형태는 제거됐다. `CALLBACK_SIGNATURE_MISMATCH=0`.

TechOps 단계는 `SCALING → EVIDENCE → GENERATING → VALIDATING`이다. 실제 sender와 API 경계를 태운 테스트가 네 이벤트와 advisory 결과를 함께 검증한다.

## D. SCHEMA VERSION MATRIX

| 범위 | hash-time | TaskRun stored | Internal request | AI accepted | 판정 |
|---|---|---|---|---|---|
| Idea Brief | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| CPV2 run/continue/selection | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| Concept Factory child tasks | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| TechOps proposal/advisory | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| Finance estimate/report | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| Marketing Content/Visual | 1.0 | 1.0 | 1.0 | 1.0 | FIXED/ALIGNED |
| Market/BM Product | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| Twin survey/stimulus | 1.0 | 1.0 | 1.0 | 1.0 | ALIGNED |
| orchestration-only parent types | 1.0 | 1.0 | 직접 AI 요청 없음 | N/A | ALIGNED |

`TaskRun.create`의 정본은 1.0이며 AI `/internal/v1/ai/executions`도 1.0만 수락한다. Backend 전체 canonical hasher call을 machine test로 검사했다. `UNEXPLAINED_SCHEMA_VERSION_DIFF=0`.

## E. CANONICAL HASH MATRIX

| seam | 검증 | 결과 |
|---|---|---|
| Marketing POST-equivalent create | 실제 `MarketingContentService.create` 호출 | PASS |
| source/request canonical input | 실제 저장 JSON 사용 | PASS |
| service hash | `MARKETING_CONTENT_GENERATION/1.0/ko-KR` | PASS |
| `TaskRunService.create` 재계산 | 동일 hasher 정본 | PASS |
| stored hash | 재계산 hash와 exact | PASS |
| stored state/schema | `QUEUED`, `1.0` | PASS |
| 동일 idempotency key/input | 동일 TaskRun replay | PASS |

Hash 검증 삭제·완화·무시는 하지 않았다.

## F. STRUCTURED PROVIDER SCHEMA MATRIX

| 호출군 | schema | offline strict | 비고 |
|---|---|---|---|
| CPV2 plan/candidate/semantic batches | 8개 model schema | PASS | closed/fully-required |
| Concept candidate/redesign/distinctness/hypothesis | model schema | PASS | repair도 동일 schema |
| Concept legal review | provider model + runtime evidence-index schema | PASS | repair도 동일 runtime schema |
| Legal source route/screening | Routing/Screening schema | PASS | retry/repair 동일 |
| Regulatory boundary normalization | `json_object` + Pydantic post-validation | N/A | strict `response_schema` 미사용 |
| Twin stimulus | DraftProviderResult | PASS | strict schema |
| Finance estimate | FinanceEstimateResult | PASS | bounded repair 유지 |
| Finance analysis report | FinanceAnalysisReportResult | FIXED/PASS | 8 properties 모두 required |
| Marketing Content | MarketingContentResult | PASS | legal-before-image 유지 |
| TechOps proposal | TechOpsProposalResult | PASS | strict schema |
| TechOps advisory | `json_object` + AdvisoryResult validation | N/A | main exact engine 유지 |
| Idea Brief | IdeaBriefProviderResult | PASS | strict schema |

Finance 최종 schema:

- root `type=object`
- `additionalProperties=false`
- 8개 property와 8개 required exact
- `source=const(AI_GENERATED_REPORT)`
- `providerStatus=const(SUCCEEDED)`
- `safeFailureReason=required type:null`
- `default` 없음

공통 `execute_structured_prompt`는 HTTP 전에 incompatible schema를 fail-closed한다. mock provider로 strict 수락, provider 400 response-format 거절, malformed JSON, valid JSON을 검증했다. `UNEXPLAINED_STRUCTURED_SCHEMA_INCOMPATIBILITY=0`.

## G. ENV VARIABLE MATRIX

| 변수 | 선언 | consumer | precheck label | 분류 |
|---|---|---|---|---|
| `TWIN_BANK_HOST_DIR` | `.env.example`, e2e example | Compose bind source | `TWIN_BANK_HOST_DIR` | DECLARED_AND_CONSUMED |
| `TWIN_BANK_DIR` | direct-run section | AI container/runtime bank loader | compose가 HOST_DIR을 DIR로 mount | DIRECT_RUN_ONLY |
| `TWIN_BANK_PATH` | 미선언 | production consumer 없음 | unknown/deprecated note 후 실패 | ALIAS가 아님 |
| General AI env | example/compose/application | structured provider | SET/MISSING만 출력 | DECLARED_AND_CONSUMED |
| Market key | dedicated 또는 OPENAI compatibility | Research2 | `MARKET_KEY` | DECLARED_AND_CONSUMED |

경로 테스트: absolute Windows path, quoted path, repo-relative path, missing path, regular file, PATH-only, HOST_DIR 정상. 값 자체는 출력하지 않는다.

자동 env 감사 결과:

```text
UNDECLARED_REQUIRED=0
UNPASSED_REQUIRED=0
UNKNOWN_ENV_USAGE=0
UNDOCUMENTED_DIRECT_RUN=0
NONEMPTY_PLACEHOLDER=0
MISLABELED_PRECHECK=0
ALIAS_CONFLICT=0
```

전체 matrix는 `docs/rebuild/verification/MAIN-FULL-V7_ENV_CONTRACT_MATRIX.md`에 생성했다.

## H. TECHOPS RESULT

| 항목 | 결과 |
|---|---|
| main exact advisory/scaler/validator core | 변경 0 |
| runtime adapter callback | FIXED |
| actual Safe sender signature | PASS |
| 실제 internal executions API seam | PASS |
| main engine 진입 | mock provider/evidence를 사용한 실제 engine orchestration 도달 확인 |
| 단계 이벤트 | 4개 순서/내용 PASS |
| programmer TypeError mapping | `INTERNAL_ERROR / UNEXPECTED_INTERNAL_ERROR` PASS |
| Docker live advisory | UNVERIFIED |

## I. FINANCE RESULT

| 항목 | 결과 |
|---|---|
| deterministic calculation/Monte Carlo/BEP/cash flow | 변경 0 |
| 400 root cause | generated schema의 required/default strict 불일치로 확정 |
| strict schema | FIXED/PASS |
| Pydantic post-validation | schema와 동일 의미 |
| malformed result | `RESULT_SCHEMA_INVALID / AI_RESULT_INVALID` |
| deterministic fallback | 유지 |
| mock provider paths | accepted/rejected/malformed/valid PASS |
| live paid provider | UNVERIFIED |

## J. MARKETING RESULT

| 항목 | 결과 |
|---|---|
| donor main schema hash | 1.0 확인 |
| full hash version | 2.0 → 1.0 복구 |
| 실제 Service→TaskRunService→TaskRun | PASS |
| QUEUED/hash/schema/idempotency | PASS |
| worker regression | PASS |
| Docker public POST | UNVERIFIED |

## K. TWIN RESULT

| 항목 | 결과 |
|---|---|
| Compose canonical env | `TWIN_BANK_HOST_DIR` |
| AI container env | `TWIN_BANK_DIR` |
| precheck output | `TWIN_BANK_HOST_DIR EXISTS/MISSING` |
| PATH-only behavior | fail + rename 안내 |
| secret/path value 출력 | 없음 |
| Docker mount | UNVERIFIED |

## L. TEST MATRIX

| 영역 | 실행 | passed | failed | skipped | 판정 |
|---|---|---:|---:|---:|---|
| AI focused V7 | 5 files | 45 | 0 | 0 | PASS |
| AI compileall | `python -m compileall app` | PASS | 0 | 0 | PASS |
| AI full pytest | `pytest tests -q` | 699 | 4 | 1 | 기존 CPV2 fixture 4건 |
| Backend compile | compileJava/compileTestJava | PASS | 0 | 0 | PASS |
| Backend Marketing/hash/schema | 4 classes | 9 | 0 | 0 | PASS |
| Backend Finance/TechOps/progress/JobEvent | 12 classes | 42 | 0 | 0 | PASS |
| Frontend TechOps/Finance/Marketing/async | 23 files | 72 | 0 | 0 | PASS |
| Frontend build | Vite 261 modules | PASS | 0 | 0 | chunk-size warning |
| Frontend scoped lint | 기능 디렉터리 | - | 6 | - | V7 frontend diff 0; baseline 직접 재실행하지 않아 PRE_EXISTING 표기 안 함 |
| env contract audit | machine audit | PASS | 0 | 0 | PASS |
| git diff --check | Git | PASS | 0 | 0 | PASS |

AI의 4개 CPV2 실패는 이번 V7에서 별도 `origin/full` archive를 생성해 같은 두 테스트 파일을 직접 실행했으며, baseline에서도 53 passed / 동일 4 failed로 재현된 `seed.fields` 구형 fixture 실패다. V7은 해당 frozen core와 테스트를 변경하지 않았다.

## M. LIVE/UNVERIFIED MATRIX

| 경로 | 상태 | 근거/필요 검증 |
|---|---|---|
| Market fresh Product | LIVE PASS 후보 | 사용자 로그에서 AI 200/current materialization 관측; V7 미재실행 |
| TechOps advisory | UNVERIFIED | Docker에서 1회 실행 필요 |
| Finance provider report | UNVERIFIED | 사용자 승인형 provider smoke + UI 1회 |
| Marketing public create | UNVERIFIED | Docker public POST/UI 1회 |
| Twin host mount | UNVERIFIED | precheck 후 compose config/up 확인 |
| Docker/PostgreSQL/MinIO | UNVERIFIED_ENVIRONMENT | Codex 셸에 Docker CLI 없음 |

## N. REMAINING GAP

| 분류 | 수 | 항목 |
|---|---:|---|
| MISSING | 0 | 이번 LIVE 네 실패의 코드 수정 누락 없음 |
| PARTIAL | 1 | Frontend scoped lint 6건; V7 diff는 없으나 baseline 직접 재현하지 않음 |
| REGRESSED | 미선언 | Docker live 재검증 전 0 선언 금지 |
| UNVERIFIED_ENVIRONMENT | 5 | TechOps, Finance provider, Marketing POST, Twin mount, Docker stack |
| PRE_EXISTING | 4 tests | 현재 `origin/full` archive에서 직접 재현한 CPV2 fixture 실패(53 passed / 4 failed) |
| CALLBACK_SIGNATURE_MISMATCH | 0 | whole-repo signature inventory |
| UNEXPLAINED_SCHEMA_VERSION_DIFF | 0 | source inventory + machine test |
| UNEXPLAINED_STRUCTURED_SCHEMA_INCOMPATIBILITY | 0 | offline schema matrix |

## 변경 파일

- TechOps runtime/API: `runtime_adapter.py`, `executions.py`
- Finance provider contract: `finance_analysis_report/models.py`, provider preflight
- Marketing hash: `MarketingContentService.java`
- Twin/env: `check_local_env.py`, README, env matrix
- 회귀 테스트: TechOps actual seam, Finance/schema provider, Twin paths, Marketing actual TaskRun seam, Task schema inventory
- 사용자 승인형 smoke: `finance_report_provider_smoke.py`

## 의도적으로 생략한 검증

- 실제 `.env` 값 열람
- 유료 OpenAI provider 호출
- Docker compose build/up
- 실제 TechOps/Finance/Marketing/Twin 사용자 여정

Codex 셸에서 Docker CLI가 발견되지 않았고 유료 호출은 사용자 승인 대상이므로 생략했다.

## 남은 위험 및 정확한 continuation point

`docs/rebuild/verification/MAIN-FULL-V7_LIVE_PRODUCT_FAILURE_REPAIR_AND_CROSS_PATH_CONTRACT_AUDIT_USER_VERIFICATION.md`의 순서대로:

1. Twin precheck/compose mount 확인
2. TechOps advisory 1회
3. Marketing create 1회
4. Finance provider smoke와 UI report 각 1회

각 실행에서 taskRunId, attemptId, error code, terminal state만 기록하면 된다. 이 네 live gate 전에는 `REGRESSED=0`, `ZERO GAP`, `COMPLETE`를 선언하지 않는다.
