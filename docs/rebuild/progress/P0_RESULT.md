# FAST IMPLEMENTATION 결과 — 사업검증·기준값·출시준비·가상인터뷰

## 기준

- start SHA: `729bdb60ee83dc74d57b4323e350f1429b6e42f3`
- branch: `full`
- start HEAD와 `origin/full` 일치 확인
- 기존 변경사항 없음. reset, clean, stash, checkout, revert, commit, push를 실행하지 않았다.

## 구현한 계약

### Business Validation Market

- Research2 dryrun의 `stat_code 해결=0`을 전체 Market의 즉시 HARD FAIL로 사용하지 않는다.
- 슬롯을 `KOSIS_VERIFIED`, `WEB_DIRECT`, `KOSIS_UNRESOLVED_WEB_FALLBACK`, `BLOCKED_NO_ROUTE`와 직접 adapter route로 분류한다.
- 기존 route metric 규칙에 WEB fallback이 있는 KOSIS 슬롯은 collect 단계까지 진행한다.
- 일부 슬롯에 대체 경로가 없으면 해당 직접 시장규모 근거를 생성하지 않고 `MARKET_ROUTE_PARTIAL` degradation을 남긴다.
- 모든 슬롯에 관측 경로가 없을 때만 `MARKET_ROUTE_UNRESOLVED`, HTTP 422, retryable=false로 실패한다.

### 기준값

- `finalValue ?? proposedValue`가 실제로 존재하면 AI 제안도 입력 완료로 계산한다.
- 7개 값이 있으면 `7/7 입력 완료`, 실제 빈 값 하나면 `6/7 입력 완료`로 표시한다.
- `AI 제안 · 확인 필요` 진행 blocker를 제거하고 `AI 제안`, `사용자 입력`, `사용자 수정` 출처 badge만 남겼다.
- `기준값 확정` 한 번으로 현재 화면의 7개 값을 기존 global confirm 계약에 전달한다.
- 실제 빈 값과 semantic/legal 오류만 구체 메시지와 row focus로 막는다.
- 금액 표시는 `500,000 KRW · 50만 원` 형식을 유지한다.

### 출시 준비

- Project Journey에는 `3. 출시 준비` 한 단계만 노출하고 TechOps/Finance child step을 제거했다.
- `/launch-readiness`에 기술 분석, 운영 분석, 재무 분석의 독립 DOCX workflow를 구성했다.
- 각 분석은 template → upload → 독립 실행/event → 결과 → 보고서 흐름이며 서로 prerequisite가 아니다.
- 새 `LAUNCH_READINESS/LAUNCH` 단일 분석은 canonical 사용자 surface에서 제거했다. Backend 참조는 compile 안전을 위해 삭제하지 않았다.
- `/technology`, `/operations` 호환 경로는 같은 Launch 화면의 해당 카드로 연결하며 `/tech-ops`, `/finance` 내부 route는 유지한다.
- 기술·운영·재무 및 통합 보고서 route를 다시 연결했다.

### Market Interview UX

- Before에 Research Mission hero, 사업안과 target representability, 6개 조사 목적, 20/40/80 선택 카드, 5단계 실행 흐름을 표시한다.
- During에 실제 event stage 8단계 rail, 현재 단계 문구와 실제 event count만 표시한다.
- After에 Result Insight Workspace, deterministic insight, Theme Explorer, Respondent master-detail, theme → respondent → original answer traceability를 표시한다.
- UI에서 새 LLM 호출, 없는 요약·확률·count·quote를 만들지 않는다.

### SSE

- 현재 source of truth에 이미 client abort/committed async response 전용 void handler와 emitter cleanup이 구현되어 있음을 확인했다.
- Broken pipe는 TaskRun 실패로 전환하지 않으며 JSON ApiResponse를 `text/event-stream`에 쓰지 않는다.
- 이번 diff에서는 중복 수정하지 않았다.

## 변경 파일

- AI: `ai/app/research/pipeline.py`, `product_pipeline.py`, `runner.py`, focused test.
- Backend: `InternalAiExecutionClient.java`.
- Frontend: Journey/module/router, 기준값 model/workspace/style/tests, Launch page/report/model/tests, Market Interview page/result/style/tests.
- Stage artifacts: 이 결과 문서와 `docs/rebuild/verification/P0_USER_VERIFICATION.md`.

정확한 목록은 `git status --short`를 기준으로 한다.

## 실제로 실행한 확인

- AI changed files `python -m py_compile ...`: PASS.
- AI pytest: 로컬 Python에 pytest/httpx가 없어 실행 불가. 설치나 Docker rebuild는 FAST 지시상 수행하지 않았다.
- Frontend 기준값 + Interview focused: 4 files, 66 tests PASS.
- Frontend Launch + Journey focused: 7 files, 66 tests PASS.
- 변경 Frontend 파일 ESLint: PASS.
- Backend focused test: 로컬 Gradle distribution은 실행됐으나 Spring Boot plugin artifact가 캐시에 없어 dependency resolution에서 중단. 다운로드 재시도는 하지 않았다.
- `git diff --check`: PASS. LF→CRLF 안내만 존재.

## 의도적으로 생략

전체 AI/Backend/Frontend test, baseline, Docker rebuild, provider call, runtime E2E, browser automation, 새 프로젝트 생성, production build.

## 남은 위험과 continuation point

1. 사용자가 현재 환경에서 같은 Business Validation 입력을 재실행해 KOSIS unresolved 슬롯이 WEB fallback으로 넘어가고 Market 결과가 degradation과 함께 완료되는지 확인한다.
2. Backend dependency cache가 준비된 환경에서 SSE focused tests만 재실행한다.
3. 인증 후 Launch 세 카드와 Interview Before/During/After를 실제 화면에서 확인한다.

---

## 2026-08-18 FAST PRODUCT POLISH + MARKETING STRATEGY TRANSPLANT

### 기준

- start SHA: `5866ac1ec9c02eb00daf92cdc59c8951020d7e77`
- branch: `full`; start HEAD와 `origin/full` 일치
- `marketingfix`의 `d4d88ff39cb9b5439a9422729866f894ba260390`은 읽기 전용으로 조사했고 merge/cherry-pick/전체 덮어쓰기를 하지 않았다.

### 구현한 계약

- Market Interview coding은 batch 직후 participant/theme/alternative/evidence/axis/verbatim을 검증한다. 실패 batch만 1회 재생성하고, minimum usable·group coverage를 지킬 때만 실패 respondent를 명시적으로 제외한다. 안전 진단에는 stage/rule/path/batchIndex/participantId만 남긴다.
- 인터뷰 응답 progress는 5명 단위로 emit하고 Work Center는 같은 stage의 count checkpoint를 한 항목으로 합친다. 새 TaskRun 직후 cursor 0의 404는 두 번까지 quiet retry한다.
- Business Validation은 준비 mission, 두 입력 카드, 6단계 state rail, 결과 summary/local nav를 제공한다. Market 표는 8건 뒤 펼치며 가격·성장·경쟁·미확보 명칭을 실제 의미로 정리했다. BM 판정·canvas·강약위험·재무 handoff와 refinement/final 결과를 progressive disclosure로 구성했다.
- Marketing Strategy AI/Backend/Frontend를 현재 `full` 위에 선별 이식했다. `CURRENT_CONCEPT`만 필수이고 Market/BM/기술/운영/재무/Market Interview/Twin Survey는 현재 결과가 있을 때만 optional context다.
- Strategy 결과의 summary/target/positioning/messages/channel audience·actions·KPI/roadmap/budget/risks/evidence를 UI와 PDF에 보존한다. Marketing은 분석 자료 → 전략 → 생성 설정 → 결과 확인 4단계이며 콘텐츠 요청에 current strategy report ID를 연결한다.
- `TWIN_SURVEY` 8개 hard prerequisite와 marketingfix의 이후 full 변경을 덮어쓰는 계약은 이식하지 않았다.

### 변경 파일

- AI: `market_interview/deep_engine.py`, 관련 models/tests, `marketing_strategy/*`, execution routing, marketing content strategy input.
- Backend: TaskType/AI routing/job projection, Market Interview failure event, `pipeline/marketing/strategy/*`, V43 migration, PDF dependency, content strategy link와 focused tests.
- Frontend: Business Validation/Market/BM/refinement/final UI, Market Interview failure/event handling, Marketing Strategy API/hook/panel/4-step page/CSS/tests.
- 정확한 목록은 `git status --short`와 `git diff --stat`을 기준으로 한다.

### 실제로 실행한 확인

- AI changed modules `python -m py_compile ...`: PASS.
- AI focused pytest 7개: PASS (`1.01s`).
- Backend Marketing Strategy source/start/current/result + module enum + AI routing focused test 8개: PASS. `compileJava`: PASS.
- Frontend focused 11 files 110 tests: PASS.
- 변경 Frontend JS/JSX ESLint: PASS.
- `git diff --check`: 최종 handoff 직전 별도 실행.

### 의도적으로 생략

전체 AI/Backend/Frontend suite, baseline, Docker rebuild, production build, 외부 provider, 실제 runtime E2E, browser automation, 새 프로젝트 생성.

### 남은 위험과 continuation point

1. 사용자가 인증된 실제 서비스에서 TaskRun `bf82d4aa-a39d-4add-96f6-ca6d3f0df88b`과 같은 입력을 재시도해 local coding retry/diagnostics를 확인한다.
2. OS에 한국어 글꼴이 없는 배포 환경에서는 Strategy PDF font 설치가 필요하다(Windows 맑은 고딕과 Linux Noto CJK 경로 지원).
3. 실제 provider 결과로 Strategy 생성과 Strategy ID가 연결된 Content 생성은 이번 FAST 지시에서 호출하지 않았다.

---

## 2026-08-18 FAST RUNTIME REPAIR + MARKETING WORKSPACE + FINAL BUSINESS PROPOSAL

### 기준과 구현 계약

- start SHA와 `origin/full`: `43ea2ede55ae483fcd5f8d5f1fd484f75aae0b39`.
- Market Interview의 `VERBATIM_QUOTE_MISMATCH`는 NFKC·zero-width·공백·인용부호·dash만 보수적으로 정규화하고, 일치한 span을 실제 원문에서 다시 잘라 저장한다. batch 재시도 뒤에도 한 respondent만 실패하면 해당 assignment만 1회 repair하며, 실패 시 minimum usable과 Target/Comparison coverage를 지키는 경우에만 제외한다. 진단에는 repair/exclusion 시도 및 차단 이유를 추가했다.
- Marketing Strategy `evidenceRefs`는 source manifest의 exact `TYPE:id` enum만 provider schema와 prompt에 제공한다. 고유 TYPE의 잘못된 id만 canonical ref로 교정하고 unknown/ambiguous TYPE은 안전 진단과 함께 거부한다.
- SSE client disconnect는 wrapper cause chain 전체에서 감지해 committed/text-event-stream 응답에 JSON `ApiResponse`를 쓰지 않는다. event replay는 최근 100개 window로 제한했다.
- Marketing 페이지는 `마케팅 전략`과 `콘텐츠 제작`의 독립 workspace로 구성했다. 콘텐츠는 전략 없이 현재 사업안만으로 생성 가능하며, 사용자가 최신 전략을 선택한 경우에만 `marketingStrategyReportId`를 보낸다. 생성 이력과 AI 초안 안내는 콘텐츠 workspace 안으로 이동했다.
- Final Report는 lightweight `/status`, 사용자 source 선택, 비동기 `FINAL_BUSINESS_PROPOSAL_GENERATION`, structured proposal, Backend evidence canonicalization, A4 web preview, deterministic PDF/DOCX renderer를 제공한다. `MARKETING_STRATEGY` current source를 포함하며 AI 검토는 별도 `FINAL_BUSINESS_PROPOSAL_REVIEW` TaskRun/TaskResult로 저장하고 선택한 문서 버전에만 부록으로 출력한다.

### 변경 파일

- AI: Market Interview repair, Marketing Strategy evidence guard, `final_business_proposal/*`, execution routing과 focused tests.
- Backend: SSE handler/replay, Final Report API/service/status/workers/document renderer, TaskType/AI routing/error/job projection과 focused tests.
- Frontend: Marketing workspace/model/tests, Final Report page/API/CSS/tests, project shell lightweight status, job labels.
- 정확한 목록은 `git status --short`를 기준으로 한다.

### 실제로 실행한 확인

- AI focused: 3개 관련 test file, `50 passed in 2.70s`.
- Backend focused: SSE, event stream, Final Report source/status, DOCX/PDF, Marketing Strategy, AI routing 지정 6개 class; 최종 `BUILD SUCCESSFUL in 16s`.
- Frontend focused: Marketing model/page/strategy hook-panel, Final Report, ProjectLayout 6개 file; `21 passed`.
- 변경 Frontend 파일 ESLint: PASS.
- `git diff --check`: PASS(LF→CRLF 안내만 존재).

### 의도적으로 생략

전체 suite/baseline, Docker rebuild, production build, 외부 provider 호출, 새 프로젝트, 장시간 runtime/browser E2E, commit/push.

### 남은 위험과 continuation point

1. 실제 project 7에서 실패한 Market Interview를 재시도해 respondent repair 또는 안전 제외 후 terminal 상태를 확인한다.
2. 실제 Marketing Strategy provider 실행으로 enum evidence ref와 canonicalization 이후 완료되는지 확인한다.
3. Final Proposal의 실제 대용량 source 문서에서 한글 font, 긴 표, page break를 사용자 다운로드 파일로 확인한다.

---

## 2026-08-18 FAST 런타임 계약 교정 — Interview Retry / Final Report / Optional Status

### 기준과 구현 계약

- start SHA와 `origin/full`: `6b78b9fa3d50f916e55c1e17e16aec1626c0d1d5`.
- Market Interview CurrentView에 Backend 정본 `retryAllowed`와 `restartAllowed`를 추가했다. retry 한도 소진 시 이전 run을 변경하지 않고 기존 sample size로 attempt 1 새 실행을 시작한다. `/retry` 409 race는 current를 재조회해 restart 안내로 전환한다.
- FinalReport event publish는 writable `REQUIRES_NEW` transaction으로 분리했다. Proposal/Review worker의 event 전송은 `safePublish`로 격리해 본체 실행·저장을 실패시키지 않는다. schema 위반만 `AI_RESULT_INVALID`, 기타 runtime/persistence 실패는 `EXECUTION_FAILED / TRANSIENT_EXECUTION_FAILURE`로 분류한다.
- Launch Technology/Operations는 concept binding과 무관하게 프로젝트의 최신 current/non-stale DOCX report를 사용한다. Finance는 최신 `USER_DOCUMENT_INPUT`과 adopted report를 concept-bound 결과보다 우선한다.
- Marketing Content는 FINALIZED를 우선하고, 없으면 current source의 latest COMPLETED revision을 `draft=true / status=COMPLETED`로 포함한다. status 응답은 AVAILABLE, AVAILABLE_FINAL, AVAILABLE_DRAFT, IN_PROGRESS, FAILED, CURRENT_RESULT_UNAVAILABLE, NOT_RUN을 구분한다.
- Twin Survey는 Final Report, Marketing Strategy context, ProjectModuleStatus 응답, Frontend selector/label/navigation에서 제거했다. `/twin-survey`만 `/market-interview` redirect로 보존한다.
- 출시 준비와 최종 보고서는 상위 Journey에서 `OPTIONAL`이며 badge와 프로젝트 진행률에서 제외한다. Project shell은 Final Report status를 조회하지 않고 Final Report 페이지 내부에서만 상태를 조회한다.
- Final Report page-level 회색 배경/margin/padding을 제거하고 A4 preview의 흰 배경과 shadow만 유지한다.

### 변경 파일

- AI: Final Proposal draft labeling prompt.
- Backend: Market Interview view, Final Report source/status/transaction/workers/composer, Marketing repository/strategy source, module/project presentation과 focused tests.
- Frontend: Market Interview retry UX, Final Report source state/UI/CSS, route/module/journey/project status와 focused tests.
- 정확한 목록은 `git status --short`를 기준으로 한다.

### 실제로 실행한 확인

- Backend focused 6개 class: XML 기준 `41 tests, 0 failures, 0 errors`.
- Frontend focused: `10 files, 78 tests PASS`.
- 변경 Frontend ESLint: PASS.
- Final Proposal prompt `py_compile`: PASS.
- `git diff --check`: 최종 handoff 직전 실행.

### 의도적으로 생략

전체 suite/baseline, Docker rebuild, 외부 provider 호출, 실제 runtime/browser E2E, commit/push.

### 남은 위험과 continuation point

1. project 7에서 retry 소진 run의 새 실행 CTA와 attempt 1 생성을 확인한다.
2. 실제 Final Proposal 생성으로 read-only transaction 오류가 재발하지 않는지 확인한다.
3. 실제 Technology/Operations/Finance/Marketing 데이터의 sourceStates와 생성 문서 포함 여부를 확인한다.

---

## 2026-08-18 FAST Runtime Repair V4

### 기준과 구현 계약

- start SHA와 `origin/full`: `45a94cfd66c9b1209aa8f87033df4e7cfde6f649`.
- Market Interview coding은 batch에서 participant 수·identity·순서·중복만 검사한다. Evidence는 respondent별로 검증하고, exact quote repair가 실패하면 해당 근거 없는 theme/evidence만 제거한다. 다른 respondent와 raw interview는 유지하며 mention count는 남은 exact evidence participant만 사용한다.
- Final Proposal AI input의 manifest `metadata`를 optional JSON contract로 수용하고, Pydantic 오류는 value 없이 path/category/expectedType만 반환한다.
- USER_DOCUMENT_INPUT 재무 결과는 shared subject `USER_DOCUMENT_INPUT`의 TaskRun 중 `inputSnapshot.snapshotId`가 현재 snapshot과 같은 adopted 결과만 선택한다.
- Marketing Strategy와 Final Proposal은 Final Report 표현 JSON이 아닌 동일한 current source catalog의 manifest/source/sourceStates를 사용한다.
- Final Report status는 최근 generation의 task/state/error code/reason을 제공하며, terminal failure를 준비 화면에서 숨기지 않는다.
- Marketing 재생성은 요청 직후 progress를 표시하면서 기존 전략을 유지한다. Finance 입력/보고서는 UI에서 `재무 분석` 하나로 묶었다.
- `/marketing/report`에 A4 전략 보고서 preview, browser PDF 저장, server PDF 다운로드를 추가했다. PDF는 TTC를 제외한 embeddable Korean TTF/OTF resolver를 공용 사용하고 Docker에 `fonts-nanum`을 추가했다.
- 프로젝트 목록의 floating `상태 안내` helper와 관련 CSS를 제거했다.

### 실제로 실행한 확인

- AI py_compile + focused pytest: 13 tests PASS(최종 metadata diagnostics 포함).
- Backend focused 4개 class: BUILD SUCCESSFUL. 전체 suite는 실행하지 않았다.
- Frontend focused 7 files: 23 tests PASS.
- 변경 Frontend ESLint: PASS.
- `git diff --check`: PASS(LF→CRLF 안내만 존재).
- 저장된 두 TaskRun input offline 조회: 로컬 PostgreSQL `localhost:5432`가 실행 중이지 않아 exact row를 읽지 못했다. 동일 metadata manifest shape은 AI model test로 validation PASS를 고정했다.

### 의도적으로 생략 및 continuation point

전체 suite/baseline, Docker rebuild, provider 호출, 장시간 runtime/browser E2E, 새 프로젝트, commit/push를 실행하지 않았다. 사용자는 project 7에서 Interview coding salvage, Marketing report/PDF 한글, Finance source, Final Proposal generation을 확인한다.

---

## 2026-08-18 FAST PRODUCT HARDENING V5

### 기준과 구현 계약

- START SHA와 `origin/full`: `1aa53213f694957af598686c8c93011e230133ab`.
- Market Interview coding batch는 transport envelope로만 사용한다. provider 순서는 expected participant 순서로 재정렬하고, schema-valid row를 즉시 보존한 뒤 누락·중복·invalid respondent만 축소 batch 1회와 single respondent fallback으로 복구한다.
- provider coding output에서 quote/themeEvidence를 제거했다. 서버가 codebook axis를 실제 answer field에 연결하고, 500자 이하 전체 답변 또는 500자 이하 문장 경계 excerpt를 exact original substring으로 저장한다. unknown theme만 제거하고 unknown alternative는 빈 값으로 정규화한다.
- Codebook의 6축/한국어/고유 title 계약 위반은 동일 transcript로 correction call 1회 후에만 실패한다.
- Marketing 재생성 POST의 taskRunId/status를 optimistic view에 즉시 보존해 SSE를 연결한다. QUEUED/ANALYZING/COMPLETED를 3단계 rail로 표현하며 기존 전략은 새 결과 전까지 유지한다.
- Marketing Report를 표지, 문서 정보, 결재란, 내부 목차, 채널/캠페인 표 중심 회사 문서로 변경했다. 사용자 PDF 다운로드 action은 제거하고 browser `PDF로 저장`만 남겼으며 print chrome을 제거한다.
- Final Report의 Marketing Strategy 상태를 `AVAILABLE / UPDATE_REQUIRED / IN_PROGRESS / FAILED / NOT_RUN`으로 구분한다. 공통 source catalog의 strategy source hash를 Marketing Strategy와 Final Report가 공유한다.
- Proposal snapshot 저장 뒤 `auto-review:{snapshotId}` idempotency key로 AI review를 자동 queue한다. queue 실패는 Proposal 성공을 되돌리지 않는다.
- Final Report는 작성자, 결재란, 내부 목차와 상단 control strip을 제공한다. 좌우 sidebar를 제거하고 PDF/DOCX는 authenticated blob download를 사용한다. PDF/DOCX deterministic renderer에도 내부 목차·결재란·작성자를 반영했다.

### 실제 확인

- AI focused Market Interview file: `45 passed in 2.12s`.
- AI `py_compile`: PASS.
- Frontend focused: `5 files, 14 tests PASS`.
- 변경 Frontend ESLint: PASS.
- Backend compile: 로컬 Gradle plugin artifact가 cache에 없고 network가 제한되어 실행 불가.
- project 7 최신 Final Proposal failure 조회: localhost PostgreSQL 미실행, Docker CLI 없음으로 terminal reason 확인 불가. 원인을 추측해 별도 runtime fix를 추가하지 않았다.

### 생략과 continuation point

전체 suite, Docker rebuild, provider smoke, 새 프로젝트, browser E2E, commit/push를 생략했다. 사용자 runtime에서 final batch single recovery, Strategy source state, auto review 및 인증 download를 확인한다.

---

## 2026-08-18 FAST STABILITY & MAIN IA RESTORATION V6

### 기준과 구현 계약

- START SHA와 `origin/full`: `5988e4f14e6a887bda4f00e125c00e7b894f9850`. `main`은 IA 참고로만 조회했고 현재 `full`의 component/token/visual system을 유지했다. 현재 `main`에는 신규 Business Validation/Market Interview feature path가 존재하지 않아 파일 이식은 하지 않았다.
- 사업 기준값은 `hasCurrentValue`와 `confirmable/blockingReason`을 분리했다. AI 제안도 값이 있으면 `7/7 입력 완료`에 포함하며, semantic/legal 차단은 별도 행 강조·사유·focus로 표시한다. confirm ActionAccepted의 `taskRunId`를 보존하고 SSE terminal 뒤 canonical selection/hypotheses를 재조회한다.
- Concept Portfolio selection input은 Backend가 보내는 `refinementApplication` strict schema를 수용하도록 round/hash/revision/snapshot 필드를 명시했다.
- Market Interview는 유효 transcript의 respondent-level coding 복구가 끝내 실패해도 해당 respondent를 `UNCLASSIFIED`로 보존한다. 다른 coded respondent와 theme evidence는 유지하며 `usableInterviewCount / codedInterviewCount / codingFailureCount`를 canonical result로 분리했다.
- Business Validation은 `사업 검증`과 `다듬어진 사업안` 두 내부 화면으로 분리했다. Market Interview는 `보여줄 것 확인 / 인터뷰 실행` 두 단계와 headline-first result, 세부 disclosure를 사용한다.
- Final Business Proposal input에 source-owned evidence catalog와 closed `allowedEvidenceKeys`를 추가했다. AI는 존재하는 key만 선택하고 Backend가 source id, 기준 시점, 원문/응답자, 출처 위치, 한계를 canonical detail로 결합한다. raw JSON fallback은 만들지 않는다.
- PDF XHTML escaping은 XML predefined entity만 사용하도록 변경해 `·`를 그대로 보존하고 `&middot;` undeclared entity를 제거했다. 동일 structured evidence detail을 Web/PDF/DOCX에 표시한다.

### 변경 파일

- AI: Concept Portfolio selection model, Market Interview model/engine, Final Business Proposal model/prompt/service와 focused tests.
- Backend: selection view/service, Final Report evidence catalog/service/document renderer와 focused tests.
- Frontend: criteria model/hook/workspace, Business Validation, Market Interview, Final Report UI/CSS/tests.
- 정확한 목록은 `git status --short`를 기준으로 한다.

### 실제로 실행한 확인

- AI Market Interview focused: `6 passed, 40 deselected`.
- AI Final Business Proposal focused: `4 passed`.
- AI refinement confirmation focused: `1 passed, 11 deselected`; 변경 AI 파일 `py_compile` PASS.
- Frontend focused 7 files: `109 tests PASS`.
- 변경 Frontend ESLint: PASS(최종 재실행 기준).
- Backend focused test는 Gradle 9.5.1 distribution이 로컬 cache에 없고 network가 제한되어 실행하지 못했다. 전체 suite나 network 설치는 시도하지 않았다.
- project 8 runtime 확인: localhost Backend 8080과 PostgreSQL 5432가 실행 중이지 않아 최신 hypotheses/Final Proposal TaskRun을 조회할 수 없었다. 원인을 추측해 runtime-specific patch를 추가하지 않았다.

### 의도적으로 생략과 continuation point

전체 suite/baseline, Docker rebuild, provider 호출, 새 프로젝트, browser E2E, commit/push를 실행하지 않았다. project 8에서 7/7 confirm terminal refresh, respondent coding degradation, Final Proposal evidence detail 및 PDF/DOCX 렌더를 사용자가 확인한다.
