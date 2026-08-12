# AIDEV-RESYNC R1 결과

## 1. START Gate

| 항목 | 확인값 | 판정 |
| --- | --- | --- |
| branch | `full` | PASS |
| start SHA | `bc0cceeb41465c57b2ee6256341b89bbf51ea0f5` | PASS |
| `origin/full` | `bc0cceeb41465c57b2ee6256341b89bbf51ea0f5` | PASS |
| `origin/aidev` | `d1b690acf7e62bbf4a8e07a810857be49d826313` | PASS |
| worktree | clean | PASS |
| 기존 AIDEV-RESYNC 계보 | HEAD 커밋 `AIDEV-RESYNC` | PASS |
| donor 접근 | lowercase `aidev` ref 직접 fetch 및 tree 읽기 성공 | PASS |

`git fetch origin --prune`은 Windows의 대소문자 비구분 ref 충돌(`origin/AIdev` 대 `origin/aidev`)로 실패했다. ref를 삭제하거나 덮어쓰지 않고 `git ls-remote --heads origin full aidev AIdev`와 `git fetch origin refs/heads/full refs/heads/aidev`로 authority SHA 및 donor 접근성을 검증했다.

## 2. 변경 전 AIDEV reverse gap audit

### 2.1 Finance

| aidev 기능 | aidev 파일 | full 대응 | 변경 전 상태 | 조치 |
| --- | --- | --- | --- | --- |
| current Market/BM 준비 authority | `FinancialService`, `FinancialPreparationFactory` | 동일 패키지 | FULL_STRONGER | KEEP |
| exact Market FULL → BM lineage·ownership·stale 검증 | `FinancialService` | `FinancialService.currentSources` | FULL_STRONGER | KEEP |
| Concept → Market → BM → 사용자/채택값 provenance | `FinancialPreparationFactory` | 동일 파일 | FULL_STRONGER | KEEP |
| 사용자값·채택값·확정 Snapshot 비덮어쓰기 | `FinancialService`, domain | 동일 파일 | FULL_STRONGER | KEEP |
| typed Money/churn/count/정확한 1·2·3년 | `models.py`, `service.py` | 동일 파일 | FULL_STRONGER | KEEP |
| bounded repair 1회 | `service.py` | 동일 파일 | FULL_STRONGER | KEEP |
| 상세 economic sanity/Tavily 지침 | `service.py` | 동일 파일 | PARTIAL | ADAPT |
| 단일 추천·대안·실패·재시도 | Finance hook/page, Backend TaskRun | 동일 계층 | FULL_STRONGER | KEEP |
| 그룹 추천 실행 | `useFinance.generateEstimates`, donor CSS | full hook/page | MISSING | TRANSPLANT |
| 추천값 input preview | `FinancePage.applyAiProposals` | full page | MISSING | ADAPT: 저장과 분리한 표시 전용 preview |
| ACCEPT/EDIT_AND_ACCEPT/REJECT/REQUEST_ALTERNATIVE | Backend API | full Backend + UI | FULL_STRONGER | KEEP |
| container/section preserveView refresh | `FinanceRefreshContext`, `RefreshButton` | full hook의 refresh만 존재 | PARTIAL | TRANSPLANT |
| Market/BM source panel 및 상세 근거 | donor page | full page | PARTIAL | ADAPT: legacy TechOps surface 제거, 실제 source만 표시 |
| Finance header/readiness | donor page | full page | FULL_STRONGER | KEEP: donor TechOps 문구는 OBSOLETE |
| 분석 TaskRun/SSE | donor 동기 API | full async service/worker/hook | FULL_STRONGER | KEEP |
| 3개년 손익·BEP·운전자금·월별 표/차트 | donor report | `AnalysisReport.jsx` | FULL_STRONGER | KEEP |
| Monte Carlo P10/P50/P90·손실·payback·stress | donor report | `AnalysisReport.jsx` | FULL_STRONGER | KEEP |
| findings/cautions/actions/disclaimer | donor report | `AnalysisReport.jsx` | FULL_STRONGER | KEEP |
| 다음 단계 CTA | donor `/panel-survey` | full Finance page | MISSING | ADAPT: 실제 `/marketing` route 사용 |
| 공통 Shell Finance 설명 | donor/제품 의도 | `ProjectModulePages.jsx` | MISSING | ADAPT: TechOps 문구 제거 |
| Finance 진행 이벤트 문구 | donor/제품 의도 | `jobEventMessages.js` | MISSING | ADAPT: TechOps 문구 제거 |
| FinancialInputSnapshot 계약 | donor contract | full schema/factory | PARTIAL | ADAPT: Market/BM 필수, legacy 필드 nullable |
| 외부 Finance handoff current authority | donor integration | full `ModuleIntegrationService` | MISSING | ADAPT: current Market/BM snapshot 조회 |
| legacy TechOps-linked row 읽기 호환 | donor legacy domain | full nullable legacy fields/constructors/repos | FULL_STRONGER | KEEP |
| Finance module status | donor module status | full `ProjectModuleStatusService` | FULL_STRONGER | KEEP: TechOps와 독립 |

명시적 제외: donor의 TechOps prerequisite 문구·demo 흐름·polling 보조는 각각 확정 아키텍처 및 full SSE runtime과 충돌하므로 이식하지 않는다.

### 2.2 Marketing

| aidev 기능 | aidev 파일 | full 대응 | 변경 전 상태 | 조치 |
| --- | --- | --- | --- | --- |
| CPV2 current selection → same-project/non-stale seed → exact concept | `MarketingSourceSnapshotService` | 동일 파일 | SAME | KEEP |
| CPV2 실패 시 legacy fallback 차단 | source service tests | 동일 서비스/tests | SAME | KEEP |
| legacy project source | source service/factory | 동일 파일 | SAME | KEEP |
| content type/channel/purpose/tone/length/CTA | setup/model | 동일 파일 | SAME | KEEP |
| required/excluded/additional instruction | setup/model | 동일 파일 | SAME | KEEP |
| reference file selection/upload/artifactId request | page/hook/API | 동일 파일 | FULL_STRONGER | KEEP: client MIME/size 검사 추가 |
| same-project reference 및 PNG/JPEG/20MB | Backend evidence/content service | 동일 파일 | FULL_STRONGER | KEEP |
| reference 없음 generate / 있음 edit | `marketing_image.py` | 동일 파일 | SAME | KEEP, 분기 테스트 보강 |
| commercial-quality 이미지 prompt | `marketing_image.py` | 동일 파일 | PARTIAL | TRANSPLANT |
| legal-before-image | `service.py` | 동일 파일 | FULL_STRONGER | KEEP |
| provider artifactRefs 주입 차단 | strict result model/service | 동일 파일 | FULL_STRONGER | KEEP |
| exact `ai-artifacts/{UUID}.jpg`·JPEG·20MB·storage 검증 | AI/Backend storage/completion | 동일 파일 | FULL_STRONGER | KEEP |
| rollback cleanup·revision/asset bind·presigned URL | Backend completion/content service | 동일 파일 | FULL_STRONGER | KEEP |
| MarketingSourceSummary/setup/list/progress | donor active page/components | 동일 파일 | SAME | KEEP |
| integrated image+copy canvas/style/editor/legal | donor components/page | 동일 파일 | SAME | KEEP |
| revision/copy/download/save/regenerate/finalize | donor components/page/hook | 동일 파일 | SAME | KEEP |
| empty/failure/stale state | donor page/hook | 동일 파일 | SAME | KEEP |
| standalone Marketing Visual legacy runtime 보존 | donor에서는 제거 | full legacy runtime | FULL_STRONGER | KEEP: active page에는 노출하지 않음 |
| Marketing module status | donor module status | full `ProjectModuleStatusService` | FULL_STRONGER | KEEP |

## 3. Finance 구현 결과

- TechOps 결합: 새 preparation, Snapshot identity, 분석 input, module status, 외부 handoff, 공통 Shell/JobEvent 문구에서 제거했다. 기존 row 읽기 호환을 위한 nullable legacy field·constructor·repository는 삭제하지 않았다.
- source authority: `MarketResearchService.current(FULL/BM)`의 version 존재·non-stale와 exact Market→BM lineage를 사용한다. 외부 handoff도 같은 current version 조합의 Snapshot만 사용한다.
- default/provenance: Concept hypothesis → Market assumption → BM financial handoff/assumption의 우선순위를 보존하고 사용자 입력·채택값은 덮어쓰지 않는다.
- AI estimate: donor의 상세 economic sanity, 단위원가, 가격비율, Tavily benchmark, 산술 일치 지침을 복원했다. typed Money/churn/count/정확한 1·2·3년 및 bounded repair 1회와 full guardrail을 유지했다.
- UI: container/section preserve-view refresh, 그룹 추천, loading/failed/retry/alternative, 설명·가정·confidence, 실제 Market/BM/Concept source surface를 연결했다.
- proposal preview: 추천값은 input에 표시하지만 React draft에는 쓰지 않는다. 일반 저장 payload는 null을 유지하며 ACCEPT 또는 EDIT_AND_ACCEPT만 사용자 결정을 확정한다.
- report: full의 async TaskRun/SSE와 `AnalysisReport`가 donor의 P&L, BEP, 운전자금, 월별 표/차트, Monte Carlo, P10/P50/P90, 손실/회수 확률, stress, findings/cautions/actions/disclaimer를 모두 제공한다.
- 다음 단계: donor의 존재하지 않는 `/panel-survey` 대신 현재 Target의 `/marketing`을 사용한다.
- module status: latest Market run에 해당하는 materialized version이 없으면 Finance를 current로 보지 않도록 보강했다. TechOps 상태는 독립이다.

## 4. Marketing 구현 결과

- CPV2 authority와 legacy-only fallback 정책은 donor와 동일하다. CPV2가 존재하면 missing/stale/foreign/concept mismatch를 legacy로 우회하지 않는다.
- setup, source summary, reference image, content list/progress, integrated image+copy canvas, style/editor/legal/revision/copy/download/save/regenerate/finalize, empty/failure/stale UI는 SAME이다.
- reference artifact는 Frontend PNG/JPEG·20MB 검사, same-project Backend 검증, secured AI download, request `referenceArtifactId` 전달을 유지한다.
- donor commercial image prompt의 professional retail/brand, hero/material/light/depth/negative space, text/logo/UI/legal 금지, prohibited claim 금지, reference shape/color/proportion/packaging/material 보존, clip-art/collage/props/distortion/text-area 회피 문구를 전부 복원했다.
- strict result validation 뒤 prohibited/excluded phrase, `legalReview.compliant`, required disclosure, required phrase를 이미지 전에 검사한다. 실패 시 이미지 함수는 호출되지 않는다.
- 생성물은 JPEG signature·20MB, exact `ai-artifacts/{UUID}.jpg`, ObjectStorage 존재/MIME/size, rollback cleanup, revision/asset binding, presigned URL을 유지한다.
- legacy Marketing Visual runtime은 삭제하지 않았고 active Marketing Content page에는 노출하지 않는다.

## 5. Migration preflight

### V20

정적 감사에서 확인된 실제 위험은 legacy TechOps snapshot만 다르고 `(project_id, market_version_id, bm_version_id)`가 같은 활성 Finance row가 2개 이상 존재하는 경우다. V18 unique에서는 허용되지만 V20 unique에서는 충돌한다.

정확한 preparation/Snapshot duplicate query와 row id 배열 출력은 `docs/rebuild/verification/AIDEV-RESYNC-R1_USER_VERIFICATION.md`에 기록했다. 실제 사용자 PostgreSQL에는 접속하지 않았으므로 운영 duplicate 결과는 **미검증**이다.

Testcontainers 회귀 테스트에는 다음 계약을 추가했다.

1. V19 schema에 위 중복을 만들면 V20은 실패한다.
2. 실패 뒤 두 row가 모두 남아 있어 migration이 데이터를 삭제하지 않는다.
3. 해결은 자동 DELETE가 아니라 history를 보존하는 감사된 soft-delete 전략을 사용한다.

### V21

기존 row는 V1의 `selection_id`, `concept_id` NOT NULL과 project-scoped FK를 이미 만족한다. V21의 `source_type NOT NULL DEFAULT 'LEGACY'`와 nullable CPV2 columns는 기존 row를 legacy authority로 유지한다. V12에 CPV2 `(id, project_id)` unique target도 존재한다.

Testcontainers 회귀 테스트에는 V20까지의 legacy Marketing Source를 삽입한 뒤 V21이 1개 migration으로 적용되고 `LEGACY`, 기존 selection/concept 보존, CPV2 columns null임을 검증하는 시나리오를 추가했다.

- 기존 migration 수정: 0
- 추가 migration: 없음
- actual PostgreSQL 실행: 미실행

## 6. 테스트

| 영역 | 실행 범위 | 파일/클래스 | passed | failed | skipped |
| --- | --- | ---: | ---: | ---: | ---: |
| Backend | Finance, Marketing, Marketing Visual compatibility, module status, integration | 17 classes | 68 | 0 | 0 |
| AI | finance estimate/report/Tavily, marketing content/visual, provider/task alignment | 7 files | 30 | 0 | 0 |
| Frontend | Finance 전체, Marketing Content 전체, shared JobEvent message | 14 files | 39 | 0 | 0 |
| 합계 | targeted + 영향 패키지 회귀 | 38 files/classes | 137 | 0 | 0 |

PostgreSQL Testcontainers test source는 `compileTestJava`로 컴파일했으나 Docker를 실행하지 않았으므로 실행 결과에 포함하지 않았다.

## 7. Build 및 정적 검증

- Backend `compileJava`: PASS
- Backend `compileTestJava`: PASS
- AI `python -m compileall -q app tests`: PASS
- Frontend production build: PASS, 259 modules transformed
- 변경 JS/JSX 8개 파일 ESLint: PASS
- `git diff --check`: PASS
- 전체 `npm run lint`: 기존 비변경 파일의 baseline 12 errors 때문에 FAIL. 변경 Finance 파일에서 당시 1 warning을 확인해 수정했고, 최종 변경 파일 직접 ESLint는 0 error/0 warning이다.
- Vite build는 500kB 초과 chunk warning 1건이 있었으나 build는 성공했다.

## 8. 보호 영역

최종 path diff gate에서 변경 0을 확인했다.

- CPV2 core
- Market algorithm
- BM algorithm
- Twin 및 Twin Bank
- TechOps product logic
- Finance deterministic calculation
- Finance Monte Carlo
- TaskRun core
- JobEvent/SSE core
- 기존 migration V1~V21
- `.env`

Project module status와 shared integration adapter는 Finance authority를 수정하기 위해 의도적으로 변경했다.

## 9. LIVE 미검증

다음은 실행하지 않았으며 PASS로 간주하지 않는다.

- Docker
- actual PostgreSQL Flyway V20/V21 및 운영 duplicate query
- OpenAI text/image provider
- Tavily
- MinIO/ObjectStorage
- 실제 browser journey 및 presigned download

## 10. 최종 aidev → full parity gate

두 branch는 common merge base가 없어서 triple-dot을 사용하지 않았다. `origin/aidev`와 현재 worktree의 tree/path/function/observable behavior를 직접 비교했다.

### Finance 최종 표

| aidev observable feature | full implementation | parity |
| --- | --- | --- |
| Market/BM source·readiness·lineage | `FinancialService.currentSources`, module status | FULL_STRONGER |
| preparation default·provenance | `FinancialPreparationFactory` | FULL_STRONGER |
| single/alternative recommendation | TaskRun worker + Finance UI | FULL_STRONGER |
| group recommendation | `useFinance.generateEstimates`, Finance page | ADAPTED |
| proposal preview | 표시 전용 preview helpers | ADAPTED |
| explicit decision semantics | Backend API + 4개 UI action | FULL_STRONGER |
| refresh | RefreshContext adapter + canonical REST/SSE | ADAPTED |
| source panel | Market/BM/Concept/evidence detail | ADAPTED |
| typed 1·2·3년/churn/count/Money | AI schema/service + UI | FULL_STRONGER |
| economic sanity/Tavily | donor prompt + full deterministic guardrail | FULL_STRONGER |
| analysis report/표/차트/Monte Carlo | async `AnalysisReport` | FULL_STRONGER |
| next step | current `/marketing` route | ADAPTED |
| loading/error/stale/status | Finance page + TaskRun/SSE | FULL_STRONGER |

### Marketing 최종 표

| aidev observable feature | full implementation | parity |
| --- | --- | --- |
| CPV2/legacy Marketing Source | source service/factory | FULL_STRONGER |
| setup 전체 필드 | setup/model/request | SAME |
| reference selection/upload/edit | page/hook/API/secured download | FULL_STRONGER |
| commercial image constraints | `marketing_image._image_prompt` | SAME |
| legal-before-image | AI deterministic pre-image guard | FULL_STRONGER |
| JPEG/exact artifact path/storage | AI + Backend completion/storage | FULL_STRONGER |
| source/list/progress/empty/failure/stale | active page/components | SAME |
| integrated image+copy canvas/style/editor/legal | active page/components | SAME |
| revision/copy/download/save/regenerate/finalize | page/hook/API | SAME |
| legacy Visual compatibility | retained inactive runtime | FULL_STRONGER |

```text
Finance AIDEV observable feature MISSING = 0
Marketing AIDEV observable feature MISSING = 0
```

**AIDEV-RESYNC R1 COMPLETE**
