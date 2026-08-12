# MAIN-FULL-RESYNC-V4-ADVERSARIAL-AUDIT 결과

## 1. 기준선

- `MAIN_SHA=ad7304756ba0845d6077a720fa083ac702a33811`
- `FULL_SHA=b81e1b95cf6b22b706d363d0dcaf2f39c9232d25`
- `DONOR_MAIN_SHA=ad7304756ba0845d6077a720fa083ac702a33811`
- branch=`full`, HEAD=`origin/full`, 시작 worktree clean
- `.env`, API key, Twin Bank는 열람하지 않았다.
- merge, cherry-pick, commit, push는 수행하지 않았다.

## 2. V3 0-gap 판정을 반증한 항목

| 항목 | V4 재현 | 조치 | 현재 판정 |
|---|---|---|---|
| fresh Market run path | `runpath.read_dir()`는 `runs-generated`를 찾지만 기존 full `_read_result()`는 `runs`를 직접 읽어 실패 | 모든 ledger read를 `runpath.read_dir()` authority로 교체 | 복구 |
| Market orchestration | dynamic concept, inline concept, sourceRun, collect, recollect, partial-slot recollect가 공개 Product 경로에서 축약됨 | main `pipeline.py`를 exact engine으로 복구하고 full immutable 입력은 `product_pipeline.py` wrapper로 분리 | 엔진 복구, Product recollect는 아래 잔여 gap |
| `expected.md` | full에 없어 preregistration이 미측정으로 저하 | donor 파일 exact transplant | 복구 |
| assumption profile | full 전용 profile이 main `runlog`/`verdict` 내부 rule을 변경 | main rule 파일 exact 복구, full 안전성은 `product_runner` post-validator로 이동 | 복구 |
| TechOps provenance | confirmed TechOps input을 BM envelope에 넣어 scaler가 `source=BM`으로 fact화 | path를 `BM.USER_CONFIRMED_TECH_OPS.*`로 명시하고 result adapter가 `TECH_OPS`/`USER_CONFIRMED_OR_ACCEPTED`로 복구 | 복구 |
| V23 migration test | 최신 기대가 V21에 고정 | V22/V23 및 table/FK/CHECK/index assertion 보강 | 정적/컴파일 복구, live 미검증 |
| donor quality resources | `expected.md`, `viewer.html`, failure/not-found/envelope/run-path 테스트 누락 | exact transplant | 복구 |

## 3. AI FILE HASH MATRIX

줄바꿈을 정규화한 전체 tree 비교 결과다. 공통 파일은 모두 해시 비교했고 다른 파일은 아래 분류로 닫았다.

| 영역 | common | exact | different | main-only | full-only | 판정 |
|---|---:|---:|---:|---:|---:|---|
| concept_portfolio_v2 | 21 | 21 | 0 | 0 | 0 | EXACT |
| legal | 10 | 10 | 0 | 0 | 0 | EXACT |
| research | 227 | 219 | 8 | 0 | 5 | 3개 whitespace, 나머지는 Product wrapper/BM diagnostics·handoff/serializer·runpath adapter |
| tasks | 49 | 43 | 6 | 0 | 7 | Finance strict years/repair/guardrail, Marketing legal-before-image/artifact, full TaskRun 전용 모듈 |
| twin | 13 | 11 | 2 | 0 | 0 | progress event wrapper |
| providers | 2 | 1 | 1 | 0 | 0 | timeout override 및 secret redaction fix |
| models | 8 | 8 | 0 | 1 | 0 | main-only `financial.py`는 옛 동기 API 모델이며 full TaskRun report로 대체 |
| tests | 56 | 49 | 7 | 0 | 10 | full runtime/강화 invariant용 adapter 테스트 |

핵심 blob 증거:

| 파일 | MAIN/FULL blob | 판정 |
|---|---|---|
| `app/research/pipeline.py` | `10137caae90556a96b6248bbff2c049c786d2772` | EXACT |
| `research2/expected.md` | `ba3a8c3e2d846e4b9ad368d8716cd5f94d64f710` | EXACT |
| `research2/runlog.py` | `452614f1f4bc762d77e4831c8063b6dbfbc184a6` | EXACT |
| `research2/service/verdict.py` | `c7a7a9baae4ee3ca42d093bc0c3db836c97881a6` | EXACT |
| TechOps models/service | `f1c609...` / `b09494...` | EXACT |
| TechOps scaler/external evidence service | `55cdb8...` / `932133...` | EXACT |

`UNEXPLAINED_DIFF=0`이다. 단 이는 아래 live 및 Product recollect gap을 0으로 만든다는 뜻이 아니다.

## 4. AI ORCHESTRATION PARITY

| capability | main | full | 판정/테스트 |
|---|---|---|---|
| dynamic concept / inline concept | `_inline_concept`, `_concept_file` | exact `pipeline.py` | EXACT, donor envelope tests |
| concept/run consistency | `_concept_path_of`, ledger concept check | exact | EXACT |
| sourceRun dual directory | `runpath.read_dir/complete` | exact + Product wrapper | WRAPPED_EXACT |
| complete semantics | `result.json` 기준 | exact | EXACT |
| partial quarantine | `runpath.quarantine_partial` | Product runner 호출 | WRAPPED_EXACT |
| collect/RESCORE | main entry | exact | EXACT |
| recollect/slots/slotsFrom | `_recollect` → `_full` | engine에는 exact | EXACT engine / PARTIAL Product runtime |
| plan/constraints/budget/degradation/failure grade | main pipeline | exact | EXACT |
| Product immutable CPV2/BM | main에 없음 | `product_pipeline.py` | FULL_RUNTIME_WRAPPER |
| arbitrary-product numeric safety | main assumption rule | post-validator에서 unobserved estimate 비공개 | FULL_POST_VALIDATOR |

## 5. RUN PATH MATRIX

| 경우 | write | read | 결과 |
|---|---|---|---|
| bundled fixture | `runs/<run>` | `runpath.read_dir()` | PASS |
| fresh collection | `runs-generated/<run>` | `runpath.read_dir()` | PASS |
| incomplete fresh run | generated partial | `complete()==false` 후 quarantine | PASS |
| 이전 full 구현 | generated write / `runlog.RUNS_DIR` read | 불일치 | V4에서 제거 |

Deterministic P0 및 Market/BM/TechOps 묶음은 109/109 통과했다.

## 6. TECHOPS PROVENANCE MATRIX

| fact | engine path | engine source | public source/status | basisId |
|---|---|---|---|---|
| Market | `MARKET.*` | MARKET | MARKET / CONFIRMED_FACT | 유지 |
| BM | `BM.businessModel.*` | BM | BM / CONFIRMED_FACT | 유지 |
| confirmed TechOps | `BM.USER_CONFIRMED_TECH_OPS.*` | BM (main scaler 원형) | TECH_OPS / USER_CONFIRMED_OR_ACCEPTED | 유지 |

실제 scaler를 통과한 fact path/source와 post-processing 뒤 advice basisId 보존을 테스트했다.

## 7. QUALITY RESOURCE MATRIX

| resource/guard | 상태 |
|---|---|
| `expected.md` prereg resource | exact, `prereg_stamp` 측정 테스트 PASS |
| `viewer.html` | exact |
| failure vocabulary script | 6/6 PASS |
| not-found/pipeline-envelope/run-path donor tests | 57/57 PASS |
| design-score 직접 품질 스크립트 | 17 PASS / 2 FAIL: ignored generated ledger 부재 |
| assumption product profile | 제거, main rules exact |
| full arbitrary-product guard | engine 밖 post-validator 테스트 PASS |

## 8. BACKEND SEMANTIC MATRIX

| 모듈 | Input/Canonical | Execution/Persistence | Lineage/Error/Recovery | 판정 |
|---|---|---|---|---|
| Market | CPV2 selection + non-stale seed | TaskRun worker/current version | exact concept/source hashes, stale/wrong source fail-closed | EQUIVALENT, P0 path 복구 |
| BM | current Market FULL + same concept | TaskRun + BM current | plan/constraints/legal handoff, current lineage | EQUIVALENT |
| TechOps | current Concept+Market+BM+confirmed snapshot | TaskRun/Attempt/SSE + advisory DB current/history | ownership, input hash, stale/retry/error mapping | FULL_STRONGER |
| Finance | current Market+BM exact lineage, TechOps 비의존 | TaskRun estimate/report + snapshot/current | explicit decision, deterministic/Monte Carlo 유지 | FULL_STRONGER |
| Twin | current product sources | TaskRun/SSE/current/history | unsupported-task guard/recovery | FULL_STRONGER |
| Marketing | current CPV2 source + same-project reference artifact | TaskRun + ObjectStorage + revision/asset | legal-before-image, MIME/size/path/ownership | FULL_STRONGER |

main의 동기 TechOps controller, localStorage, sample fallback은 제품 의미만 full runtime에 이식하고 구현 자체는 `SKIP_TEMP`로 분류했다. Generic `404/JOB_NOT_FOUND` SSE reconnect 중단은 full 코드와 전용 테스트에 존재한다. Navigation 표시 순서는 1 아이디어, 2 사업안, 3 시장, 4 BM, 5 TechOps, 6 Finance, 7 Twin, 8 Marketing으로 확인했다.

## 9. LIVE MIGRATION RESULT

- V23 테스트는 Flyway applied version `22, 23`과 competitor seed table/FK/CHECK/partial unique index를 검증하도록 갱신했다.
- Backend compileJava/compileTestJava: PASS.
- 로컬 PostgreSQL 17 client binary는 있었지만 server share resource `postgres.bki`가 없어 임시 cluster `initdb`가 실패했다.
- Docker executable도 없어 Testcontainers `postgresTest`를 실행할 수 없었다.
- 따라서 V1→V23 live migration 및 `ddl-auto=validate`는 **미검증**이다.

## 10. E2E / TEST RESULT

| 분류 | 결과 |
|---|---|
| AI compileall | PASS |
| AI targeted Market/BM/TechOps | 109 passed, 0 failed |
| AI tests 전체 | 652 passed, 4 failed, 1 skipped |
| AI 4 failures | donor에서도 동일 재현된 CPV2 seed wrapper 기존 계약 불일치 (`PRE_EXISTING`) |
| Backend compile | PASS |
| Backend 영향 패키지 | 45 classes, 165 passed, 0 failed/skipped |
| Backend 전체 | 124초 초과로 미완료; 전체 완료로 계산하지 않음 |
| Frontend 영향 영역 | 36 files, 166 passed |
| Frontend 전체 | 76 files passed / 2 failed; 418 passed / 18 failed. V4 frontend diff=0인 기존 routing/auth 실패 |
| Frontend build | PASS, chunk size warning |
| Frontend lint | FAIL 10; V4 diff=0인 기존 React purity/refresh/test global 위반 |
| `git diff --check` | PASS |
| PostgreSQL/Flyway live | ENVIRONMENTAL 미검증 |
| Docker/compose/MinIO/provider/browser | 미검증 |

## 11. REMAINING GAP

1. `PARTIAL`: Product Market recollect 계약. main 엔진에는 exact capability가 있으나 Backend input factory/UI에 recollect 입력이 없고 TaskRun 종료 후 raw ledger를 canonical ObjectStorage artifact로 보존·복원하는 경로가 없다.
2. `QUALITY_GAP`: 실제 PostgreSQL V1→V23 및 Hibernate validate 미검증.
3. `QUALITY_GAP`: Backend full suite가 도구 시간 제한 안에 완료되지 않았다.
4. `PRE_EXISTING`: AI CPV2 4 failures, Frontend 18 failures, lint 10 errors.
5. `QUALITY_RESOURCE_GAP`: research design-score `--runs` 입력 원장이 gitignored라 2 assertions fail.

최종 수치:

- `MISSING=0`
- `PARTIAL=1`
- `REGRESSED=0`
- `UNEXPLAINED_AI_DIFF=0`
- `LIVE_MIGRATION_UNVERIFIED=1`
- `FULL_BACKEND_UNVERIFIED=1`

따라서 `MISSING=0 / PARTIAL=0 / REGRESSED=0` 또는 V4 COMPLETE를 선언하지 않는다.
