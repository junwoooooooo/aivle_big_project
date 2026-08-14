# V12 Workspace Composition · Work Center · Historical Job 사용자 검증

## 목적

V12 자동 검증 이후 남은 실제 인증 화면 검증 절차다. 정상 사용자 계정과 해당 사용자가 소유한 프로젝트를 사용한다. 비밀번호, JWT, cookie, API key, Authorization header는 기록하거나 문서에 붙여 넣지 않는다.

## 사전 조건

1. branch가 `full`인지 확인한다.
2. 네트워크가 가능하면 `git fetch origin full` 후 HEAD와 fetched `origin/full`을 비교한다.
3. frontend, backend와 필요한 인프라를 정상 실행한다.
4. 브라우저에서 정상 사용자로 로그인한다.
5. active, NEEDS_INPUT, COMPLETED 또는 FAILED 작업을 포함한 소유 프로젝트를 연다.

## 1. Project Overview 전체 노드

프로젝트 개요에서 여섯 Journey node를 확인한다.

- card 본문을 클릭하면 해당 화면으로 이동한다.
- 우측 상단 arrow를 클릭해도 동일하게 이동한다.
- Tab 한 번에 node 전체가 하나의 focus target이 된다.
- Enter로 이동한다.
- arrow가 별도 tab stop이 아니다.
- hover/focus-visible에서 미세한 배경, border, shadow가 보인다.
- station이 card border와 붙거나 겹쳐 답답해 보이지 않는다.
- 곡선이 각 station 중심을 통과한다.

상태별 접근성 이름 예시:

- 사업 기획 시작하기
- 사업 검증 계속하기
- 최종 보고서 결과 보기

## 2. Quick Work Center 실측

1440×900과 1920×1080에서 각각 Quick Work Center를 연다. 브라우저 개발자 도구 Console에서 아래 코드를 실행한다.

```js
const selectors = [
  '.project-work-popover',
  '.job-center--compact',
  '.job-center--compact > header',
  '.job-center__summary',
  '.job-center__groups',
  '.job-center__group',
  '.job-center__detail-button',
];

const rows = selectors.map((selector) => {
  const element = document.querySelector(selector);
  if (!element) return { selector, missing: true };
  const rect = element.getBoundingClientRect();
  const style = getComputedStyle(element);
  return {
    selector,
    x: rect.x,
    y: rect.y,
    width: rect.width,
    height: rect.height,
    display: style.display,
    maxWidth: style.maxWidth,
    minWidth: style.minWidth,
    justifySelf: style.justifySelf,
    alignSelf: style.alignSelf,
    flex: style.flex,
    gridTemplateColumns: style.gridTemplateColumns,
  };
});
console.table(rows);
```

popover content width는 `popover rect width - 좌우 padding - 좌우 border`로 계산한다.

통과 조건:

- `jobCenterWidth / popoverContentWidth >= 0.96`
- `summaryWidth / jobCenterWidth >= 0.98`
- `detailButtonWidth / jobCenterWidth >= 0.98`
- header, metric 3개, compact rows, 전체 작업 보기의 좌우선이 동일하다.
- 오른쪽에 의미 없는 큰 빈 공간이 없다.
- 두 viewport에서 Quick open screenshot을 저장한다.

## 3. Full Work Center 높이

Full Work Center를 열고 다음 순서로 전환한다.

1. 전체
2. 진행 중 — 가능하면 결과 0건
3. 입력 필요 — 가능하면 결과 0건
4. 완료·종료
5. 전체

각 단계에서 Console로 아래 값을 수집한다.

```js
const rect = document.querySelector('.work-center-sheet')?.getBoundingClientRect();
console.log(rect && { x: rect.x, y: rect.y, width: rect.width, height: rect.height });
```

통과 조건:

- 모든 filter의 outer height 차이가 1 CSS px 이하이다.
- sheet가 Bottom Sheet이며 Right Drawer로 보이지 않는다.
- content만 내부 scroll된다.
- Quick과 Full이 동시에 보이지 않는다.
- 빈 상태 문구가 filter와 일치한다.

## 4. 종료 작업 저장 이벤트

완료 또는 실패 작업을 선택한다.

- 저장 event 5개인 완료 작업은 상세에 5개가 표시된다.
- FAILED 작업은 실패에 이르는 처리 과정이 표시된다.
- 종료 작업을 선택했다고 SSE reconnect가 무한 반복되지 않는다.
- 실제 저장 event가 0건일 때만 “이 작업에는 저장된 처리 기록이 없습니다.”가 표시된다.
- 이전 종료 작업의 느린 응답이 새로 선택한 작업의 상세를 덮지 않는다.
- active 작업은 기존 live 진행 event를 계속 표시한다.

필요하면 Network에서 `GET /api/v2/jobs/{jobId}/events?after=0`과 후속 `after={nextSequence}` 요청이 성공하는지만 확인한다. header, token, cookie 값은 캡처하지 않는다.

## 5. Idea Split Workspace

### Desktop 1440×900 / 1920×1080

- 왼쪽에 필수 입력 3개와 참고 자료 Dropzone이 보인다.
- 오른쪽에 선택 입력 10개 이름이 접힌 상태에서도 모두 보인다.
- “선택 정보 N / 10 입력”이 보인다.
- 한 항목을 열면 입력 panel이 표시된다.
- 값을 입력한 뒤 다른 항목을 열고 돌아와도 값이 유지된다.
- 채운 항목은 한 줄 summary로 보이고 긴 값은 ellipsis 처리된다.
- 실행 버튼이 본문을 가리는 floating overlay가 아니다.

### Tablet 1024 / Mobile 390

- 2열을 강제하지 않고 한 열로 쌓인다.
- 순서는 필수 입력·참고 자료 다음 선택 입력이다.
- accordion과 입력기가 화면 전체 폭을 사용한다.
- `document.documentElement.scrollWidth === document.documentElement.clientWidth`이다.

## 6. TechOps / Finance와 결과 화면

- TechOps는 핵심 사실 입력과 운영 가설·근거가 Desktop에서 분리되고 좁은 화면에서는 순서대로 쌓인다.
- Finance는 핵심 비용·목표·수익 가정과 세부 CAC·조건·도움말이 분리된다.
- Twin, Marketing Preview, Market 결과, Business Model 결과, Final Report가 입력용 62/38 layout으로 강제되지 않았는지 확인한다.

## 7. V11 회귀 확인

- Full Sheet를 닫은 뒤 body scroll이 복원된다.
- modal open/close 반복 후 scroll lock class가 남지 않는다.
- focus trap이 dialog 안에서 순환한다.
- history “이전 작업 더 보기”가 유지된다.
- Final Report와 project presentation state가 정상이다.
- Dropzone과 수평 `ProjectFormRow`가 유지된다.

## 기록 양식

| 항목 | viewport | 측정값/스크린샷 | PASS/FAIL | 메모 |
|---|---|---|---|---|
| Quick 폭 비율 | 1440×900 |  |  |  |
| Quick 폭 비율 | 1920×1080 |  |  |  |
| Sheet filter height | 1440×900 |  |  |  |
| Journey station/path | 1440×900 |  |  |  |
| Idea split | 1440×900 |  |  |  |
| Idea split | 1920×1080 |  |  |  |
| Idea stack/overflow | 1024 |  |  |  |
| Idea stack/overflow | 390 |  |  |  |
| terminal events | 해당 없음 |  |  |  |

Quick 실측, Sheet 높이, Journey 정렬, Idea 반응형 중 하나라도 확인하지 못하면 V12를 COMPLETE로 판정하지 않는다.
