# MAIN-LATEST-FULL-COMPLETE-RESYNC-V3 최종 보고서

## 기준선 및 작업 경계

- `MAIN_SHA=ad7304756ba0845d6077a720fa083ac702a33811`
- `FULL_START_SHA=c6682c4d802d38ec61f8067819db086afb70938b`
- 시작 branch/HEAD: `full` / `c6682c4d802d38ec61f8067819db086afb70938b`
- 시작 worktree: clean
- donor authority는 위 MAIN_SHA로 동결했다.
- merge/cherry-pick/branch 전환/기존 migration 수정은 하지 않았다.
- 실제 `.env`, API key, Twin Bank는 열람·수정하지 않았다.

## A. MAIN → FULL CAPABILITY PARITY

| Capability | Main evidence | Full implementation | Status | Notes |
| --- | --- | --- | --- | --- |
| TechOps advisor | `tech_ops_advisor/models.py`, `service.py` | 동일 3개 blob + runtime adapter | WRAPPED_EXACT | decision, 7 advice, gates, costs, readiness, pilot, layer1/2 보존 |
| TechOps scaler | `tech_ops_input_scaler/**` | 동일 2개 blob | EXACT | 수치·단위·비용 행동 정규화 보존 |
| TechOps external evidence | `tech_ops_external_evidence/**` | 동일 2개 blob | EXACT | Tavily/DART/KOSIS evidence layer 보존 |
| TechOps runtime | main synchronous advisory 의미 | full controller/service/TaskRun/worker/report/SSE | EQUIVALENT | ownership, current source, input hash, retry/history 강화 |
| Market Research2 | `research2/run.py`, rules, prompts, adapters | main core + full product wrapper | WRAPPED_EXACT | run/prompt/schema 주요 blob 일치 |
| Research quality | design score, quote audit, funnel, step 15~18 | 동일 도구·fixture·tests | EXACT | donor 누락 fixture 1건은 별도 부채 |
| selected Concept path | main actual Concept input | CPV2 exact selection + expanded input factory | EQUIVALENT | sample fallback 없음, wrong Concept fail-closed |
| Market/BM planning | BM fields, constraints, seeds | current BM plan과 seed를 product input에 결속 | EQUIVALENT | stale/wrong lineage는 full gate가 차단 |
| competitor seed | main V22/API/UI | full V23/domain/API/UI | EQUIVALENT | migration 번호만 full에 맞게 변환 |
| Finance | main Market/BM source | full current Market FULL + BM exact lineage | FULL_STRONGER | TechOps 무관, aidev UX, explicit proposal decision, hardened report |
| Twin Survey | main engine/backend/frontend | full engine + TaskRun/progress/unsupported guard | FULL_STRONGER | sample fallback 미사용 |
| Marketing | main source/reference/image | CPV2 source + secured artifact + legal-first | FULL_STRONGER | integrated content UI와 revision 보존 |
| generic async 404 guard | main `useJobEvents` fix | full hook/test에 이미 존재 | EXACT | JOB_NOT_FOUND 무한 reconnect 차단 |
| navigation | main 최신 1~8 label | full shell/routes/Work Center | EXACT | 내부 ID rename 불필요 |
| PDF extraction | `pdfplumber==0.11.9` | AI requirements 반영 | EXACT | container install은 live 미검증 |
| dev API proxy | main Vite config | full Vite `/api` proxy | EXACT | test/hook timeout도 반영 |

## B. AI ENGINE PARITY

| AI module | Main engine paths | Full engine paths | 판정 | Tests |
| --- | --- | --- | --- | --- |
| TechOps | `ai/app/tasks/tech_ops_{advisor,input_scaler,external_evidence}` | 동일 경로 + `runtime_adapter.py` | WRAPPED_EXACT | core 7/7 hash match, targeted 포함 |
| Market/BM Research2 | `ai/app/research/research2/**` | 동일 core + `product_runner.py`, assumption adapter | WRAPPED_EXACT | step15~18 135 pass; main run/prompt/schema/tool hash match |
| Concept/Legal | main 관련 tasks | full CPV2/legal production facade | ADAPTED/FULL_STRONGER | full canonical source·ownership 보존 |
| Finance | `finance_estimate/service.py` | full estimate + validator/repair/decision | ADAPTED/FULL_STRONGER | exact years, bounded repair, typed values targeted pass |
| Twin | `ai/app/twin/**` | 동일 engine, progress wrapper 추가 | WRAPPED_EXACT | unsupported guard/recovery targeted audit |
| Marketing | `marketing_content/service.py` | full legal-first/artifact validator wrapper | ADAPTED/FULL_STRONGER | generate/edit/reference/legal contract targeted pass |

### 허용한 AI adapter

- TechOps: main engine은 payload와 provider를 직접 받지만 full은 canonical snapshot payload와 progress callback을 제공한다. adapter는 입력 구조 변환만 수행하며 engine result를 축약하지 않는다.
- Research2: main CLI는 full TaskRun progress option을 알지 못한다. engine 인자를 변형하지 않고 wrapper가 STARTED/FAILED/COMPLETED event를 발행한다.
- Research2 product isolation: generic/local assumption rule이 product run에 섞이지 않도록 full product profile과 SOM fail-closed를 유지했다.
- Finance/Marketing: main blob exact copy는 full의 explicit decision, legal-first, ObjectStorage validation을 제거하므로 적용하지 않았다. main observable capability를 모두 포함한 stronger contract를 유지했다.

## C. BACKEND SEMANTIC PARITY

| Module | Canonical input | Execution | Persistence | Lineage | Errors | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Market | CPV2 selection + non-stale seed | TaskRun worker | current/history | Concept exact | wrong/stale/foreign fail-closed | EQUIVALENT |
| BM | current Market FULL | TaskRun worker | current/history/snapshot | Concept+Market version | mismatch/partial 차단 | FULL_STRONGER |
| TechOps | current Concept+Market+BM+confirmed input+legal | TaskRun worker | report/current/history | input hash와 upstream versions | ownership/stale/provider mapping | FULL_STRONGER |
| Finance | current Market FULL+current BM exact lineage | estimate/report TaskRun | preparation/snapshot/report | TechOps 비포함 | missing/stale/wrong lineage | FULL_STRONGER |
| Twin | supported stimulus/task | TaskRun worker | snapshot/artifact | project/current source | unsupported pre-call 차단 | FULL_STRONGER |
| Marketing | CPV2 source+same-project artifact | TaskRun worker | revision/asset/ObjectStorage | source+artifact binding | MIME/size/path/legal 차단 | FULL_STRONGER |

## D. QUALITY PARITY

| Main quality guard/test | Full equivalent | Status |
| --- | --- | --- |
| Research2 step 15~18 | 동일 scripts/tests/fixtures | EXACT |
| quote-value verification | `quote_audit.py` 동일 blob | EXACT |
| design score | `design_score.py` 동일 blob | EXACT; donor fixture 1개 부재 |
| wrong Concept prevention | full input factory/start gate tests | FULL_STRONGER |
| competitor seed invariant | service/controller/frontend tests | EQUIVALENT |
| TechOps substantive result contract | exact models/service + adapter tests | WRAPPED_EXACT |
| provider 5xx observability | structured provider safe log | EXACT |
| SSE terminal 404 guard | existing full hook/test | EXACT |
| artifact/security/legal ordering | full Marketing tests | FULL_STRONGER |

## E. OMITTED-IN-PAST RECOVERY

| 과거 누락 | 기존 감사가 놓친 이유 | 이번 복구 | 재발 방지 |
| --- | --- | --- | --- |
| TechOps original engine | UI/result field 유사성만으로 이식 완료 판정 | core 7개 donor blob exact transplant | blob hash + result contract tests |
| Research2 최신 다단계 engine | 일부 wrapper와 이전 파일 존재로 기능 동일 오판 | runtime/rules/tools/resources 전수 transplant | step15~18 135 tests |
| quote/design/funnel | active API path 밖 quality tool 미감사 | tool와 fixture 복구 | quality script suite |
| competitor seed | main V22와 full V22 번호 충돌 | semantic schema를 V23로 추가 | service/API/UI tests |
| PDF dependency | 코드만 비교하고 runtime manifest 누락 | pdfplumber pin 추가 | container preflight 사용자 검증 |
| Vite `/api` proxy | production API path 중심 감사 | dev proxy 반영 | production build + config audit |

## F. INTENTIONAL DIFFERENCES

1. main synchronous TechOps endpoint 대신 full TaskRun/Worker/SSE를 쓴다. long HTTP timeout, localStorage canonical result, process restart 유실을 방지하기 위해서다.
2. main sample Concept/Twin fallback은 이식하지 않는다. 잘못된 사업안으로 성공 결과를 만드는 P0 위험이며 full canonical source가 fail-closed한다.
3. `CONCEPT_HANDOFF_NOT_CONNECTED`와 TODO bridge는 제품 capability가 아니라 main 미완성 표식이므로 이식하지 않는다.
4. main competitor seed V22는 full V22 TechOps migration과 충돌한다. 기존 checksum을 보존하기 위해 V23으로만 추가했다.
5. Finance는 TechOps를 prerequisite로 두지 않는다. current Market FULL + BM exact lineage가 확정 authority다.
6. Marketing은 copy legal validation을 이미지 생성보다 먼저 수행하고 generated JPEG를 ObjectStorage 검증 후 revision에 결속한다.
7. Research2 local `runs-generated`는 canonical DB/Artifact가 아니므로 Git 추적에서 제외했다.

## G. TEST SUMMARY

| 분류 | 파일/클래스 | Passed | Failed | Skipped | 판정 |
| --- | ---: | ---: | ---: | ---: | --- |
| AI 전체 | 전체 pytest suite | 594 | 4 | 0 | 4 PRE_EXISTING, FULL_START_SHA 동일 재현 |
| AI targeted | targeted set | 134 | 0 | 0 | PASS |
| Research2 step15~18 | quality scripts | 135 | 0 | 0 | PASS |
| Design score | 1 file | 17 | 2 | 0 | donor 자체 fixture gap |
| Backend Market | targeted classes | 19 | 0 | 0 | PASS |
| Backend TechOps/Finance/Marketing/Module | 24 classes | 91 | 0 | 0 | PASS |
| Backend targeted 합계 | 25+ classes | 110 | 0 | 0 | PASS |
| Frontend Market | 18 files | 103 | 0 | 0 | PASS |
| Frontend 전체 | 78 files | 418 | 18 | 0 | 18 PRE_EXISTING_CANDIDATE |

추가 검증:

- AI compileall: PASS
- backend 전체 suite: 제한 시간 내 미완료 (`ENVIRONMENTAL_INCOMPLETE`)
- backend compile 재실행: Gradle distribution download network 차단 (`ENVIRONMENTAL`)
- frontend 변경 파일 ESLint 5개: PASS
- frontend 전체 ESLint: 10 errors, 2 warnings(시작 SHA 대비 무변경 영역)
- frontend production build: PASS, 260 modules
- `git diff --check`: PASS
- migration/PostgreSQL: live 미검증
- Docker/Compose/MinIO/OpenAI/Tavily/KOSIS/DART/browser: 미검증

## H. 보호 영역

변경 0을 확인한 영역:

- CPV2 core
- Finance deterministic calculation 및 Monte Carlo
- Twin core
- TaskRun/TaskAttempt core
- JobEvent/SSE core
- 기존 V1~V22 migration
- `.env`
- Twin Bank

Market/BM은 donor 최신 engine과 canonical input 의미를 복구하는 요청 범위에서만 변경했다. Finance/Marketing의 full R1 강화 계약은 유지했다.

## I. 최종 gap

- `MISSING: 0`
- `PARTIAL: 0`
- `REGRESSED: 0`

이는 main의 유효 제품 capability에 대한 코드·계약 판정이다. 기존 테스트 부채와 live infrastructure 검증 미완료를 PASS로 간주하지 않는다.

## J. Git 인계

- 최종 변경은 working tree에 남아 있다.
- 저장소 `AGENTS.md`의 Git safety가 `commit`과 `push`를 명시적으로 금지하므로 권장 커밋 `MAIN-LATEST-COMPLETE-RESYNC-V3`는 생성하지 않았다.
- 저장소 관리자가 diff와 미검증 항목을 확인한 뒤 별도 권한 컨텍스트에서 커밋해야 한다.
