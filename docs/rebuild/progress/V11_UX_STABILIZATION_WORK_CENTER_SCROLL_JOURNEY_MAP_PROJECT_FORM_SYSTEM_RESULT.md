# V11 UX 안정화 결과

## 상태

- 구현 및 자동 검증: 완료
- 인증된 실제 프로젝트 화면 시각 검증: 미완료(로컬 Docker 부재, 3000/5173 모두 로그인으로 리디렉션)
- 최종 판정: **사용자 Live 확인 전 조건부 완료**

## START SHA

- 작업 브랜치: `full`
- 시작 `HEAD`: `3b56f090c578987891484febe56d126bfc0bf3fc`
- `origin/full`: `3b56f090c578987891484febe56d126bfc0bf3fc`
- 시작 시 작업 트리: clean

## SCROLL ROOT CAUSE

`ProjectContextTools`는 프로젝트 셸에 계속 마운트되지만 Full Work Center의 `sheet` 상태는 경로와 독립적이었다. Full을 연 상태에서 `업무 화면 열기` 또는 다른 프로젝트 경로로 이동하면 컴포넌트가 언마운트되지 않아 `sheet.mounted`와 `document.body.style.overflow = hidden`이 남을 수 있었다. 직접 DOM 스타일을 관리하는 effect도 여러 overlay가 겹칠 때 앞선 overflow 값을 잘못 복원할 수 있었다.

해결 내용:

- `useBodyScrollLock(active)` 공통 훅을 추가하고 참조 수로 중첩 잠금을 관리했다.
- 최초 inline overflow를 저장하고 마지막 잠금 해제 또는 unmount 때만 복원한다.
- `location.pathname` 변경 시 Full/Quick/Navigator 상태와 닫기 타이머를 즉시 초기화한다.
- `업무 화면 열기` 클릭 자체에서 Full을 즉시 닫고 잠금을 해제한다.

## SCROLL LOCK MATRIX

| 경로 | 기대 | 자동 검증 |
|---|---|---|
| 일반 프로젝트 화면 | `overflow !== hidden` | PASS |
| Quick만 열기 | 잠금 금지 | PASS |
| Full 열기 | `overflow === hidden` | PASS |
| X 닫기 | 원래 값 복원 | PASS |
| Backdrop 닫기 | 원래 값 복원 | PASS |
| Escape 닫기 | 원래 값 복원 | PASS |
| 업무 화면 링크/route 이동 | Full 제거 및 복원 | PASS |
| 컴포넌트 unmount | 복원 | PASS |
| 중첩 잠금 2개 | 마지막 해제 때만 복원 | PASS |

## WORK CENTER BEFORE/AFTER

| 구분 | 이전 | 이후 |
|---|---|---|
| Full 외곽 | 우측 Drawer | 화면 하단 중앙 Bottom Sheet |
| DOM 위치 | Quick popover 계층에 종속될 수 있음 | `createPortal(..., document.body)` |
| Quick → Full | 두 UI가 동시 표시될 수 있음 | Quick 즉시 제거 후 Full 1개만 렌더 |
| 크기 | 우측 상단 stacking context 영향 | `min(74rem, 100vw - 2rem)`, 최대 `78dvh/54rem` |
| 스크롤 | body 잠금 잔류 가능 | header 고정, content만 스크롤, 종료 시 복원 |
| 종료 | 경로 이동 뒤 잔류 가능 | X/Backdrop/Escape/route/unmount 모두 정리 |
| 초점 | 복귀만 일부 지원 | 최초 닫기 버튼 초점, Tab 순환, 종료 후 작업 trigger 복귀 |
| 0건 | 빈 흰 영역 | 명시적 empty state와 안내 문구 |

V10의 Quick 1/1/3, 사용자 친화적 작업명, 통합 목록, 필터, `/jobs/history?page=&size=` pagination, 상세 헤더와 기술 정보 접기는 유지했다.

## WORK CENTER DOM/PORTAL MATRIX

| 항목 | 결과 |
|---|---|
| Full 활성 중 `.project-work-popover` | 0개, PASS |
| Full sheet 개수 | 1개, PASS |
| Full의 부모 영역 | `document.body` portal, PASS |
| Quick와 Full 동시 렌더 | 없음, PASS |
| Full dialog semantics | `role=dialog`, `aria-modal=true`, PASS |
| Focus trap/return | PASS |
| 실제 DOM bounding rect | 인증 화면 접근 불가로 미검증 |

## JOURNEY MAP BEFORE/AFTER

| 항목 | 이전 | 이후 |
|---|---|---|
| Desktop 읽기 순서 | `1 2 3 / 6 5 4` | 왼쪽에서 오른쪽 `1 → 6` |
| 배치 | 역순 grid | normalized x/y 기반 상하 stagger |
| 곡선 | station과 시각 오차 | 각 station 중심 좌표와 맞춘 SVG path |
| action icon | 모두 `arrowRight` | 메뉴 열기 의미의 `arrowUpRight` |
| Mobile | 압축된 가로 흐름 | 1100px 이하 세로 roadmap |

semantic DOM 순서, 화면 읽기 순서, action 의미를 분리하지 않았으며 1~6 순서와 SVG path/icon을 테스트로 고정했다.

## FORM INVENTORY

| 영역 | 입력 유형 | V11 처리 |
|---|---|---|
| 프로젝트 생성/사업 개요 | TextInput/Textarea | 공통 horizontal layout 적용 |
| Idea Intake/누락 Seed | textarea, 참고 파일 | `ProjectFormRow/Section`, Dropzone 적용 |
| Concept 선택 | 선택 이유, 가설 수정 | 공통 horizontal layout 적용 |
| Concept 후보/비교 카드 | checkbox/radio, 구조화 후보 편집 | 카드·결정 맥락을 유지한 전용 UI |
| Market | 경쟁사 Seed, 재수집 옵션 | 공통 horizontal layout 적용 |
| Business Model | 결과/실행 중심, 일반 입력 없음 | 억지 적용하지 않음 |
| TechOps | 사양, 비용, 목표, 근거 파일 | 공통 행 레이아웃, Dropzone 적용 |
| Finance | 숫자·통화·목표 입력 | 기존 도메인 컨트롤 유지, 공통 행 레이아웃 적용 |
| Twin Survey | 상황 문장, 쌍 편집, 표본 slider | 상황 문장 공통 행; 카드/slider는 전용 UI 유지 |
| Marketing | 생성 설정, copy/style/visual, 이미지 | 공통 행 레이아웃, 이미지 Dropzone 2곳 적용 |
| Final Report | 실제 편집 입력 없음 | 적용하지 않음 |

## FORM MIGRATION MATRIX

| 화면 | 공통 구조 | 검증 |
|---|---|---|
| Idea Intake | `ProjectFormSection`, `ProjectFormRow` | PASS |
| 필수 Seed 보완 | `ProjectFormRow` | PASS |
| 프로젝트 생성/사업 개요 | `project-form-layout` + 기존 `FormField` | 기존 테스트 PASS |
| Market 경쟁사/재수집 | `project-form-layout` | 기존 테스트 PASS |
| Concept 선택 이유/가설 수정 | `project-form-layout` | 기존 테스트 PASS |
| Finance | `finance-form-grid project-form-layout` | PASS |
| TechOps | `tech-ops-form-grid project-form-layout` | PASS |
| Twin 상황 문장 | `ProjectFormRow` | PASS |
| Marketing 설정 | `ProjectFormRow` | PASS |
| Marketing copy/style/visual | `project-form-layout` | build/lint PASS |
| 760px 이하 stack | 공통 media query에서 1열 전환 | 정적 CSS 확인, 실기기 미검증 |

공통 CSS는 전역 `FormField` 기본 동작을 변경하지 않고 `.project-form-layout` 아래에서만 horizontal 규칙을 활성화한다.

## FILE UPLOAD MATRIX

| 상태/기능 | 결과 |
|---|---|
| Normal/Hover/Focus/Drag | 공통 style 및 drag state 구현 |
| Uploading | `aria-busy`, 입력/제거 비활성, 상태 문구 구현 |
| Uploaded | 선택 파일명·크기·제거 버튼, 완료 색상 구현 |
| Error/Disabled | 오류/비활성 style 및 semantics 구현 |
| 키보드/label | native file input과 label 연결 유지 |
| 다중 파일 | Idea Intake에서 유지 |
| API/validation contract | 변경 없음 |
| 적용 화면 | Idea 참고 파일, TechOps 근거, Marketing 참고/visual 이미지 |

## TEST MATRIX

| 명령/대상 | 결과 |
|---|---|
| V11 최종 targeted Vitest | 15 files, 48 tests PASS |
| 변경 JS/JSX ESLint | PASS, 오류/경고 없음 |
| `npm.cmd run build` | PASS, 270 modules |
| `git diff --check` | PASS |
| 보호 영역 | Task/AI/domain/API/storage/hash/lineage 변경 없음 |

빌드에는 기존 500kB 초과 chunk 경고가 남지만 빌드 실패는 아니다.

## VISUAL MATRIX

| 환경 | 결과 |
|---|---|
| 로컬 5173 공개/로그인 화면 | Chromium 로드 PASS, console error 0 |
| 로컬 3000 공개/로그인 화면 | Chromium 로드 PASS |
| 인증 프로젝트 접근 | `/app/projects/1` → `/auth/login`, 미검증 |
| Docker/Postgres | Docker 실행 파일 없음 |
| Desktop 1920×1080 | 인증 프로젝트 시각 검증 미완료 |
| Desktop 1440×900 | 인증 프로젝트 시각 검증 미완료 |
| Mobile 390×844 | 인증 프로젝트 시각 검증 미완료 |
| Quick bounding rect | 인증 프로젝트 시각 검증 미완료 |

## REMAINING GAP

1. 실제 사용자 계정과 데이터가 있는 환경에서 1920×1080, 1440×900, 390×844 시각 검증이 필요하다.
2. Quick의 popover/summary/group/detail button bounding rect와 Full의 실제 긴 목록 내부 스크롤은 인증 환경에서 확인해야 한다.
3. 사업 기획·사업 검증·출시 준비·가상 인터뷰·마케팅 전략·최종 보고서의 실제 wheel/PageDown 스크롤은 사용자 Live 확인이 필요하다.
4. 20건 이상 실제 job history의 `이전 작업 더 보기`는 기존 API/코드를 유지했으나 이번 환경에서 실데이터 검증하지 못했다.

위 항목 때문에 사용자 Live 확인 전에는 V11을 무조건적인 COMPLETE로 판정하지 않는다.
