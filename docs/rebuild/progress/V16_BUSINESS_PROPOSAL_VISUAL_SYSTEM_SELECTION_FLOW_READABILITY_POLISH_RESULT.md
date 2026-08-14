# V16 사업안 시각 체계·선택 흐름·가독성 개선 결과

## 완료 상태

- 기능 구현: **COMPLETE**
- 자동 검증: **PASS**
- 시각 수용성: **USER REVIEW PENDING**
- 인증 브라우저 화면은 V16 정책에 따라 기능 완료 조건에서 분리했다. 자동 검증으로 확인할 수 없는 색감, 밀도, 실제 콘텐츠 길이에서의 시선 흐름은 사용자 시각 검토 항목으로 남긴다.

## START SHA

| 항목 | 값 |
| --- | --- |
| branch | `full` |
| 작업 시작 HEAD | `1b1fed52de26f9e7c7ca830a539c199b9cbe3bdf` |
| 로컬 `origin/full` | `1b1fed52de26f9e7c7ca830a539c199b9cbe3bdf` |
| 예상 SHA 일치 | 예 |
| fetch | 네트워크 연결 실패로 원격 갱신 불가. 시작 시점 로컬 HEAD와 로컬 `origin/full`은 예상 SHA와 일치했다. |

V15의 exact-two 비교, 사업안 미리보기, 단계형 Decision Flow, 법령 단위 근거 그룹, 시장 분석 기준값 용어를 보호 계약으로 유지했다.

## RESELECTION ROOT CAUSE

재선택 회귀에는 두 가지 프런트엔드 원인이 함께 있었다.

1. 전환 감지가 `selectionId`만 비교했다. 백엔드가 기존 selection record를 갱신해 같은 `selectionId`와 새 `conceptId`를 반환하면 새 사용자 선택으로 인식하지 못했다.
2. 갤러리 접기와 다음 단계 포커스가 `basisRef.current` 존재 여부에 함께 묶여 있었다. 선택 변경을 위해 갤러리가 열린 동안에는 기준값 섹션이 렌더되지 않아 ref가 없으므로, 접기 자체가 실행되지 않을 수 있었다.

선택 API 성공을 낙관적 완료로 사용하지 않고, 갱신된 `portfolio.selection`을 최종 권한으로 유지했다.

## SELECTION IDENTITY BEFORE/AFTER

| 구분 | 이전 | V16 |
| --- | --- | --- |
| 선택 식별자 | `selectionId` | `${selectionId}:${conceptId}` |
| 동일 selection record의 사업안 변경 | 전환 누락 | 새로운 선택으로 감지 |
| 갤러리 접기 | basis ref 존재 조건과 결합 | 권한 있는 선택 변경 감지 즉시 별도 수행 |
| 포커스 이동 | 접기와 같은 effect | 갤러리 접힘 후 대상 ref가 마운트되면 수행 |
| API 실패 | 전환 위험 | 서버 selection이 바뀌지 않으므로 갤러리와 기존 선택 유지 |
| 처리 중 표시 | 전체 busy 중심 | 클릭한 사업안만 `선택 중...` 표시 |
| 모션 감소 | 부분 대응 | 포커스 이동 시 smooth scroll 제거 |

성공 흐름은 `선택 중 → 갱신된 selection 확인 → 갤러리 접기 → BROWSE 복귀 → 현재 Decision section 포커스` 순서다.

## VISUAL SYSTEM BEFORE/AFTER

| 항목 | 이전 | V16 |
| --- | --- | --- |
| 표면 | 흰 배경·1px 회색 테두리 반복 | 페이지/업무 섹션/강조 표면의 3단계 tonal hierarchy |
| 컨트롤 | 유사한 직사각 outline | Primary/Secondary/Tertiary/Icon 역할 분리 |
| 입력 | 긴 흰 input이 항상 노출 | 현재 값 우선 read mode, 수정할 필드만 editor 노출 |
| 사업안 카드 | 넓은 auto-fit과 반복 구획 | 최대 3열, 카드 폭 제한, 차별 정보 3개 중심 |
| 선택 상태 | 강한 테두리 중심 | tonal surface, check, `현재 선택` 상태 |
| 비교 | 격자형 border 강조 | 행 divider, tonal A/B header, 6줄 clamp |
| 법률 결과 | 카드 안 카드 반복 | 법률 그룹과 조항 chip/accordion 중심의 읽기 구조 |

새 override 전용 stylesheet를 추가하지 않았고 `business-proposal.css`의 기존 selector를 직접 정리했다.

## BUTTON MATRIX

| 역할 | 용도 | 표현 |
| --- | --- | --- |
| Primary | 사업안 선택, 기준값 확인, 법률 확인, 시장 분석 시작 | 브랜드 solid tonal, 42px 높이, 굵은 label |
| Secondary | 두 사업안 비교, 선택 변경 | 낮은 채도의 브랜드 tonal surface |
| Tertiary | 세부 보기, 수정, 편집 닫기, 기준값 다시 보기 | 기본 border 없는 text/icon action |
| Icon | 뒤로 가기, 펼치기/접기 | `AppIcon` 기반, 접근 가능한 이름 유지 |

버튼 press 상태와 pending spinner를 추가했고 동일한 사업안을 다시 선택하는 호출은 비활성 상태로 방지한다.

## INPUT MATRIX

| 입력 유형 | V16 계약 |
| --- | --- |
| 공통 input/textarea/select | `width:100%`, `min-width:0`, `max-width:100%`, 44px 기준 높이 |
| 기본 상태 | 옅은 tonal background와 미세 border |
| focus | 흰 surface와 brand ring/border |
| 긴 textarea | 읽기 폭 최대 72ch |
| 단위 입력 | 값과 `%`·`년`을 하나의 suffix group으로 표시 |
| 통화 | 자유 입력 대신 기존 값을 보존하는 select 구조 |
| 기준값 | 기본 read mode에서 현재 값·상태·수정 action 제공 |
| 구조화 편집 | 선택한 행만 editor를 열고 서버 입력 의미는 유지 |

## CONTENT WIDTH MATRIX

| 영역 | 최대 폭 |
| --- | --- |
| 전체 사업안 shell | 92rem 유지 |
| 사업안 gallery | 88rem |
| 정확히 두 사업안 비교 | 86rem |
| 사업 기준값 | 80rem |
| 법률 읽기 workspace | 80rem |
| 선택 요약·단계·시장 준비 | 82rem |

넓은 프로젝트 shell은 유지하면서 정보 유형별 읽기 폭만 중앙에서 제한했다.

## GALLERY DENSITY MATRIX

| viewport | 구성 |
| --- | --- |
| Desktop | 최대 3열, 카드 최대 약 27.5rem, 중앙 정렬 |
| Tablet | 2열 |
| Mobile | 1열 |

카드 미리보기의 차별화 highlight 상한을 4개에서 3개로 줄였다. 사업안 이름, 한 줄 정의, 핵심 차별 정보, 비교/선택 action의 우선순위를 유지한다.

## COMPARISON READABILITY MATRIX

| 항목 | 계약 |
| --- | --- |
| 비교 대상 | 정확히 2개 유지 |
| desktop grid | 9~10rem label + A/B `minmax(0, 1fr)` |
| 가로 전략 | `max-content`, primary horizontal scroll 사용 금지 |
| 셀 구분 | 진한 격자 대신 행 divider |
| header | A/B tonal surface |
| 긴 값 | 최대 6줄 clamp |
| mobile | 각 기준 아래 A/B vertical pair, 가로 overflow 없음 |
| 상세 | 기존 그룹별 disclosure 및 접근성 속성 유지 |

## BASIS LAYOUT MATRIX

| 상태 | 표시 |
| --- | --- |
| 처음 진입 | label, 현재 기준값, 상태, `수정` action |
| 수정 | 선택한 필드만 tonal editor 노출 |
| 사업 기본 조건 | 약 11.5rem label + bounded value/action |
| 시장 목표 | 하나의 surface 안에서 점유율과 초기 시장 규모를 divider로 구분 |
| 확인 완료 | 기존 V15 compact summary로 접힘 |

7개 필드를 동시에 브라우저 기본 input처럼 노출하지 않으면서도 기존 Hypothesis 저장·추천·확정 계약은 바꾸지 않았다.

## RESPONSIVE CONTRACT

| 구간 | 계약 |
| --- | --- |
| `>= 1200px` | bounded basis/legal, 최대 3열 gallery, 두 사업안 비교 3-column |
| Tablet | gallery 2열, 긴 입력 구성은 필요한 곳에서 1열 전환 |
| Mobile `390px` | gallery 1열, 비교 vertical pair, 기준값 label/value/editor stack |
| 공통 | grid child `min-width:0`, input 최대 폭 100%, 불필요한 full-width button 금지 |

CSS source contract로 `max-content`와 unbounded `auto-fit` 부재, bounded width, mobile 전환, input overflow 방지를 고정했다.

## TEST MATRIX

| 검증 | 결과 |
| --- | --- |
| 동일 `selectionId` + 다른 `conceptId` 재선택 | PASS |
| 새 `selectionId` + 다른 사업안 선택 | PASS |
| 선택 API 실패 시 갤러리·기존 A 유지 | PASS |
| 선택 중인 B만 pending 표시 | PASS |
| exact-two 비교 계약 | PASS |
| proposal preview 3개 차별 정보 | PASS |
| 기준값 read/edit mode | PASS |
| 법률 grouping·evidence 계약 회귀 | PASS |
| CSS width/overflow/responsive source contract | PASS |
| 대상 Vitest | 3 files, 38 tests PASS |
| 변경 JS/JSX ESLint | PASS |
| Vite production build | PASS, 273 modules |
| `git diff --check` | PASS |

프로젝트 지침의 fast profile에 따라 관련 기능 테스트만 실행했고 전체 회귀 suite는 실행하지 않았다. 빌드에는 기존 500kB 초과 chunk 경고가 남지만 빌드는 정상 완료됐다.

## USER VISUAL ITEMS

시각 수용성은 사용자 검토 대기 상태다. 실제 인증 데이터로 다음을 확인한다.

- 1440×900과 1920×1080에서 gallery 카드가 과도하게 넓지 않은지
- 사업안 변경 후 다른 사업안을 선택하면 갤러리가 접히고 현재 Decision section으로 자연스럽게 이동하는지
- 버튼 네 계층과 tonal input이 Windows/MFC형 기본 컨트롤처럼 보이지 않는지
- 기준값 read mode에서 필요한 값을 빠르게 찾고 수정할 수 있는지
- 비교 화면의 A/B 시선 이동과 긴 값 clamp가 자연스러운지
- 법률 그룹에서 경고색이 실제 주의 항목에만 사용되는지
- 390×844에서 가로 overflow, 단위 input 겹침, 불필요한 버튼 full-width가 없는지

## 남은 간극과 이어서 확인할 지점

- 기능 계약과 자동 검증은 완료됐다.
- 인증 브라우저 screenshot은 V16 완료 조건에서 제외했으며 사용자 시각 검토가 남았다.
- 실제 사용자 콘텐츠가 자동 테스트 fixture보다 훨씬 길 때 카드 clamp와 법률 조항 disclosure의 체감 밀도는 사용자 검토 후 조정할 수 있다.
- Vite의 기존 대형 chunk 경고는 이번 UX 범위 밖이며 별도 성능 작업 대상으로 남긴다.
