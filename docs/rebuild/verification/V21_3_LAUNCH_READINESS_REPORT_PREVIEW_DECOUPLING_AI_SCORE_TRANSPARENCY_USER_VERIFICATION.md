# V21.3 사용자 검증 안내

자동 기능 검증은 완료되었습니다. 아래는 실제 인증 프로젝트와 IDM 환경에서 확인할 항목입니다.

## 1. Technology 미리보기

1. 완료된 Technology 분석에서 `보고서 미리보기`를 누릅니다.
2. 브라우저 Network 탭을 확인합니다.

기대 결과:

- Dialog가 즉시 열립니다.
- `/launch-readiness/technology/report` 요청은 0회입니다.
- IDM 팝업이나 파일 저장이 발생하지 않습니다.
- 종합 평가, 입력 기준, 영역별 평가, 위험, Gate, 실행 과제, 외부 참고자료가 표시됩니다.

## 2. Operations 미리보기

완료된 Operations 분석에서 같은 절차를 수행합니다.

기대 결과:

- `/launch-readiness/operations/report` 요청은 0회입니다.
- 운영 입력과 운영 분석 결과가 표시됩니다.
- Technology 내용이 잘못 섞이지 않습니다.

## 3. Finance 미리보기

1. 완료된 Finance 분석에서 `보고서 미리보기`를 누릅니다.
2. Network 탭과 화면 내용을 확인합니다.

기대 결과:

- `/finance/analysis/report` 요청은 0회입니다.
- 핵심 결과, 3개년 추정, 월별 주요 지표, 스트레스 시나리오, Monte Carlo, AI 해석과 권장 조치가 표시됩니다.
- 미리보기 클릭만으로 IDM이 실행되지 않습니다.

## 4. 통합 미리보기

1. 보고서 영역에서 Technology, Operations, Finance를 모두 선택합니다.
2. `3개 통합 보고서 미리보기`를 누릅니다.

기대 결과:

- `/reports/download` 요청은 0회입니다.
- Technology → Operations → Finance 순서로 세 문서가 표시됩니다.
- 각 문서의 heading과 내용이 구분됩니다.

## 5. 명시적 PDF 다운로드

각 미리보기 Dialog에서 `PDF 다운로드`를 누릅니다.

기대 결과:

- 이 버튼을 누른 경우에만 해당 PDF endpoint가 정확히 1회 호출됩니다.
- IDM이 요청을 받는다면 사용자가 명시적으로 다운로드한 이 시점에만 동작합니다.
- 저장 파일이 PDF reader에서 정상적으로 열립니다.

## 6. AI 점수 설명

Technology와 Operations 결과를 확인합니다.

기대 결과:

- `AI 출시 준비도 평가 82점`처럼 AI 평가임을 명시합니다.
- `작성한 기술·운영 계획을 바탕으로 AI가 평가한 준비도입니다. 정해진 재무 산식처럼 계산된 점수는 아닙니다.` 안내가 표시됩니다.
- 품질 검증이 통과한 결과에만 `독립 AI 검증 통과`가 표시됩니다.
- reviewer 내부 점수는 일반 화면에 별도 숫자로 표시되지 않습니다.

## 7. Stale와 보안 표시

가능하면 새 입력으로 인해 이전 기준이 된 결과를 확인합니다.

기대 결과:

- `이전 입력 기준 결과입니다.`가 표시됩니다.
- TaskRun ID, snapshot/result ID, hash, storage key, AI raw prompt나 provider 내부 정보가 표시되지 않습니다.

## 8. 모바일

390px 폭에서 각 미리보기를 확인합니다.

기대 결과:

- Dialog가 화면 폭을 넘지 않습니다.
- 표는 문서 내부에서만 안전하게 스크롤됩니다.
- 본문과 다운로드 버튼이 겹치지 않습니다.
- 가로 페이지 overflow가 없습니다.

