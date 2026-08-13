# MAIN-FULL-RESYNC-V4-RECOVERY-CONTINUE 최종 보고서

## 0. 기준

- `MAIN_SHA=ad7304756ba0845d6077a720fa083ac702a33811`
- `FULL_START_SHA=b81e1b95cf6b22b706d363d0dcaf2f39c9232d25`
- `RECOVERY_CHECKPOINT_SHA=4bafefe9710e95abe7584e1a63e3ec97154701bf`
- `DONOR_MAIN_SHA=ad7304756ba0845d6077a720fa083ac702a33811`
- branch: `full`
- 시작 worktree: clean
- checkpoint는 `origin/full`보다 1 commit 앞선 정상 WIP였다.
- `.env`, API key, Twin Bank는 열람하지 않았다. merge/cherry-pick/reset/restore/clean/push는 수행하지 않았다.

## 1. 기존 체크포인트 보존 감사

| 항목 | 현재 증거 | 판정 |
|---|---|---|
| public Market engine | `ai/app/research/pipeline.py` blob이 main과 `10137c...`로 동일 | EXACT |
| full Market adapter | `product_pipeline.py`가 CPV2 immutable input/BM/TaskRun 경계를 담당 | FULL_RUNTIME_WRAPPER |
| generated ledger P0 | `_read_result()`가 `runpath.read_dir()` 사용, generated-only deterministic test 통과 | 복구 유지 |
| run path authority | main `read_dir`, `complete`, `quarantine_partial` 사용 | EXACT/WRAPPED_EXACT |
| quality resource | `expected.md`, `viewer.html`, `runlog.py`, `verdict.py` donor exact | EXACT |
| assumption rule | product 전용 engine profile 제거, 안전성은 `product_runner` post-validator에 위치 | FULL_POST_VALIDATOR |
| TechOps engine | advisor models/service, scaler, external evidence donor exact | EXACT |
| TechOps provenance | `BM.USER_CONFIRMED_TECH_OPS.*` 경계 후 public fact를 `TECH_OPS` / `USER_CONFIRMED_OR_ACCEPTED`로 복원 | WRAPPED_EXACT |
| PostgreSQL tests | V23 및 competitor seed table/FK/CHECK/index expectation 존재 | 정적 계약 유지, live 미검증 |

## 2. V3/V4 기존 결론을 반증해 발견한 항목

| 기존 0-gap 주장과 충돌한 항목 | 발견 | 조치 | 재발 방지 |
|---|---|---|---|
| fresh generated-run read path | generated write 후 seed `runs/` 직접 read | checkpoint에서 `runpath.read_dir()` authority로 복구됨을 재검증 | run path 13건 + V4 gate |
| Market orchestration 축약 | dynamic/inline/consistency/recollect/partial recollect가 product path에서 축약됐었음 | exact main pipeline + 외부 full adapter 구조 유지 확인 | envelope 37건 |
| prereg quality resource | `expected.md` 누락으로 `미측정` 가능 | donor exact resource 유지 확인 | prereg test |
| TechOps source 오표기 | confirmed TechOps input이 scaler에서 BM fact로 보일 수 있었음 | adapter path와 result provenance 복원 유지 확인 | 실제 scaler/basisId test |
| 사용자 실패 taxonomy | `serialize.py`에서 `extract_capped`, `fetch_empty` 전용 설명이 누락 | donor 두 분기를 exact 복구하고 full `financialHandoff` 확장은 유지 | V4 회귀 test 2건 추가 |

## 3. AI FILE HASH MATRIX

전체 지정 tree의 Git blob 비교 결과:

| 구분 | 파일 수 | 분류 |
|---|---:|---|
| 공통 byte-exact | 359 | EXACT |
| 공통 whitespace-only | 4 | EXACT(줄바꿈/공백 차이 제외) |
| 공통 semantic diff | 20 | 아래 분류로 모두 설명됨 |
| main-only | 1 | `app/models/financial.py`, full Finance report/TaskRun 계약으로 대체 |
| full-only | 22 | runtime wrapper, post-validator, diagnostics, 강화 task/test |
| unexplained | 0 | 완료 조건 충족 |

핵심 blob:

| 파일 | MAIN | FULL | 판정 |
|---|---|---|---|
| `research/pipeline.py` | `10137caae905...` | 동일 | EXACT |
| `research2/expected.md` | `ba3a8c3e2d84...` | 동일 | EXACT |
| `research2/runlog.py` | `452614f1f4bc...` | 동일 | EXACT |
| `research2/service/verdict.py` | `c7a7a9baae4e...` | 동일 | EXACT |
| TechOps advisor models | `f1c609a4ff6a...` | 동일 | EXACT |
| TechOps advisor service | `b09494bd6dce...` | 동일 | EXACT |
| TechOps scaler service | `55cdb85404c0...` | 동일 | EXACT |
| TechOps evidence service | `932133d1e147...` | 동일 | EXACT |

Semantic diff 분류:

| 파일군 | 분류 | 근거 |
|---|---|---|
| `providers/structured.py` | FULL_STRONGER_PROVEN | timeout override 0 처리와 secret redaction 수정, provider tests |
| `research/bm/analyze.py`, `flow.py`, `serialize.py` | ARCHITECTURAL_REPLACEMENT_PROVEN | validation diagnostics와 Finance handoff 추가; main 진단/caveat/not-found 의미 보존 |
| `research/runner.py` | FULL_RUNTIME_WRAPPER | package import 및 generated ledger read-path 수정 |
| Finance estimate models/service | FULL_STRONGER_PROVEN | exact 1/2/3 years, bounded repair, typed count/churn, price/economic guard |
| Marketing content 4파일 | FULL_STRONGER_PROVEN | legal-before-image, UUID artifact path, JPEG/result 검증, prompt 보강 |
| Twin `__init__.py`, `runner.py` | FULL_RUNTIME_WRAPPER | main survey 알고리즘에 progress observer만 추가 |
| 공통 test/fixture 7파일 | FULL_STRONGER_PROVEN/ARCHITECTURAL_REPLACEMENT_PROVEN | 위 강화 계약과 full runtime의 동등성 검증 |
| full-only 22파일 | FULL_RUNTIME_WRAPPER/FULL_POST_VALIDATOR/FULL_STRONGER_PROVEN | Product adapter, BM diagnostics, Finance report, Marketing visual, TechOps adapter와 해당 tests |

## 4. AI ORCHESTRATION PARITY

| capability | MAIN | FULL | 테스트/판정 |
|---|---|---|---|
| dynamic collection | main pipeline | exact public engine | envelope PASS |
| inline concept | `_inline_concept` | exact | PASS |
| concept/run consistency | ledger concept 검증 | exact | PASS |
| sourceRun/dual path | `runpath.read_dir` | exact + adapter | PASS |
| complete/quarantine | `complete`, `quarantine_partial` | exact 호출 | PASS |
| collect/RESCORE | main entry | exact | PASS |
| recollect/partial slots/slotsFrom | main engine | engine exact | engine PASS / Product persistence PARTIAL |
| plan/constraints/budget/degradation/failure grade | main pipeline | exact | PASS |
| immutable CPV2/BM | main에 없음 | `product_pipeline.py` | FULL_RUNTIME_WRAPPER |
| arbitrary-product safety | main rule 유지 | engine 밖 post-validator | FULL_POST_VALIDATOR |

## 5. RUN PATH MATRIX

| 경우 | write | read | 결과 |
|---|---|---|---|
| bundled fixture | `runs/<run>` | `runpath.read_dir()` | PASS |
| fresh run | `runs-generated/<run>` | `runpath.read_dir()` | PASS |
| generated와 seed 동명 | generated 우선 | resolver | PASS |
| incomplete run | generated partial | `complete=false` 후 quarantine | PASS |
| Product subprocess | temporary workspace generated ledger | main engine materialize 후 envelope 반환 | PASS |
| 다음 Product TaskRun recollect | 이전 raw ledger 필요 | temporary workspace 종료 시 소멸 | PARTIAL |

## 6. TECHOPS PROVENANCE MATRIX

| fact | engine path/source | public source/status | basis 추적 | 결과 |
|---|---|---|---|---|
| Market | `MARKET.*` / MARKET | MARKET / CONFIRMED_FACT | factId 유지 | PASS |
| BM | `BM.businessModel.*` / BM | BM / CONFIRMED_FACT | factId 유지 | PASS |
| confirmed TechOps | `BM.USER_CONFIRMED_TECH_OPS.*` / engine 내부 BM | TECH_OPS / USER_CONFIRMED_OR_ACCEPTED | advice basisId 동일 | PASS |

## 7. QUALITY RESOURCE MATRIX

| resource/guard | 결과 |
|---|---|
| `expected.md` + `prereg_stamp` | exact, 측정 PASS |
| `viewer.html` | exact |
| failure vocabulary/not-found/envelope/run-path | PASS |
| `extract_capped` / `fetch_empty` 사용자 진단 | 이번 recovery에서 복구, 2 tests PASS |
| assumption product profile | 제거 유지; main rules exact |
| arbitrary-product numeric post-validator | PASS |
| design score 직접 script | 17 PASS / 2 FAIL: `--runs`용 ignored generated ledger 부재 |

## 8. BACKEND SEMANTIC MATRIX

| 모듈 | canonical input | execution/persistence | lineage/error/recovery | 판정 |
|---|---|---|---|---|
| Market | CPV2 selection + non-stale seed | TaskRun/current | exact concept/source hash, stale/wrong-source fail-closed | EQUIVALENT |
| BM | current Market FULL + exact concept | TaskRun/current | plan/constraints/legal/Finance handoff | EQUIVALENT |
| TechOps | current Concept+Market+BM+confirmed input | TaskRun/Attempt/SSE + report history | ownership/hash/stale/retry/provenance | FULL_STRONGER |
| Finance | current Market+BM exact lineage, TechOps 비의존 | estimate/report TaskRun + snapshot | explicit proposal decision, deterministic/Monte Carlo 유지 | FULL_STRONGER |
| Twin | current sources | TaskRun/SSE/current/history | unsupported guard/recovery | FULL_STRONGER |
| Marketing | CPV2 source + same-project artifact | TaskRun/ObjectStorage/revision | legal-before-image, MIME/size/path/ownership | FULL_STRONGER |

## 9. LIVE MIGRATION RESULT

- Docker executable: 없음.
- PostgreSQL client binary: 있음.
- PostgreSQL server resource `C:/Program Files/PostgreSQL/17/share/postgres.bki`: 없음.
- PostgreSQL service: 없음.
- 따라서 clean PostgreSQL V1→V23, `ddl-auto=validate`, competitor seed live table/FK/CHECK/index는 **UNVERIFIED_ENVIRONMENT**다.
- 환경 설치는 사용자 지침에 따라 시도하지 않았다.

## 10. E2E RESULT

| 경로 | 무료 deterministic 증거 | 결과 |
|---|---|---|
| Market CPV2→input factory→Product adapter→generated ledger→materialize | Backend Market 26 tests + Product/run-directory/bridge 17 tests + P0 path gate | PASS(구성요소 통합) |
| Market FULL→BM fake provider→handoff | Market→BM bridge tests | PASS |
| Concept+Market+BM+confirmed TechOps→real scaler→fake structured provider→basis | TechOps advisory tests | PASS |
| Backend↔AI 실제 HTTP | 서비스 미기동 | 미검증 |
| Docker/MinIO/provider/browser journey | 환경/유료 호출 미사용 | 미검증 |

## 11. TEST RESULT

| 분류 | 파일/클래스 | passed | failed | skipped | 비고 |
|---|---:|---:|---:|---:|---|
| AI compileall | app tree | PASS | 0 | 0 | exit 0 |
| AI 전체 pytest | 659 tests | 654 | 4 | 1 | 4건은 donor main에서도 동일 재현된 CPV2 seed wrapper 실패 |
| AI V4 Market gate | 1 file | 6 | 0 | 0 | 신규 taxonomy 2건 포함 |
| Frontend 영향 영역 | 32 files | 156 | 0 | 0 | Market/TechOps/Finance/Marketing/async-events |
| Frontend 전체 | 78 files | 418 | 18 | 0 | V4 frontend diff=0; routing/auth 기존 실패 |
| Backend 비-PostgreSQL | 119 classes | 463 | 2 | 0 | Concept/Idea 2건 단독 재현, V4 관련 diff=0 |
| Backend PostgreSQL tag | 7 classes | 0 | 0 | 7 classes 미실행 | 환경 미확보 |
| Research design-score script | 19 assertions | 17 | 2 | 0 | ignored generated fixture 부재 |

빌드/정적 검사:

- Backend `compileJava`: PASS
- Backend `compileTestJava`: PASS
- Frontend production build: PASS(대형 chunk warning)
- Frontend lint: FAIL 10(V4 frontend diff=0)
- `git diff --check`: 최종 gate에서 별도 확인

독립 명령 exit/duration:

| 명령 | exit | duration | 결과 |
|---|---:|---:|---|
| AI `compileall app` | 0 | 0.9s | PASS |
| AI `pytest tests -q` | 1 | 17.2s(테스트 15.26s) | 654/4/1 |
| Backend `compileJava` | 0 | 14.0s | PASS |
| Backend `compileTestJava` | 0 | 13.6s | PASS |
| Backend Market/BM | 0 | 19.7s | 26/0/0 |
| Backend TechOps | 0 | 18.6s | 23/0/0 |
| Backend Finance | 0 | 18.6s | 36/0/0 |
| Backend Marketing | 0 | 19.0s | 28/0/0 |
| Backend module | 0 | 18.2s | 12/0/0 |
| Backend TaskRun | 0 | 105.3s | 47/0/0 |
| Backend 나머지 pipeline | 1 | 111.6s | 202/2/0 |
| Backend 비-jobevent 잔여 | 0 | 172.5s | 52/0/0 |
| Backend JobEvent/SSE | 0 | 105.6s | 37/0/0 |
| Frontend build | 0 | 3.4s | PASS |
| Frontend lint quiet | 1 | 11.5s | 10 errors |
| Frontend 전체 test | 1 | 33.6s(테스트 31.40s) | 418/18/0 |
| Frontend 영향 test | 0 | 14.5s(테스트 12.34s) | 156/0/0 |

## 12. REMAINING GAP

| 분류 | 수/상태 | 내용 |
|---|---|---|
| MISSING | 최종 확정 보류 | live migration/runtime 미검증 때문에 0 선언 금지 |
| PARTIAL | 1 | Product Market raw ledger가 TaskRun 이후 보존되지 않아 다음 실행 recollect 불가 |
| REGRESSED | 무료/정적 검증 범위에서 발견 없음 | live 범위는 미확정 |
| UNEXPLAINED_AI_DIFF | 0 | 모든 semantic diff 분류 완료 |
| UNVERIFIED_ENVIRONMENT | 2 | PostgreSQL/Flyway live, Docker compose/runtime |
| PRE_EXISTING failures | AI 4, Backend 2, Frontend 18, lint 10 | V4 diff 0 또는 donor 동일 재현 증거 있음 |
| QUALITY_RESOURCE_GAP | 1 | design-score `--runs` fixture 부재로 2 assertion 실패 |

따라서 이번 결과는 **ZERO GAP 또는 V4 COMPLETE가 아니다.**
