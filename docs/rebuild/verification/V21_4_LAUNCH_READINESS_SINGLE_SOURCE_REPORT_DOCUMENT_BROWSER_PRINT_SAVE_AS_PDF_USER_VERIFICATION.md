# V21.4 Launch Readiness 단일 보고서 사용자 검증

## 준비

1. 로그인한 상태에서 Technology, Operations, Finance 중 검증할 분석을 완료한다.
2. 최신 입력 기준 결과인지 확인한다. stale 결과는 기본 report route에서 열리지 않아야 한다.
3. 브라우저 개발자 도구의 Network를 열고 요청 목록을 지운다.

## 1. 개별 보고서 route

출시 준비 화면에서 Technology의 `보고서 보기`를 누른다.

기대:

- Dialog가 아니라 `/launch-readiness/reports/technology` route로 이동한다.
- 화면이 문서 상단에서 시작한다.
- 상단에 `출시 준비로 돌아가기`, `PDF로 저장`이 있다.
- `application/pdf` 요청이 0회다.
- 보고서 본문에서 다음을 확인할 수 있다.
  - 표지·분석 기준일·입력 문서
  - AI 출시 준비도 평가·판정·독립 검증
  - 경영진 요약
  - 입력 근거
  - 영역별 판단
  - 위험
  - 출시 기준
  - 실행 과제
  - 사업 적용 결론
  - 외부 참고 출처
  - 문서 한계

Operations도 같은 순서로 확인한다.

## 2. AI 점수 투명성

기대:

- `AI 출시 준비도 평가 N점`으로 표시된다.
- 고정 산식 점수가 아니라는 설명이 있다.
- 각 Dimension은 `AI 평가 N점`으로 표시된다.
- 독립 검증을 통과한 결과에서만 `독립 AI 검증 통과`가 보인다.
- reviewer score, TaskRun ID, snapshot hash, provider 내부 정보는 보이지 않는다.

## 3. Finance 보고서

Finance `보고서 보기`를 누른다.

기대:

- `/launch-readiness/reports/finance` route다.
- Finance PDF endpoint 요청은 0회다.
- 핵심 결과, 3개년 추정 손익, 월별 SVG 추이, 월별 전체 표, 스트레스 시나리오, Monte Carlo, AI 해석, 사업 적용 결론이 있다.
- SVG의 매출·영업이익·누적 현금흐름 선과 범례가 구분된다.
- 화면의 숫자는 current Finance 결과와 일치한다.

## 4. 통합 보고서

보고서 선택 영역에서 Technology와 Finance처럼 두 개 이상을 선택하고 `통합 보고서 보기`를 누른다.

기대:

- integrated route와 `modules` query가 생성된다.
- 통합 PDF endpoint 요청은 0회다.
- 통합 표지 다음에 선택한 개별 문서가 같은 화면 문서 구조로 나온다.
- 외부 참고 출처가 개별 Professional 문서마다 반복되지 않고 마지막 통합 출처에 URL 기준 한 번씩 나온다.

## 5. PDF로 저장

각 개별 보고서와 통합 보고서에서 `PDF로 저장`을 누른다.

기대:

- 브라우저 Print dialog가 열린다.
- Backend PDF GET과 파일 download navigation이 발생하지 않는다.
- IDM popup이 발생하지 않는다.
- Print Preview에는 보고서 문서만 보인다.
- 다음은 출력되지 않는다.
  - app topbar
  - 프로젝트 header/navigation
  - report action bar
  - `본문으로 바로가기`
  - scroll-to-top button
  - 기타 버튼

## 6. 파일명

브라우저 Save as PDF dialog의 제안 파일명을 확인한다.

- `{프로젝트명}_기술_출시준비_보고서_{YYYYMMDD_HHmm}`
- `{프로젝트명}_운영_출시준비_보고서_{YYYYMMDD_HHmm}`
- `{프로젝트명}_재무_출시준비_보고서_{YYYYMMDD_HHmm}`
- `{프로젝트명}_출시준비_통합보고서_{YYYYMMDD_HHmm}`

프로젝트명의 Windows 금지 문자는 제거되어야 한다. 실제 파일명은 브라우저 dialog에서 사용자가 최종 확정한다.

## 7. 저장된 PDF 확인

Save as PDF로 저장한 파일을 Chrome/Edge PDF viewer에서 다시 연다.

기대:

- 한글이 깨지지 않는다.
- 표지와 section heading이 자연스럽게 분리된다.
- 표 header·row·risk·gate·action·chart가 부자연스럽게 잘리지 않는다.
- Finance SVG가 선명하다.
- 긴 표 값이 겹치거나 잘리지 않는다.
- 외부 참고 출처 링크를 클릭할 수 있다.

## 문제 보고 시 함께 제공할 정보

- report 종류와 URL
- viewport
- Print dialog의 용지·배율·여백 설정
- 문제가 발생한 PDF 페이지 screenshot
- Network에서 발생한 `application/pdf` 요청 유무
- 브라우저와 IDM 사용 여부
