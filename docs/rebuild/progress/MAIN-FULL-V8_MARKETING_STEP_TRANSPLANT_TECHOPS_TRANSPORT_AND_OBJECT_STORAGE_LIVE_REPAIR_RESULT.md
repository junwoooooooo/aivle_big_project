# MAIN-FULL-V8 Marketing 단계 이식·TechOps Transport·Object Storage Live Repair 결과

작성일: 2026-08-13  
판정 기준: 현재 코드, `origin/main` PR #43 tree, deterministic seam test

## REMOTE SHA

| 항목 | SHA/상태 |
|---|---|
| branch | `full` |
| START/HEAD | `a95ef9b902eae435e7356d948e2c3a19e4c7acda` |
| origin/full | `a95ef9b902eae435e7356d948e2c3a19e4c7acda` |
| origin/main | `e91741c406c4fd9d919299d9656b7b78fa73c2b3` |
| START worktree | clean |
| ahead/behind | `0/0` |

## MAIN MARKETING TRANSPLANT MATRIX

| 파일/기능 | MAIN latest | FULL before | FULL after | 분류 |
|---|---|---|---|---|
| `MarketingContentPage.jsx` | Concept→설정→결과 3-step | 단일 3-column workspace | main step UI + full source 안내 보존 | TRANSPLANTED_ADAPTED_TO_FULL |
| `MarketingContentPage.test.jsx` | step 이동 3 tests | 단일 화면 tests | donor tests 이식, revision key fixture 보강 | TRANSPLANTED_ADAPTED_TO_FULL |
| `marketing-content.css` | step/result 반응형 + sticky actions | 단일 workspace + sticky actions | donor 반응형 이식, actions는 static | TRANSPLANTED_ADAPTED_TO_FULL |
| Reference image frontend validation | main PR에서 hook validation 제거 | PNG/JPEG, 20MB validation | 기존 validation 유지 | FULL_STRONGER_PRESERVED |
| 통합 Marketing Visual | main PR에서 파일 삭제 | full standalone/runtime 보존 | 삭제하지 않음 | MAIN_NOT_APPLICABLE |
| API/Canvas/Setup formatting diff | 의미 변화 없음 또는 disabled 회귀 | full 동작 계약 | full 유지 | FULL_STRONGER_PRESERVED |
| canonical refresh/polling | main에도 없음 | SSE terminal detail refresh만 | REST polling + list/detail reconcile | NEW_LIVE_REPAIR |

PR #43의 변경 파일을 모두 검사했다. 제품 의미가 있는 3-step page/CSS/test는 이식했고, full의 reference 검증·Marketing Visual runtime 삭제 및 단순 포맷 diff는 이식하지 않았다. 결과적으로 설명되지 않은 PR #43 제품 기능 차이는 없다.

## TECHOPS TRANSPORT MATRIX

| 항목 | 이전 | 이후 | 검증 |
|---|---|---|---|
| Worker attempt/deadline | 6분 | 유지 | source inspection |
| Worker lease | 7분 | 유지 | source inspection |
| Provider timeout override | 180초 bounded calls | 유지 | AI core 무변경 |
| Backend route | generic 30초 | long-running 7분 | routing test |
| generic short tasks | 30초 | 30초 유지 | delayed HTTP test에서 먼저 종료 |
| TechOps delayed response | 약 30초에 transport 종료 가능 | 축소 시간축에서 long response 성공 | delayed HTTP test |
| timeout taxonomy | generic `RestClientException`이 dependency로 축약 가능 | cause chain의 timeout→`REQUEST_DEADLINE_EXCEEDED` | real RestClient delayed test |
| connection taxonomy | dependency unavailable | 유지 | connection failure test |

TechOps advisor `service.py`, scaler, evidence, validator 등 AI core는 변경하지 않았다. V7의 sync one-dict `SafeTaskProgressSender.emit` 계약도 그대로 유지했다.

## TECHOPS LIVE ERROR REASON

| 증거 | 판정 |
|---|---|
| 사용자 관측 | 약 29초 후 `FAILED / AI_SERVICE_UNAVAILABLE` |
| 이전 코드 | `TECH_OPS_ADVISORY`가 generic 30초 client 사용 |
| deterministic transport test의 이전 결과 | short timeout이 `DEPENDENCY_UNAVAILABLE`로 축약되는 경로 재현 |
| 실제 과거 DB의 `normalized_error_reason` | UNVERIFIED — Codex 셸에서 Docker/DB 접근 불가 |
| 복구 후 예상 | 30초 경계 제거; 실제 timeout은 `REQUEST_DEADLINE_EXCEEDED` |

정확한 과거 row의 reason은 사용자 검증 문서의 TaskRun/Attempt/JobEvent SQL로 확인해야 한다. 현재 증거만으로 과거 row를 임의로 `REQUEST_DEADLINE_EXCEEDED`라고 단정하지 않는다.

## OBJECT STORAGE ENDPOINT MATRIX

| 경로 | 이전 | 이후 | authority |
|---|---|---|---|
| Backend S3 client | `http://minio:9000` | 동일 | container internal |
| Backend presigner | `http://minio:9000` | `${OBJECT_STORAGE_PUBLIC_ENDPOINT:-http://localhost:9000}` | browser public |
| MinIO API host port | base compose 미노출 | `${MINIO_API_PORT:-9000}:9000` | local browser access |
| MinIO console | e2e에서 9001 노출 | 기본/e2e 미노출 | 불필요 |
| invalid public host | 허용 | exact `minio/backend/ai-server` fail-fast | product guard |

## PRESIGNED URL RESULT

Offline S3 presigner test 결과:

- internal client endpoint: `http://minio:9000`
- public signed URL scheme/host/port: `http://localhost:9000`
- path: `/aivle-ai-artifacts/ai-artifacts/<UUID>.jpg`
- `X-Amz-Algorithm`, `X-Amz-Signature` query 유지
- Docker-only public hostname fail-closed

MinIO object의 실제 존재와 browser HTTP 200/render는 Docker 부재로 미검증이다. `OBJECT_STORED`와 `BROWSER_URL_REACHABLE`을 별도 live gate로 유지한다.

## MARKETING REFRESH MATRIX

| 시나리오 | 구현/결과 |
|---|---|
| QUEUED→RUNNING→COMPLETED | 1.5초 canonical detail polling으로 감지 |
| SSE terminal 수신 | 즉시 REST detail 재조회 |
| SSE terminal 누락 | polling fallback으로 완료 감지 |
| detail terminal | active=false가 되어 timer cleanup |
| COMPLETED detail | selected detail과 list summary 동시 reconcile |
| FAILED detail | polling 중단 |
| content switch | request epoch로 old response 무시 |
| unmount | timer 취소, pending response 적용 차단 |
| terminal authority | SSE가 아닌 REST detail |

## MARKETING UI MATRIX

| UI | 결과 |
|---|---|
| Step 1 Concept/Marketing Source | 이식 |
| Step 2 setup/reference/generation progress | 이식 |
| Step 3 preview/style/legal/editor/revision/actions | 이식 |
| 생성 완료 자동 Step 3 | canonical detail 기반 이식 |
| 저장 콘텐츠 클릭 Step 3 | 이식 |
| desktop/tablet/mobile CSS | donor breakpoints 이식 |
| keyboard/focus | native button, `aria-current=step`, focus-visible 유지 |
| action footer | `position: static`; sticky/fixed 0 |

## TEST MATRIX

| 영역 | 명령/범위 | passed | failed | skipped | 결과 |
|---|---|---:|---:|---:|---|
| Frontend Marketing 전체 | 9 files | 26 | 0 | 0 | PASS |
| Frontend page 재검증 | 1 file | 3 | 0 | 0 | PASS |
| Frontend changed lint | 6 changed JS/JSX files | PASS | 0 | - | PASS |
| Frontend production build | Vite 261 modules | PASS | 0 | 0 | chunk warning only |
| Backend transport/Marketing/storage | 10 classes | 36 | 0 | 0 | PASS |
| Backend compileJava/compileTestJava | Gradle target suite 선행 | PASS | 0 | 0 | PASS |
| AI TechOps focused | 2 files | 9 | 0 | 0 | PASS, 6 deprecation warnings |
| ENV contract audit | machine audit | PASS | 0 | 0 | required/unknown gates 0 |
| compose storage static | PyYAML contract assertion | PASS | 0 | 0 | PASS |
| Docker compose config/build/up | Docker CLI 없음 | - | - | - | UNVERIFIED_ENVIRONMENT |
| browser desktop/mobile | live app 없음 | - | - | - | UNVERIFIED_ENVIRONMENT |

`npm run lint -- --quiet <files>`는 script가 `eslint .`로 고정되어 전체 저장소의 기존 10건을 검출했다. 변경 파일만 `npx eslint ... --quiet`로 다시 실행한 결과는 PASS다.

## 보호 영역

변경 0:

- CPV2 core
- Market Research2 core
- BM core
- TechOps advisor AI core
- Finance deterministic calculation/Monte Carlo
- Twin bank/runtime
- Legal source pipeline
- canonical hash validation
- TaskRun stale/ownership semantics

## 변경 파일 및 계약

- Frontend: Marketing page/test/CSS, `useMarketingContent`, `useMarketingGeneration` 및 hook tests
- Backend: Internal AI client routing/taxonomy와 tests, ObjectStorage property guard/presigner test
- Runtime: `compose.yaml`, `compose.e2e.yaml`, `.env.example`, `.env.e2e.example`
- Docs: README, env matrix, 이 결과와 사용자 검증 문서

구현 계약:

1. TechOps는 generic 30초 client를 사용하지 않는다.
2. REST Marketing detail/list가 canonical이며 SSE는 notification이다.
3. container S3 endpoint와 browser presigner endpoint는 분리한다.
4. Docker-only hostname을 browser artifact URL로 발행하지 않는다.
5. Marketing action footer는 viewport overlay가 아니다.

## LIVE USER VERIFICATION

정확한 명령은 `docs/rebuild/verification/MAIN-FULL-V8_MARKETING_STEP_TRANSPLANT_TECHOPS_TRANSPORT_AND_OBJECT_STORAGE_LIVE_REPAIR_USER_VERIFICATION.md`에 있다.

## REMAINING GAP

| 분류 | 수 | 내용 |
|---|---:|---|
| MISSING | 0 | 요청된 코드 이식/복구 누락 없음 |
| PARTIAL | 0 | offline seam 기준 |
| REGRESSED | 미선언 | live 전 0 선언 금지 |
| UNVERIFIED_ENVIRONMENT | 5 | Docker config/up, TechOps live, Marketing live refresh, MinIO object, browser render/mobile |
| UNSCOPED_LINT_FAILURE | 10 | 전체 lint에서 비변경 파일 오류; baseline을 별도 재실행하지 않아 PRE_EXISTING으로 분류하지 않음 |
| UNEXPLAINED_MAIN_MARKETING_DIFF | 0 | PR #43 전체 분류 완료 |

## 의도적으로 생략한 검증과 남은 위험

- 실제 `.env` 및 secret 열람: 금지에 따라 생략
- 유료 provider 호출: 생략
- Docker/MinIO/PostgreSQL/browser: Codex 셸에 Docker CLI가 없어 미검증
- 실제 과거 TechOps Attempt reason: DB 접근 불가로 미확정
- 실제 Marketing 이미지의 object stat/HTTP 200/render: 사용자 live 검증 필요

따라서 이 단계에서는 `COMPLETE`, `REGRESSED=0`을 선언하지 않는다.

정확한 continuation point는 사용자 검증 문서 순서대로 backend/frontend 재기동 후 TechOps 1회, Marketing 1회, Object stat, browser 렌더, mobile/action footer를 확인하는 것이다.
