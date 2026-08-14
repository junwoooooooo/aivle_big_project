# V12 Workspace Composition · Quick Work Center · Historical Job UX 결과

## 판정

**상태: HOLD — 구현 및 자동 검증 완료, 인증 프로젝트 화면의 실측 검증 미완료**

V11의 기능 계약을 유지하면서 Journey 전체 노드 링크, Quick Work Center 폭 계약, Full Work Center 고정 높이, 종료 작업의 저장 이벤트 조회, Idea 중심의 반응형 입력 Workspace를 구현했다. 다만 로컬 브라우저에서 프로젝트 경로가 로그인 화면으로 이동하여 사용자가 요구한 인증 화면 bounding rect와 1440×900·1920×1080 스크린샷을 수집하지 못했다. 이 문서는 해당 항목을 PASS로 간주하지 않는다.

## START SHA

| 항목 | 결과 |
|---|---|
| `git fetch origin full` | 실패 — 현재 실행 환경에서 GitHub 연결 불가 |
| branch | `full` |
| START HEAD | `a452f15b04af8ce996ade8993a1e31e59cb4a249` |
| 로컬 `origin/full` | `a452f15b04af8ce996ade8993a1e31e59cb4a249` |
| 예상 SHA와 일치 | 예 |
| 시작 worktree | clean |

Fetched `origin/full`을 새로 확인하지 못했으므로 네트워크 복구 후 START GATE를 다시 실행해야 한다. 현재 로컬 기준으로는 HEAD와 `origin/full`이 일치한다.

## V11 보호 계약

- Body scroll lock과 cleanup 유지
- Quick/Full 상호 배제 유지
- Full Work Center의 `document.body` portal 유지
- Bottom Sheet, focus trap, 내부 scroll 유지
- V10 history pagination 유지
- Journey 1→6 순서 유지
- `ProjectFormRow`, `ProjectFormSection`, `FileDropzone` 유지
- canonical hash, lineage, stale/current, TaskRun 의미와 backend upload 계약 변경 없음
- V11에서 남긴 인증 Project 화면 bounding rect 미검증 상태를 이번 실측 전까지 유지

## QUICK WIDTH ROOT CAUSE

코드 기준 직접 원인은 compact root가 `.pipeline-task-center`의 `display:flex; justify-content:space-between`을 그대로 상속한 점이다. Quick 내부의 header, summary, groups, 전체 작업 보기 버튼이 세로 콘텐츠가 아니라 가로 flex item으로 배치되었고, `.job-center--compact`에는 padding과 border 제거만 있어 popover content box 전체를 쓰는 명시적 폭·layout 계약이 없었다.

수정:

- popover에 `box-sizing:border-box` 부여
- 직계 compact root를 `display:grid`, `width:100%`, `min-width:0`, `max-width:none`, stretch로 고정
- header, summary, groups, group, detail button에 `width:100%`, `min-width:0`, `box-sizing:border-box` 적용

### QUICK BOUNDING RECT BEFORE/AFTER

| 뷰포트 | Before | After | 판정 |
|---|---:|---:|---|
| 1440×900 | 사용자 실제 화면에서 좌측 쏠림 확인, 수치 미수집 | 인증 세션 부재로 수치 미수집 | 미검증 |
| 1920×1080 | 수치 미수집 | 인증 세션 부재로 수치 미수집 | 미검증 |

필수 비율 `jobCenter/popoverContent >= 0.96`, `summary/jobCenter >= 0.98`, `detailButton/jobCenter >= 0.98`은 실제 인증 화면에서 아직 확인하지 못했다.

## SHEET HEIGHT BEFORE/AFTER

이전에는 `max-height:min(78dvh,54rem)`만 있어 콘텐츠가 적으면 sheet 자체가 축소될 수 있었다. 이후에는 Desktop `height:min(78dvh,54rem); max-height:none`, Mobile `height:88dvh`를 사용하고 header와 내부 scroll 영역을 분리했다.

| 항목 | Before | After | 실측 판정 |
|---|---|---|---|
| 전체 → 진행 중 0건 → 전체 | 콘텐츠 양에 따라 shrink 가능 | 동일 CSS height 계약 | 인증 화면 미검증 |
| 전체 → 입력 필요 0건 | 콘텐츠 양에 따라 shrink 가능 | 동일 CSS height 계약 | 인증 화면 미검증 |
| 전체 → 완료·종료 | 콘텐츠 양에 따라 shrink 가능 | 동일 CSS height 계약 | 인증 화면 미검증 |

상태별 빈 문구를 분리했다.

- 현재 진행 중인 작업이 없습니다.
- 지금 입력이 필요한 작업이 없습니다.
- 완료되거나 종료된 작업이 없습니다.

## TERMINAL EVENT ROOT CAUSE

기존 `useProjectJobs`는 선택 작업이 active 목록에 있을 때만 `liveJobId`를 만들고 `useJobEvents(liveJobId)`를 호출했다. terminal/recent/history 작업은 `liveJobId=null`이므로 저장된 `JobEvent`가 있어도 frontend가 조회하지 않았고, 상세 화면은 이를 실제 0건으로 오해했다.

해결:

- 기존 `GET /api/v2/jobs/{jobId}/events?after={sequence}` 재사용
- terminal 선택 시 `after=0`부터 `hasMore=false`까지 `nextSequence`로 조회
- 최대 50 page 안전 경계 적용
- 작업 전환·unmount 시 `AbortController`로 이전 요청 중단
- presentation에는 `events` 한 shape만 전달
- REST 조회 완료 후 실제 0건일 때만 “이 작업에는 저장된 처리 기록이 없습니다.” 표시
- backend endpoint, 저장 방식, TaskRun 의미 변경 없음

## EVENT SOURCE MATRIX

| 작업 상태/위치 | 소스 | 연결 정책 | 결과 |
|---|---|---|---|
| QUEUED / READY / RUNNING / NEEDS_INPUT, active 목록 | SSE `useJobEvents` | 기존 live 연결 유지 | 구현·테스트 PASS |
| SUCCEEDED / COMPLETED / FAILED / CANCELLED / TIMED_OUT / RESOLVED_INPUT, recent/history | persisted REST | bounded pagination, SSE 미연결 | 구현·테스트 PASS |
| terminal + 저장 event 5건 | REST | 2 page를 통합 | 5건 테스트 PASS |
| FAILED + 저장 event | REST | 실패 과정 보존 | 테스트 PASS |
| terminal + 실제 0건 | REST | 조회 완료 뒤 empty | 테스트 PASS |
| terminal 작업 전환 | REST | 이전 request abort | stale overwrite 방지 테스트 PASS |

## OVERVIEW INTERACTION MATRIX

| 상호작용 | 변경 결과 | 자동 검증 | 실제 화면 |
|---|---|---|---|
| node body click | node 전체가 하나의 React Router `Link` | PASS | 미검증 |
| arrow click | 동일 Link 내부의 시각 affordance | PASS | 미검증 |
| keyboard Enter | semantic link 기본 동작 | 구조 PASS | 미검증 |
| tab stop | node당 1개, arrow는 `aria-hidden` | 구조 PASS | 미검증 |
| hover/focus-visible/active | 미세 배경·border·shadow | CSS PASS | 미검증 |
| status-aware aria-label | “시작하기/계속하기/결과 보기” 조합 | PASS | 미검증 |

station의 실제 점을 `--station-x`, `--station-y`로 정의했다. `li`의 top/left가 station/path 점이고, card는 `margin-top:1.55rem`으로 별도 offset을 갖는다. station은 link 기준 `top:-2.55rem`, 높이 `2rem`이므로 station 중심이 다시 `li`의 실제 좌표에 놓인다. SVG path와 station 좌표 계약은 코드상 일치하지만 실제 경계·곡선 정렬은 인증 화면에서 확인해야 한다.

## IDEA SPLIT WORKSPACE BEFORE/AFTER

| 구분 | Before | After |
|---|---|---|
| Desktop 구성 | 넓은 1열 | `1.55fr / .95fr`, 최대 92rem |
| 필수 입력 | 3개 row | 왼쪽 3개 row + 완료 수 |
| 참고 자료 | 별도 큰 section | 왼쪽 필수 section 내부 divider + Dropzone |
| 선택 입력 | 하나의 전체 `<details>` 안에 10개 | 오른쪽에 10개 항목 이름을 항상 노출 |
| 선택 상태 | 열기 전 파악 불가 | empty/filled/error/expanded 요약 |
| interaction | 전체 열기/닫기 | 기본 single-open accordion |
| 입력 보존 | parent draft | 동일 parent draft 유지 |
| 실행 문구 | 안전 확인 및 AI 해석 | 입력 내용으로 사업안 만들기 |
| sticky action | floating sticky | Idea 첫 입력 화면에서는 일반 footer action |

## INPUT PAGE MIGRATION MATRIX

| 화면 | 판정 | V12 적용 |
|---|---|---|
| Idea Intake | input-heavy reference | 필수·자료 / 선택·현황 split 적용 |
| TechOps | 핵심 사실과 보완 결정·근거가 동시 존재 | 사실 / 운영 가설·근거 split 적용 |
| Finance | 핵심 가정과 세부 CAC·조건·도움말이 동시 존재 | 핵심 비용·목표·수익 / 세부 CAC·조건·도움말 split 적용 |
| Twin | 비교안 편집 → 표본 → 결과의 순차 단계 | 2열 강제하지 않음 |
| Marketing | setup → 생성 → 편집·Preview의 단계형 화면 | result/preview 회귀 방지를 위해 2열 강제하지 않음 |
| Market 재수집 | 기존 결과 안의 작은 고급 옵션 | 결과 화면 전체를 입력 layout으로 바꾸지 않음 |
| Business Model/Market 결과/Final Report | result-heavy | 변경 없음 |

## RESPONSIVE MATRIX

| viewport | 기대 계약 | 자동/코드 검증 | 실제 화면 |
|---|---|---|---|
| 1920 Desktop | 62/38 근사 2-pane, max 92rem | CSS contract PASS | 미검증 |
| 1440 Desktop | 62/38 근사 2-pane | CSS contract PASS | 미검증 |
| 1024 Tablet | 1-pane, primary → secondary | CSS contract PASS | 미검증 |
| 390 Mobile | full width accordion, horizontal overflow 0 | CSS contract 존재 | 미검증 |

## TEST MATRIX

| 범위 | 명령/검사 | 결과 |
|---|---|---|
| Frontend 관련 테스트 | Vitest 7 files, 25 tests | PASS |
| Journey | whole-link 구조·라벨 | PASS |
| Job Center | portal/focus/filter copy/terminal 5 events | PASS |
| useProjectJobs | live/terminal/history pagination/switch/0건 | PASS |
| Idea/Workspace | split/10 accordion/accessibility/state | PASS |
| TechOps/Finance | 기존 page tests | PASS |
| changed-file lint | ESLint 변경 JS/JSX | PASS |
| Frontend build | `npm.cmd run build` | PASS, 기존 chunk size warning만 존재 |
| Backend JobEvent | Controller/API/Publisher 3개 target suite | PASS |
| `git diff --check` | whitespace 검사 | PASS |

## LIVE VISUAL MATRIX

| 대상 | 1440×900 | 1920×1080 | 상태 |
|---|---|---|---|
| Quick open screenshot + rect | 미수집 | 미수집 | BLOCKED |
| Full Sheet filter rect 비교 | 미수집 | 미수집 | BLOCKED |
| Journey node/station/path | 미수집 | 미수집 | BLOCKED |
| Idea Desktop | 미수집 | 미수집 | BLOCKED |
| Idea Tablet 1024 | 미수집 | 해당 없음 | BLOCKED |
| Idea Mobile 390 | 미수집 | 해당 없음 | BLOCKED |

브라우저 확인 결과 `http://localhost:3000/app/projects/1/overview`와 `http://localhost:5173/app/projects/1/overview` 모두 `/auth/login`으로 이동했다. 별도 Chrome/외부 브라우저 연결도 사용할 수 없었다. 인증 우회, 임의 계정 생성, token/cookie 검사는 수행하지 않았다.

## REMAINING GAP

1. 네트워크가 가능한 환경에서 `git fetch origin full`을 다시 실행해 fetched authority를 확인한다.
2. 인증된 실제 프로젝트 화면에서 Quick 6개 selector의 bounding rect/computed style을 수집한다.
3. 1440×900·1920×1080 Quick screenshot과 폭 비율을 확인한다.
4. Full Sheet 필터 전후 outer rect 높이 차이가 1 CSS px 이하인지 확인한다.
5. Journey node border, station, SVG path의 실제 정렬과 body/arrow click을 확인한다.
6. Idea를 1440/1920/1024/390에서 확인하고 horizontal overflow 0을 검증한다.

위 항목이 남아 있으므로 V12를 COMPLETE로 선언하지 않는다.
