# Session 5 User-visible Feature Parity

기준 inventory는 `02_DONOR_UI_INFORMATION_INVENTORY.md`의 모든 `PRESERVE_REQUIRED=YES` 항목이다. 아래는 기능군별 최종 집계이며, 상세 행의 원문은 삭제하거나 축약하지 않고 기존 문서에 보존한다.

## 1. 공식 Product parity

| 영역 | 보존 정보/행동 | 최종 상태 | 누락 |
|---|---|---|---:|
| Market | 실행/재실행/상태, KPI, ledger, A1~A4/estimate/verdict, 계산식, source/evidence/grade, caveat/not-found, partial/empty/failure, BM 이동 | PORTED | 0 |
| BM | plan 4 cells, budget/duration/team, 빈칸 확인, BMC 9 blocks, fit/consistency, strength/weakness/risk, evidence/caveat/legal, financial handoff, empty/failure/actions | PORTED | 0 |
| Twin | stimulus draft/edit, gate warning, sample 50/100/300, progress, X/Y, profile/interview, decision, CI/MDE, class/short-cell/caveat/not-measurable, retry/failure | PORTED | 0 |
| TechOps | Target 준비/입력/제안/evidence/snapshot/status UI | KEEP_TARGET | 0 |
| Finance | upstream summary/provenance, 입력·AI 추정, finalize, deterministic 3-year P&L/cashflow/BEP/payback/working capital, sensitivity/stress/Monte Carlo/P10·P50·P90, report/fallback/source/caveat | PORTED | 0 |
| Marketing Content | source/legal, 8 content types, title/body/CTA/hashtags/imageBrief, editor/style, revisions, save/finalize/copy/download, empty/failure/stale | KEEP_TARGET / PORTED | 0 |
| Marketing Visual | 광고 배너 설명, promotion/main/sub copy, 7 tones, format, keyword, owned source image preview/remove, source summary, validation, generation state, preview, associated copy/revision/tone/format, retry/download/open | PORTED | 0 |

공식 required 항목의 `NOT_PORTED`는 0이다. Marketing Visual의 direct donor API, local output, timer mock은 기능 삭제가 아니라 Target runtime seam으로 대체했다.

## 2. REPLACED_SEAM

| donor 표현 | Target 표현 | 상태 |
|---|---|---|
| Market/Twin 화면 polling 또는 GET synchronize | Worker materialization + Job/Project SSE + canonical refresh | REPLACED_SEAM |
| donor direct AI/banner API | Backend Product API → TaskRun → Internal AI | REPLACED_SEAM |
| VirtualMarket mock product picker | current Marketing Source/selected Concept summary | REPLACED_SEAM |
| timer/canvas fake generation | 실제 TaskRun 단계와 Artifact preview | REPLACED_SEAM |
| AI local banner save | Project Artifact + MinIO persistence | REPLACED_SEAM |
| legacy “배너 저장” | 생성 시 영속화 + ownership download/open | REPLACED_SEAM |

## 3. DEV_ONLY / NOT_OFFICIAL

| surface | 상태 | 공식 Journey 차단 근거 |
|---|---|---|
| Finance demo/sandbox/duplicate API | DEV_ONLY / NOT_OFFICIAL | `/app/projects/:id/finance`에서 사용하지 않음 |
| sample Market fixtures/local research tools | DEV_ONLY | fixture flag/test 도구이며 FULL current authority 아님 |
| `VirtualMarket.jsx` legacy | DEV_ONLY / NOT_PORTED | AppRouter/Project navigation route 없음 |
| donor banner public FastAPI route | NOT_PORTED | Browser 공식 API로 노출하지 않음 |
| AI local research outputs | DEV_ONLY | MinIO/Artifact current authority 아님 |

## 4. Persona 및 일반 synthetic profile 구분

공식 route, module type, TaskType, controller, navigation, Marketing source에 Persona/Persona Interview/Persona Marketing 추가는 0이다. Twin 결과의 synthetic profile/가상 응답자 표시는 donor Twin 분석 결과의 일부이며 금지된 과거 Persona 제품 모듈이 아니다.

## 5. UI consistency 검증

- 모든 공식 모듈은 ProjectLayout의 journey/navigation/helper를 공유한다.
- Work Center는 Market과 BM을 subject type으로 구분하고, Twin/TechOps/Finance/Marketing 작업을 사람말로 표시한다.
- `NEEDS_INPUT`이 후속 refresh에서 해결되면 `RESOLVED_INPUT` notice로 교체한다.
- Visual 실패는 완료된 Content를 지우지 않지만 진행 중 Visual은 Work Center에서 계속 보인다.
- SSE 단절은 cursor 기반 SSE 재연결로 처리하며 수동 새로고침이나 REST polling을 요구하지 않는다.

## 6. CUTOVER-R1 상태·진행 표시 보정

| 사용자 표시 | canonical source | R1 상태 |
|---|---|---|
| Market 실제 조사 단계 | AI safe progress → JobEvent/Work Center | A1/A2/A3/A4/B/C 및 결과 정리 경계를 표시 |
| Business Model 실제 단계 | AI safe progress → JobEvent/Work Center | restore/adapter/model/serialization 표시 |
| Twin 실제 조사 단계 | AI safe progress → JobEvent/Work Center | gate/bank/sampling/wave/aggregate 표시, wave는 실제 완료 셀 기준 bounded update |
| Marketing Visual 생성 중 | Backend worker coarse event | 관측하지 않은 copy/image/composition 개별 완료 표시는 제거 |
| Twin Draft/Finance Estimate 진행 | Project Module Status overlay | Work Center와 Journey의 active 상태를 일치시킴; 보조 task 실패는 완료 결과를 덮지 않음 |
