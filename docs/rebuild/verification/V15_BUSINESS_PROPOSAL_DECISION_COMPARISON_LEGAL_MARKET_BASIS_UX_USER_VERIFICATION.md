# V15 사용자 검증 안내

## 검증 전 준비

1. 실제 인증 계정으로 로그인한다.
2. 사업안이 3개 이상 생성된 프로젝트를 연다.
3. 브라우저 DevTools console을 사용할 수 있게 준비한다.
4. 1440×900, 1920×1080, 390×844 순서로 확인한다.

현재 자동화 환경에는 인증 세션이 없어 아래 검증은 사용자 확인이 필요하다.

## 1. 사업안 gallery

- 제목이 `생성된 사업안을 살펴보세요`인지 확인한다.
- `사업안 목록`, `비교` 탭이 없어야 한다.
- 각 카드에서 한 줄 정의 외에 주요 고객, 수익 방식, 제공 방식 등 실제 차이가 보여야 한다.
- 여러 카드의 summary가 같아도 highlight가 모두 똑같이 반복되지 않아야 한다.
- 카드에 상세 법률 문단이 없어야 하며 `법률·규제 사전 검토 완료` 한 줄만 허용한다.

## 2. 정확히 두 개 비교

1. 첫 카드의 `비교에 추가`를 선택한다.
2. `한 개 더 선택하세요` 안내를 확인한다.
3. 둘째 카드를 선택한다.
4. 셋째 카드의 비교 control이 비활성인지 확인한다.
5. `두 사업안 비교`를 누른다.

기대 결과:

- 같은 route에서 Focus View가 열린다.
- 비교 대상은 정확히 두 개다.
- 기본 항목은 주요 고객, 해결 문제, 핵심 가치, 제공 방식, 수익 방식, 운영상의 차이 중심이다.
- 법률 상세가 비교 matrix에 없어야 한다.
- `세부 내용 보기` 아래 그룹을 개별로 펼칠 수 있다.
- `사업안으로 돌아가기`로 gallery가 복원된다.

## 3. 비교 overflow 측정

비교 화면에서 console에 다음을 실행한다.

```js
const matrix = document.querySelector('.proposal-comparison__matrix');
({
  viewport: [innerWidth, innerHeight],
  matrix: matrix?.getBoundingClientRect().toJSON(),
  matrixOverflow: matrix ? matrix.scrollWidth - matrix.clientWidth : null,
  documentOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
});
```

기대:

- 1440/1920에서 `matrixOverflow === 0`
- 모든 viewport에서 `documentOverflow === 0`
- 390에서는 A/B가 기준별 vertical pair로 표시되고 가로 스크롤이 없어야 한다.

## 4. 사업안 선택과 단계 collapse

1. 사업안 하나를 선택한다.
2. gallery가 사라지고 `사업안 선택 완료` compact summary가 보이는지 확인한다.
3. `시장 분석에 사용할 기준값`이 현재 full section인지 확인한다.
4. `선택한 사업안 보기` disclosure와 `선택 변경`을 확인한다.
5. 선택 직후 기준값 section으로 자연스럽게 이동하는지 확인한다.
6. OS reduced motion 사용 시 smooth scroll이 없어야 한다.

## 5. 시장 분석 기준값

다음 사용자 label을 확인한다.

- 사업 대상 지역
- 수익을 만드는 방식
- 가격·과금 방식
- 고객에게 제공하는 경로
- 핵심 차별점
- 목표 시장 점유율
- 초기 목표 시장 규모

금지 문구:

- 7개 검증 가정
- 시장 점유 가정
- 초기 확보 시장 규모
- 제안값

기본 5개는 label-left/input-right productivity row이고, 시장 목표 2개는 별도 panel이어야 한다. primary action은 `기준값 확인 완료`다.

## 6. structured input overflow

390×844에서 다음을 실행한다.

```js
const controls = [...document.querySelectorAll('.hypothesis-structured input, .hypothesis-structured textarea')];
controls.map((control) => {
  const rect = control.getBoundingClientRect();
  const parent = control.parentElement.getBoundingClientRect();
  return {
    label: control.getAttribute('aria-label') || control.closest('label')?.innerText,
    leftOverflow: parent.left - rect.left,
    rightOverflow: rect.right - parent.right,
    width: rect.width,
  };
});
```

기대:

- 모든 `leftOverflow <= 1`
- 모든 `rightOverflow <= 1`
- 점유율/기간/통화/근거 input이 서로 겹치지 않는다.
- 문서 horizontal overflow가 0이다.

## 7. 단계 전환

| 실제 상태 | 기대 화면 |
|---|---|
| 선택 직후 | 선택 summary compact + 기준값 full |
| 기준값 확인 후 | 선택/기준값 compact + 법률 full |
| 법률 보고서 준비 | `법률·규제 결과 확인 완료` |
| 보고서 확정 후 | `시장 분석 준비 완료하기` |
| `READY_FOR_MARKET` | 모든 이전 단계 compact + `시장 분석 시작하기` |

gallery, 기준값 form, 법률 report가 동시에 모두 길게 펼쳐지면 실패다.

## 8. 법률·규제 grouping

법률 결과에서 다음을 확인한다.

- 같은 법률명은 최상위 surface 하나다.
- 제15조, 제17조 같은 조항은 같은 surface 내부 chip/disclosure다.
- 동일 제17조 evidence가 중복 표시되지 않는다.
- article disclosure에 요약, 시행일, 원문 링크가 유지된다.
- 기술 정보는 기본 접힘이다.

DevTools에서 예시 확인:

```js
[...document.querySelectorAll('.legal-evidence__law')].map((law) => ({
  law: law.querySelector('header strong')?.textContent.trim(),
  articles: [...law.querySelectorAll('header span')].map((item) => item.textContent.trim()),
  rect: law.getBoundingClientRect().toJSON(),
}));
```

## 9. 접근성

- 비교 checkbox를 Space로 전환할 수 있어야 한다.
- Focus View back button에 `사업안으로 돌아가기` 이름이 있어야 한다.
- 세부 비교와 법률 article button의 `aria-expanded`가 열림/닫힘에 따라 바뀌어야 한다.
- disclosure button의 `aria-controls` 대상 id가 실제 존재해야 한다.
- 현재 단계에 `aria-current="step"`이 있어야 한다.
- 키보드 focus outline이 보인다.

## 10. 검증 결과 기록

아래 표를 채워 결과 문서의 LIVE VISUAL MATRIX를 갱신한다.

| 화면 | 1440×900 | 1920×1080 | 390×844 | 비고 |
|---|---|---|---|---|
| 사업안 3개 gallery |  |  |  |  |
| exact-two 비교 |  |  |  |  |
| 선택 후 collapse |  |  |  |  |
| 시장 기준값 |  |  |  |  |
| 법령 grouping |  |  |  |  |

모든 항목이 통과하고 실제 캡처/rect 증거가 남기 전에는 V15를 COMPLETE로 변경하지 않는다.
