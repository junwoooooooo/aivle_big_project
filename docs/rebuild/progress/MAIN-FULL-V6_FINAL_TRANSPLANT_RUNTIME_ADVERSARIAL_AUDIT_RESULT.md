# MAIN-FULL-V6 최종 이식·런타임 적대적 감사 결과

작성일: 2026-08-13  
판정 기준: 과거 V3/V4/V5 보고서가 아니라 현재 Git tree, 실제 호출 경로, 실행한 테스트

## 1. REMOTE BASELINE

| 항목 | 값 | 판정 |
|---|---|---|
| branch | `full` | PASS |
| START/HEAD | `b4704c9fd536c7b9d2d9f143651d34b2f07a2856` | origin/full과 동일 |
| origin/main | `ad7304756ba0845d6077a720fa083ac702a33811` | donor authority |
| origin/full | `b4704c9fd536c7b9d2d9f143651d34b2f07a2856` | target baseline |
| ahead/behind | `0 / 0` | PASS |
| 시작 worktree | `.env.example` stat/줄바꿈 표시 외 내용 diff 없음 | 기존 내용 손실 없음 |
| 금지 작업 | merge/cherry-pick/reset/restore/clean/push/commit | 실행하지 않음 |

## 2. 결론

사용자가 Docker Product 실행에서 관측한 약 301초 Market 실패는 재현 가능한 런타임 계약 회귀였다. 원인은 `MARKET_RESEARCH`가 300초 generic HTTP client를 사용하고, worker 예산도 donor main의 20분에서 6분으로 축소되어 있던 점이다.

이번 변경은 AI 검색·증거·scorer·retry를 줄이지 않고 다음처럼 복구했다.

```text
MARKET_RESEARCH
  -> Market 전용 RestClient(22분)
  -> worker deadline 20분
  -> lease 22분
  -> exact Research2 engine
  -> 20초 best-effort heartbeat
  -> TaskRun/JobEvent/SSE/Market 화면
```

무료 결정론 검증은 통과했다. 그러나 이 Codex 셸에는 Docker CLI가 없어서 실제 PostgreSQL/Flyway, MinIO, Backend↔AI 프로세스 경계, 유료 fresh Market 실행은 검증하지 못했다. 따라서 `ZERO GAP`, `COMPLETE`, `FULLY MIGRATED`를 선언하지 않는다.

## 3. AI CORE HASH MATRIX

| AI core | main blob | full blob | 판정 |
|---|---|---|---|
| `ai/app/research/pipeline.py` | `10137caae90556a96b6248bbff2c049c786d2772` | 동일 | EXACT |
| `ai/app/research/research2/run.py` | `17f9bd8c2c66d5cfd0ddf8d02b687294e97368ce` | 동일 | EXACT |
| `ai/app/concept_portfolio_v2/engine.py` | `74e5b495ef927c414354d069ee0c4ff6cadde238` | 동일 | EXACT |
| `ai/app/tasks/tech_ops_advisor/service.py` | `b09494bd6dce8304ef98ebaa08e7d2b2e09d86b5` | 동일 | EXACT |
| `ai/app/legal/registry.py` | `8cca0aa32fb0414d88c0a9ee6f73a48b6a6e81bb` | 동일 | EXACT |
| `ai/app/twin/bank.py` | `c589df10caeffa1ea0fbd56990bd3cb2da752472` | 동일 | EXACT |

위 frozen core는 수정하지 않았다. Market 시간을 맞추기 위해 검색 샘플, evidence, scorer, PDF parsing, retry를 축소하지 않았다.

## 4. MAIN/FULL AI SEMANTIC DIFF MATRIX

| 차이 영역 | full 호출 경로/증거 | 분류 | 설명 |
|---|---|---|---|
| `api/executions.py` | `/internal/v1/ai/executions` → TaskRun별 adapter | ARCHITECTURAL_REPLACEMENT_PROVEN | full progress, Finance report, Marketing Visual, TechOps, Product Market 및 artifact persistence를 결합 |
| `research/product_pipeline.py`, `product_runner.py` | canonical CPV2 → exact `research.pipeline` → materializer | FULL_RUNTIME_WRAPPER | 이번 변경은 wrapper에 heartbeat만 추가 |
| `providers/structured.py` | provider 실행 경계 | FULL_STRONGER_PROVEN | timeout override와 redacted logging 보존 |
| BM analyze/flow/serialize | Market 결과 → BM post-validation/materialization | FULL_POST_VALIDATOR | financial handoff/진단 강화 |
| `research/runner.py` | `runpath.read_dir()` | FULL_STRONGER_PROVEN | generated/seed dual-path authority 사용 |
| Finance estimate models/service | typed 3개년, repair, 가격 guard | FULL_STRONGER_PROVEN | donor capability 보존 + 명시 decision/validation 강화; 106개 focused 묶음 포함 |
| Marketing Content models/service/prompt/image | legal-before-image, artifact UUID/JPEG 검증 | FULL_STRONGER_PROVEN | donor 생성 UX + full storage/legal guard |
| Twin runner | exact bank core 외부 progress observer | FULL_RUNTIME_WRAPPER | core hash 동일 |
| main의 direct financial/marketing/tech-ops API | full internal execution endpoint | MAIN_ONLY_RUNTIME / OBSOLETE | 동기 direct endpoint는 활성 app에 mount하지 않고 TaskRun으로 대체 |
| full-only ledger/progress/post-validator/adapter | TaskRun/ObjectStorage/SSE | FULL_ONLY_RUNTIME | full 플랫폼 결속 |
| research2 tool/notebook 및 테스트 차이 | 제품 호출 경로 밖 | DEV_ONLY / QUALITY_RESOURCE | runtime capability로 오분류하지 않음 |

정규화된 전체 AI tree 감사에서 실제 차이 파일을 위 범주 또는 test/fixture 차이로 분류했다. `UNEXPLAINED_DIFF=0`이다. 줄바꿈 차이는 semantic diff로 세지 않았다.

## 5. WORKER BUDGET MATRIX

| Task | main | 변경 전 full | 변경 후 full | 판정 |
|---|---:|---:|---:|---|
| MARKET_RESEARCH budget | 20분 | 6분 | 20분 | main intent 복구 |
| MARKET_RESEARCH lease | 22분 | 8분 | 22분 | `lease > budget` |
| TWIN_SURVEY budget/lease | 12분/15분 | 12분/15분 | 유지 | KEEP_FULL |
| Twin stimulus draft | 별도 단기 실행 | 90초/+2분 | 유지 | KEEP_FULL |

Main 주석의 신규 사업안 `harness + dryrun + collection` 장기 경로와 실제 full Product fresh collection을 근거로 6분 축소를 폐기했다.

## 6. HTTP CLIENT ROUTING MATRIX

| Task class | client | read timeout | 테스트 |
|---|---|---:|---|
| SHORT/default | generic | 30초 | routing test |
| CONCEPT_PORTFOLIO | concept client | 15분 | routing test |
| MARKET_RESEARCH | market-specific client | 22분 | routing + delayed HTTP test |
| TWIN_SURVEY | twin survey client | 14분 | routing test |
| MARKETING_CONTENT_GENERATION | long client | 7분 | routing test |

Generic timeout을 20분으로 올리지 않았다. Market과 짧은 task의 운명 공유를 제거했다.

## 7. TIMEOUT MATRIX

| 계층 | 값 | 관계/판정 |
|---|---:|---|
| HTTP connect | 3초 기본 | 연결 실패를 빠르게 dependency unavailable로 판정 |
| generic read | 30초 | 짧은 task용 |
| marketing long read | 7분 | 5분 worker deadline보다 큼 |
| Market read | 22분 | 20분 worker deadline보다 큼 |
| Market deadline | 20분 | donor main 의미 복구 |
| Market lease | 22분 | deadline보다 큼 |
| Frontend guidance | 22분 | worker/lease보다 먼저 거짓 실패 안내 금지 |

Main의 공용 long client 420초는 Market 20분 budget보다 짧아 내부적으로도 모순이다. Full은 Market 전용 22분 client를 분리하여 이 모순을 해소했다.

## 8. RETRY/CANCELLATION MATRIX

| 항목 | 현재 의미 | 판정 |
|---|---|---|
| AI error `retryable`/`retryAfterMs` | 기존 strict envelope 검증 유지 | KEEP_FULL |
| read/response timeout | `DEADLINE_EXCEEDED` | 복구 |
| connect/refused/DNS/TLS | `DEPENDENCY_UNAVAILABLE` | 복구 |
| 기타 RestClient failure | dependency failure | 유지 |
| TaskRun deadline | worker가 20분 deadline을 internal request에 전달 | PASS |
| lease | claim 시 22분 | PASS |
| cancellation | 기존 TaskRun/Attempt 계약 유지 | 변경 없음 |
| heartbeat callback failure | AI 실행을 실패시키지 않고 삼킴 | PASS |

## 9. PROGRESS EVENT MATRIX

| 단계 | 구현 | 사용자 observable |
|---|---|---|
| AI wrapper | 시작 뒤 20초마다 `MARKET_COLLECTION/HEARTBEAT/RUNNING` | 거짓 SEARCHING 등 세부 stage를 만들지 않음 |
| Backend internal callback | `/internal/v1/ai/task-progress` | safe detail만 JobEvent로 변환 |
| JobEvent/SSE | `job.market.trace` | 기존 재연결/404 stop 계약 유지 |
| Market page | active taskRun의 최신 trace 표시 | “시장 근거 수집을 계속 진행하고 있습니다.” |
| Work Center | 기존 message registry의 `traceDetail` 표시 | 동일 event 소비 |

## 10. MARKET LIVE PATH MATRIX

| 경로 | 결과 | 근거 |
|---|---|---|
| CPV2 canonical input → Product adapter | PASS | focused AI tests |
| exact Research2 engine | PASS | blob hash + orchestration tests |
| generated run read path | PASS | `runpath.read_dir/complete` tests |
| Market 전용 transport | PASS | 실제 delayed local HTTP 축소 테스트 |
| result materialization | PASS | Market Product tests |
| raw ledger artifact persistence/restore/recollect | PASS(결정론) | V5 ledger artifact tests 포함 106/106 |
| 실제 Docker fresh Market A | 미실행 | UNVERIFIED_ENVIRONMENT |
| Market A → MinIO commit → Market B recollect | 미실행 | UNVERIFIED_ENVIRONMENT |

## 11. ENV LOCAL CONFIG MATRIX

| 영역 | 계약 | 상태 |
|---|---|---|
| General structured AI | `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL` | `.env.example` 분리 문서화 |
| Market Research2 | `MARKET_RESEARCH_OPENAI_API_KEY` 우선, `OPENAI_API_KEY` 호환 | 분리 문서화 |
| Custom OpenAI base URL | Responses API와 `web_search` 지원 필요 | README/.env.example 명시 |
| Compose timeout | short/long/Market/CP/Twin 별도 env | compose/application 일치 |
| local precheck | `python scripts/check_local_env.py --compose` | 값 대신 SET/MISSING/EXISTS만 출력 |
| env 자동 감사 | required/example/compose/direct-run | 5개 gate 모두 0 |

자동 감사 결과: `UNDECLARED_REQUIRED=0`, `UNPASSED_REQUIRED=0`, `UNKNOWN_ENV_USAGE=0`, `UNDOCUMENTED_DIRECT_RUN=0`, `NONEMPTY_PLACEHOLDER=0`.

## 12. OBJECT STORAGE MATRIX

| 항목 | 정적/결정론 검증 | live 검증 |
|---|---|---|
| V24 ledger artifact schema | 존재 | PostgreSQL 미실행 |
| manifest/allowlist/checksum/path traversal/size | test 통과 | MinIO 미실행 |
| ownership/project/concept/revision binding | test 통과 | MinIO 미실행 |
| temp 삭제 후 restore/recollect | test 통과 | 프로세스+MinIO 미실행 |
| generated local output Git 보호 | `.gitignore`, `.dockerignore`, tracked file 0 | image context 미실행 |

## 13. POSTGRES/DOCKER MATRIX

| 검증 | 결과 | 이유 |
|---|---|---|
| `Get-Command docker` | 없음 | Codex 셸에 Docker CLI 미노출 |
| `docker compose config` | 미실행 | UNVERIFIED_ENVIRONMENT |
| Docker build/up/health | 미실행 | UNVERIFIED_ENVIRONMENT |
| PostgreSQL Flyway V1→V24 | 미실행 | Docker/PostgreSQL 미가용 |
| Hibernate `ddl-auto=validate` | 미실행 | 동일 |
| MinIO actual cycle | 미실행 | 동일 |
| Backend↔AI 실제 HTTP process boundary | 미실행 | 동일 |

## 14. TEST MATRIX

| 영역 | 실행 | passed | failed | skipped | 판정 |
|---|---|---:|---:|---:|---|
| AI compileall | `python -m compileall app` | PASS | 0 | 0 | PASS |
| AI 전체 pytest | `pytest tests -q` | 664 | 4 | 1 | 4건 baseline/donor 동일 |
| AI V4/V5/V6 focused | 12개 파일 | 106 | 0 | 0 | PASS |
| Backend compileJava/compileTestJava | Gradle | PASS | 0 | 0 | PASS |
| Backend P0 transport/runtime | 4 classes | 9 | 0 | 0 | PASS |
| Backend Market package | package 묶음 | 26 | 0 | 0 | PASS |
| Backend TaskRun/JobEvent 핵심 | 4 classes | 16 | 0 | 0 | PASS |
| Backend ledger/local ObjectStorage | 2 classes | 7 | 0 | 0 | PASS |
| Backend 전체 | `gradlew test` | 완주 못함 | 미집계 | 미집계 | 종료 scheduler/binary result 문제, PARTIAL |
| Frontend Market/async focused | 4 files | 16 | 0 | 0 | PASS |
| Frontend production build | Vite, 261 modules | PASS | 0 | 0 | PASS(큰 chunk 경고) |
| 변경 frontend lint | 5 files | PASS | 0 | 0 | PASS |
| Frontend 전체 lint | ESLint | - | 10 errors | - | baseline 직접 재현 안 됨 |
| Frontend 전체 test | 80 files/442 tests | 424 | 18 | 0 | 2개 파일 실패, baseline 직접 재현 안 됨 |
| env contract audit | script | 5 gates | 0 | 0 | PASS |
| git diff --check | Git | PASS | 0 | 0 | PASS |

## 15. PRE_EXISTING PROOF

| 실패 | baseline full | donor main | current | 판정 |
|---|---|---|---|---|
| CPV2 strict `seed.fields` 4건 | 별도 `origin/full` archive에서 동일 4건 실패 | donor checkout에서 동일 4건 실패 | 동일 4건 실패 | PRE_EXISTING |
| Frontend 전체 lint 10건 | 직접 실행 안 함 | 직접 실행 안 함 | 실패 | PRE_EXISTING으로 분류하지 않음 |
| Frontend 전체 test 18건 | 직접 실행 안 함 | 직접 실행 안 함 | 실패 | PRE_EXISTING으로 분류하지 않음 |
| Backend 전체 suite 종료 문제 | baseline 직접 실행 안 함 | 해당 없음 | 완주 못함 | PRE_EXISTING으로 분류하지 않음 |

## 16. REMAINING GAP

| 항목 | 분류 | 닫는 조건 |
|---|---|---|
| 실제 Docker fresh Market 성공 | UNVERIFIED_ENVIRONMENT | 사용자 환경에서 1회 실행 및 20분 내 정상 완료 증거 |
| PostgreSQL V1→V24 + ddl validate | UNVERIFIED_ENVIRONMENT | clean DB migration/startup |
| MinIO ledger commit/restore/recollect | UNVERIFIED_ENVIRONMENT | 실제 object cycle |
| Backend↔AI 실제 process-boundary contract | UNVERIFIED_ENVIRONMENT | container HTTP smoke |
| Backend 전체 test suite | PARTIAL | 종료 hanging/result binary 문제를 분리해 전 클래스 집계 |
| Frontend 전체 test/lint | PARTIAL | baseline 재현 후 기존/신규 분류 및 오류 해소 |
| 유료 fresh Market evidence/artifact/current version | UNVERIFIED_ENVIRONMENT | 사용자 승인하에 단 1회 기록 |

## 17. 최종 분류

| 분류 | 값 | 설명 |
|---|---:|---|
| MISSING | 0 | 이번에 감사한 P0 런타임 계약에서 구현 누락 없음 |
| PARTIAL | 2 | Backend 전체 suite, Frontend 전체 suite/lint |
| REGRESSED | 0 | 301초 timeout 회귀는 코드와 결정론 테스트 기준 복구됨; live 확인은 별도 UNVERIFIED |
| UNVERIFIED_ENVIRONMENT | 5 | Docker, PostgreSQL, MinIO, process HTTP, fresh paid Market |
| PRE_EXISTING | 4 tests | CPV2 실패를 baseline/full/donor에서 직접 재현 |
| EXACT | 6 frozen core files | 위 hash matrix |
| FULL_STRONGER | 5 capability groups | Market-specific timeout, provider guard, BM, Finance, Marketing |
| UNEXPLAINED_DIFF | 0 | AI semantic diff를 wrapper/post-validator/stronger/dev-only로 분류 |

완료 선언은 보류한다. 실제 Docker Product Market 성공과 live storage/migration 검증이 남아 있다.
