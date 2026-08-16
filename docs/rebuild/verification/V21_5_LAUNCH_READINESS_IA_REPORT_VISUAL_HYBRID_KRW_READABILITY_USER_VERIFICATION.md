# V21.5 Launch Readiness IA·보고서·KRW 사용자 검증

## 준비

1. 로그인한 프로젝트에서 출시 준비 화면을 연다.
2. Technology, Operations, Finance의 실제 완료 결과가 있으면 세 보고서까지 준비한다.
3. Desktop 1280px 이상, 1024px, 768px, Mobile 390px에서 확인한다.

## 1. 메인 화면 정보 구조

Desktop 1280px 이상 기대:

- 기술·운영·재무 카드가 같은 행의 3열로 보인다.
- 각 카드는 `독립 사용 가능` 표시가 있다.
- 세 카드 높이를 억지로 맞춘 큰 빈 공간이 없다.
- 기술·운영·재무·보고서 sticky navigation이 없다.
- 보고서 확인 영역은 세 분석 카드 아래의 full-width section이다.

1024px에서는 2열, 760px 이하에서는 1열인지 확인한다.

## 2. 독립 문서 분석 설명

다음 의미가 3초 안에 이해되는지 확인한다.

- 기술·운영·재무 분석은 서로 독립적으로 사용할 수 있다.
- 기술·운영은 사용자가 제출한 전문 문서가 1차 기준이다.
- 공개 참고자료는 기술·운영의 보조 근거일 수 있다.
- 재무는 시장 분석이나 사업 모델 결과 없이 사용할 수 있다.
- 재무 계산은 업로드한 재무 값만 기준으로 한다.

앞 단계 결과가 있어야 출시 준비를 사용할 수 있다는 인상을 주면 문제로 기록한다.

## 3. 카드 workflow

Technology와 Operations:

1. 템플릿 받기
2. 실제 계획 작성·업로드
3. 독립 분석 결과 확인

Finance:

1. 재무 템플릿 받기
2. 재무 값과 산정 근거 작성·업로드
3. 손익·현금흐름 독립 분석

세 단계가 가로로 눌리지 않고 위에서 아래로 자연스럽게 읽혀야 한다.

## 4. 카드 내부 가독성

- template, 분석, 보고서 action이 서로 겹치지 않는다.
- 긴 filename이 카드 밖으로 나오지 않는다.
- 긴 AI summary와 실행 과제가 wrap된다.
- 완료 결과는 카드 안에서 단일 열로 읽힌다.
- 390px에서 horizontal overflow가 없다.

## 5. Professional 보고서

Technology와 Operations 보고서에서 확인한다.

- 표지·프로젝트명·기준일·입력 문서가 유지된다.
- summary strip은 `AI 출시 준비도 평가`, `판정`, `독립 검증` 3개다.
- AI 점수는 정해진 산식이 아니라는 설명이 유지된다.
- 독립 검증을 통과한 결과에서만 통과 문구가 나온다.
- 다음 section 내용이 모두 보인다.
  - 경영진 요약
  - 평가에 사용한 입력 근거
  - 영역별 준비도와 판단 근거
  - 핵심 위험
  - 출시 전 확인 기준
  - 우선 실행 과제
  - 사업 적용 결론
  - 외부 참고 출처

입력 근거, 영역별 평가, 위험, Gate, 실행 과제는 navy header와 얇은 grid의 table이어야 한다.

## 6. 보고서 데이터 손실

현재 분석 JSON 또는 이전 화면과 비교해 다음 항목 수가 동일한지 확인한다.

- dimensions
- risks
- gates
- actions
- external sources

카드에서 표로 바뀌었을 뿐 문장과 항목이 빠지면 안 된다.

## 7. Finance KRW 가독성

Launch Page의 매출·영업이익·운전자금은 다음처럼 보인다.

`123,000,000 KRW · 1억 2,300만 원`

Finance Report의 금액은 raw와 readable이 2줄로 보인다.

- 50,000,000 KRW / 5천만 원
- 120,000,000 KRW / 1억 2천만 원
- 325,000,000 KRW / 3억 2,500만 원
- 음수는 두 표현 모두 음수 sign을 유지

계산값 자체가 바뀌지 않았는지 current result와 대조한다.

## 8. Monte Carlo

P10, P50, P90이 하나의 긴 `A / B / C` 문장이 아니라 각각 구분된 metric인지 확인한다. 각 금액에 raw KRW와 한국식 읽기 표현이 모두 있어야 한다.

## 9. 통합 보고서 순서

다음 순서로 선택해 본다.

1. 재무
2. 기술
3. 운영

통합 보고서 기대 순서:

1. 기술 분석 보고서
2. 운영 분석 보고서
3. 재무 분석 보고서

통합 표지의 `포함 보고서`와 마지막 외부 참고 출처도 이 순서를 따라야 한다. 동일 URL은 한 번만 표시된다.

## 10. Screen = Print

보고서 화면에서 `PDF로 저장`을 누른다.

기대:

- Backend PDF GET이 발생하지 않는다.
- PDF.js를 사용하지 않는다.
- 같은 React Report Document에서 `window.print()`가 실행된다.
- Print Preview에는 app topbar, project navigation, action bar, skip-link, scroll button이 없다.
- 화면과 print의 section 순서와 표 내용이 같다.

## 11. Print table 안정성

Chrome/Edge Print Preview와 저장된 PDF에서 확인한다.

- navy table header가 명확하다.
- 긴 표의 다음 페이지에서도 header가 반복된다.
- row가 페이지 경계에서 부자연스럽게 잘리지 않는다.
- 긴 한국어가 cell과 겹치지 않는다.
- source URL이 문서 밖으로 나가지 않는다.
- section heading만 페이지 하단에 홀로 남지 않는다.
- Finance raw/readable 금액이 겹치지 않는다.
- 외부 출처 링크가 클릭된다.

## 문제 보고 시 함께 제공할 정보

- project ID와 report 종류
- viewport 너비
- 문제 section과 row 이름
- 화면 screenshot 또는 저장 PDF 문제 페이지
- Print dialog 용지·배율·여백 설정
- 실제 current 결과의 해당 값
- 통합 보고서 선택 순서와 실제 표시 순서
