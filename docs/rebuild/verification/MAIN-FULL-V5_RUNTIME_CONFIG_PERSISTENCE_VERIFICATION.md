# MAIN-FULL-V5 검증 행렬

## 1. BASELINE MATRIX

| Branch/tree | SHA | 판정 |
|---|---|---|
| full start | `eb1f54cd756debc62737eb05678f66426539a8c0` | 작업 기준 |
| origin/full | `eb1f54cd756debc62737eb05678f66426539a8c0` | 시작 시 일치 |
| origin/main | `ad7304756ba0845d6077a720fa083ac702a33811` | donor authority |
| donor-main | `ad7304756ba0845d6077a720fa083ac702a33811` | origin/main 일치 |

## 2. FROZEN AI CORE HASH MATRIX

`git hash-object --path`로 line-ending filter를 적용한 blob ID다.

| AI core | full | main | 판정 |
|---|---|---|---|
| `research/pipeline.py` | `10137caa` | `10137caa` | EXACT |
| `research2/runpath.py` | `31968597` | `31968597` | EXACT |
| `research2/runlog.py` | `452614f1` | `452614f1` | EXACT |
| `research2/service/verdict.py` | `c7a7a9ba` | `c7a7a9ba` | EXACT |
| `research2/expected.md` | `ba3a8c3e` | `ba3a8c3e` | EXACT |
| `tech_ops_advisor/models.py` | `f1c609a4` | `f1c609a4` | EXACT |
| `tech_ops_advisor/service.py` | `b09494bd` | `b09494bd` | EXACT |
| `tech_ops_input_scaler/service.py` | `55cdb854` | `55cdb854` | EXACT |
| `tech_ops_external_evidence/service.py` | `932133d1` | `932133d1` | EXACT |

V5 diff에서 frozen subtree 변경은 0이다. 변경된 AI 파일은 execution diagnostic 및 Product runtime
wrapper와 신규 artifact adapter뿐이다.

## 3. MAIN/FULL RESIDUAL CAPABILITY MATRIX

| main-only 그룹 | full 대응 | 분류 | 근거 |
|---|---|---|---|
| main synchronous Finance API/model/prompt | pipeline Finance TaskRun, deterministic/Monte Carlo/report | FULL_STRONGER | current Market/BM lineage와 명시적 proposal decision 유지 |
| main `journey/MarketResearch*` | `pipeline/market` + exact AI engine + TaskRun | ARCHITECTURAL_REPLACEMENT_PROVEN | product call path와 focused/integration tests |
| main `journey/TwinSurvey*` | full Twin TaskRun/current/history/runtime | ARCHITECTURAL_REPLACEMENT_PROVEN | full runtime authority 유지 |
| main synchronous TechOps controller/service | full TechOps preparation/snapshot/report worker | ARCHITECTURAL_REPLACEMENT_PROVEN | exact advisor core, provenance tests |
| main standalone advisory UI | active full TechOps result UI | EQUIVALENT | main visible fields가 full active route에 결합됨 |
| main legacy finance/market migrations | full immutable V1~V23 schema 의미 | OBSOLETE | migration 번호 충돌을 복사하지 않음 |
| main async event API 파일 | full shared async-events/SSE | FULL_STRONGER | JobEvent sequence/current refresh/reconnect guard |
| main smoke/docs/mockups | full smoke/contract/verification 문서 | DEV_ONLY/EQUIVALENT | production callable이 아닌 자료는 구조별 대응 |

V5 runtime residual에서 새 `MISSING`, `PARTIAL`, `UNEXPLAINED_DIFF`는 발견하지 않았다. 이 결론은
live runtime PASS를 의미하지 않으며 아래 환경 미검증과 분리한다.

## 4. MARKET RECOLLECT PERSISTENCE MATRIX

| 요구 | 구현/테스트 | 결과 |
|---|---|---|
| TaskRun A ledger persistence | AI bundle → Backend stage → ObjectStorage → DB | PASS (unit/integration) |
| temp workspace 삭제 | round-trip test에서 삭제 후 bundle 검증 | PASS |
| TaskRun B restore | 원본 A 계보 metadata로 `runs-generated` 복원 | PASS |
| exact recollect 호출 | restored `runpath.complete` 후 donor pipeline `_full`에 전달 | PASS |
| partial slots | `slots=S1,S2` 전달 | PASS |
| `slotsFrom` | `source/current` validation 및 `current` 전달 | PASS |
| generated 우선 read | runpath generated-first invariant | PASS |
| seed read | exact runpath fallback 유지 | PASS |
| incomplete original | `complete` fail-closed / partial quarantine invariant | PASS |
| corrupt manifest/file/hash/path | AI+Backend 검증 | PASS |
| missing artifact/object | Backend fail-closed | PASS (unit) |
| wrong project/concept/version | Backend start/download + AI lineage 검증 | PASS (unit) |
| stale concept/current version | start gate | PASS (service logic + compile/tests) |
| interrupted upload | DB pointer 미생성 | PASS |
| failed run staged cleanup | object delete 후 DB staged row delete | PASS |
| process restart | DB/ObjectStorage authority, local workspace 비의존 | PASS (deterministic reconstruction) |
| concurrent reads | independent streams parallel read | PASS |
| actual MinIO restart | 실제 container 없음 | UNVERIFIED_ENVIRONMENT |

## 5. ARTIFACT MANIFEST MATRIX

| 필드/가드 | 상태 |
|---|---|
| `artifactContractVersion` | 구현 |
| sourceRun/project/concept | 구현 |
| concept snapshot hash | 구현 |
| canonical input hash | 구현 |
| source/current TaskRun·Attempt 분리 | 구현 |
| Market source version identity | DB binding + recollect output manifest에 구현 |
| asOf/createdAt | 구현 |
| file name/size/SHA-256 | 구현 |
| manifest hash | 구현 |
| engine identifier | 구현 |
| path traversal/allowlist/max bytes | 구현 |
| secret/token 저장 금지 | manifest builder allowlist상 저장하지 않음 |
| STAGED→COMMITTED atomic authority | 구현 |

## 6. ENV CONTRACT MATRIX

전체 변수별 행렬은 [MAIN-FULL-V5_ENV_CONTRACT_MATRIX.md](MAIN-FULL-V5_ENV_CONTRACT_MATRIX.md)에 있다.

| 요약 gate | 값 |
|---|---:|
| UNDECLARED_REQUIRED | 0 |
| UNPASSED_REQUIRED | 0 |
| UNKNOWN_ENV_USAGE | 0 |
| UNDOCUMENTED_DIRECT_RUN | 0 |
| NONEMPTY_PLACEHOLDER | 0 |

## 7. UNDECLARED/UNPASSED ENV MATRIX

| 유형 | 이름 | 결과 |
|---|---|---|
| required 미선언 | 없음 | 0 |
| required Compose 미전달 | 없음 | 0 |
| direct-run 미문서화 | 없음 | 0 |
| unknown usage | 없음 | 0 |

## 8. DOCKER BUILD CONTEXT PROTECTION MATRIX

| 대상 | `.gitignore` | `ai/.dockerignore` | 검증 |
|---|---|---|---|
| Research2 runs-generated | 차단 | 차단 | static PASS |
| Research2 outputs | 차단 | 차단 | static PASS |
| noncanonical seed runs | 차단 | 전체 runs 차단 | static PASS |
| 실제 Twin Bank | 차단 | 차단 | static PASS |
| `.env`, `.env.*` | 차단(example 예외) | 차단 | static PASS |
| Docker sentinel image 검사 | 해당 없음 | Docker 부재 | UNVERIFIED_ENVIRONMENT |

`git check-ignore --verbose --stdin`으로 generated/output/noncanonical sentinel 경로와 canonical seed
exception을 확인했다.

## 9. COMPOSE CONFIG MATRIX

| 검증 | 결과 |
|---|---|
| required variable 정적 계약 | PASS |
| E2E synthetic Twin mount | 구성됨 |
| Backend↔AI token 이름 | 일치 |
| PostgreSQL/MinIO 변수 정적 연결 | PASS |
| `docker compose config` | UNVERIFIED_ENVIRONMENT — Docker CLI 없음 |
| E2E merged config | UNVERIFIED_ENVIRONMENT — Docker CLI 없음 |

## 10. POSTGRESQL LIVE MIGRATION MATRIX

| 검증 | 결과 |
|---|---|
| latest migration inventory | V24 신규, 기존 V1~V23 불변 |
| migration test expectation | V1~V24, applied 23개로 갱신 |
| clean `initdb` | 실패: 설치본에 `share/postgres.bki` 없음 |
| Flyway V1→V24 live | UNVERIFIED_ENVIRONMENT |
| `ddl-auto=validate` startup | UNVERIFIED_ENVIRONMENT |
| V24 table/FK/CHECK/UNIQUE/index live | UNVERIFIED_ENVIRONMENT |

## 11. BACKEND↔AI HTTP MATRIX

| 계약 | 무료 테스트 | 실제 process boundary |
|---|---|---|
| 내부 token | Backend client/AI API tests | UNVERIFIED_ENVIRONMENT |
| taskRun/attempt/correlation/input hash | execution contract tests | UNVERIFIED_ENVIRONMENT |
| Market artifact upload/download | fake HTTP transport + controller/service/worker tests | UNVERIFIED_ENVIRONMENT |
| 오류/크기/schema mapping | 기존 contract tests | UNVERIFIED_ENVIRONMENT |

## 12. OBJECT STORAGE E2E MATRIX

| 검증 | 결과 |
|---|---|
| key/content-type/size/checksum | unit PASS |
| ownership/source binding | unit PASS |
| missing/corrupt object | unit/AI validation PASS |
| STAGED/COMMITTED DB pointer | integration PASS |
| 실제 MinIO upload→download→restore | UNVERIFIED_ENVIRONMENT |

## 13. TEST RESULT MATRIX

| Suite | Files/classes | Passed | Failed | Skipped | 판정 |
|---|---:|---:|---:|---:|---|
| AI 전체 | - | 660 | 4 | 1 | PRE_EXISTING 4 |
| AI V5 orchestration subset | - | 62 | 0 | 0 | PASS |
| Backend 비-PostgreSQL 분할 전체 | 123 | 472 | 2 | 0 | PRE_EXISTING 2 |
| Backend Market+Worker 최종 재검증 | 8 | 27 | 0 | 0 | PASS |
| Frontend 전체 | 79 | 420 | 18 | 0 | PRE_EXISTING 18 |
| Frontend Market focused | 19 | 105 | 0 | 0 | PASS |
| Frontend build | 260 modules | - | 0 | - | PASS |
| Frontend 전체 lint | - | - | 10 | - | PRE_EXISTING 10 |
| 변경 Market 파일 lint | 5 | - | 0 | - | PASS |
| PostgreSQL tagged | 15 tests | - | - | - | UNVERIFIED_ENVIRONMENT |

## 14. PRE_EXISTING FAILURE PROOF MATRIX

| 실패군 | current | V5 baseline | donor main | 판정 |
|---|---|---|---|---|
| AI CPV2 legacy seed schema 4개 | 동일 실패 | 동일 실패 | 동일 실패 | PRE_EXISTING |
| Backend Concept retry NPE + Idea route 400 | 동일 2개 | 별도 worktree에서 동일 2개 | 해당 없음 | PRE_EXISTING |
| Frontend App/Auth 18개 | 동일 실패 | 별도 worktree에서 동일 18개 | 해당 없음 | PRE_EXISTING |
| Frontend lint 10개 | 동일 오류 | 별도 worktree에서 동일 10개 | 해당 없음 | PRE_EXISTING |

## 15. REMAINING GAP MATRIX

| 분류 | 항목 | 수 |
|---|---|---:|
| MISSING | 정적/call-path 감사에서 확인된 항목 없음 | 0 |
| PARTIAL | 코드 구현 기준 없음 | 0 |
| REGRESSED | V5 신규 regression 없음 | 0 |
| UNVERIFIED_ENVIRONMENT | Docker config/build/runtime, PostgreSQL/Flyway, MinIO cycle, 실제 Backend↔AI HTTP | 4개 군 |
| PRE_EXISTING | AI, Backend, Frontend test, Frontend lint | 4개 군 |
| FULL_STRONGER | TaskRun/current-lineage/ObjectStorage/JobEvent 기반 main 대체군 | 4개 군 |
| EXACT | 대표 frozen AI core hash | 9개 |
| UNEXPLAINED_DIFF | 없음 | 0 |

환경 미검증이 남아 있으므로 `ZERO GAP`, `COMPLETE`, `DONE`을 선언하지 않는다.
