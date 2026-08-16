# V21.6 Launch Readiness 최종 UX 사용자 검증

## 준비

1. Finance DOCX를 업로드해 분석을 완료한 프로젝트를 준비한다.
2. 가능하면 기술·운영·재무 보고서가 모두 current 상태인 프로젝트를 사용한다.
3. Desktop 1280px 이상, Tablet 768~1024px, Mobile 390px에서 확인한다.

## 1. Finance 문서명 persistence

1. `finance-plan-v3.docx`를 업로드한다.
2. 출시 준비 화면을 벗어난다.
3. route를 다시 열거나 브라우저를 새로고침한다.

기대:

- 재무 카드에 `finance-plan-v3.docx`가 다시 보인다.
- 파일을 방금 선택한 브라우저 local state에 의존하지 않는다.
- 역사 데이터에 artifact가 없더라도 화면 전체가 실패하지 않는다.

## 2. Finance 보고서 입력 문서

Finance `보고서 보기`를 연다.

기대:

- 표지의 label은 `입력 문서`다.
- 값은 실제 업로드한 `finance-plan-v3.docx`다.
- Finance가 포함된 통합 보고서에서도 같은 이름이 보인다.
- filename이 없는 역사 결과만 `사용자 재무 입력 문서` fallback을 사용한다.

## 3. 상단 설명과 카드 문구

기대:

- 필요한 기술·운영·재무 분석만 선택할 수 있고 제출 문서가 기준이라는 설명이 상단에 한 번만 있다.
- `독립 사용 가능` badge가 없다.
- `선택형 · 독립 문서 분석` status가 없다.
- 카드마다 앞 단계 결과를 사용하지 않는다는 같은 설명이 반복되지 않는다.
- 기술/운영/재무 카드는 각 분석이 다루는 실제 범위를 짧게 설명한다.

실제 실행은 계속 서로 독립적이어야 한다. 앞 단계 결과를 새 필수 조건으로 요구하면 회귀다.

## 4. Compact 보고서 toolbar

ProjectStageHeader 바로 아래를 확인한다.

기대:

- 작은 `보고서` utility toolbar가 하나 있다.
- 하단에 네 번째 대형 보고서 module이 없다.
- 완료/current/not-stale 보고서만 선택할 수 있다.
- 1개 선택은 개별 보고서, 2~3개 선택은 통합 보고서로 이동한다.
- 재무 → 기술 → 운영 순으로 선택해도 통합 문서는 기술 → 운영 → 재무 순서다.
- 준비된 보고서가 없으면 disabled checkbox 3개 대신 짧은 안내만 보인다.

390px에서는 picker와 button이 card 밖으로 나가지 않아야 한다.

## 5. 세로 workflow

각 카드에 세 station과 두 connector가 보이는지 확인한다.

기술/운영:

1. 템플릿 받기
2. 실제 계획 작성·업로드
3. 분석 결과 확인

재무:

1. 재무 템플릿 받기
2. 재무 값과 산정 근거 작성·업로드
3. 손익·현금흐름 분석 결과 확인

기대:

- 일반 bullet list가 아니라 station과 vertical connector가 명확하다.
- 큰 nested step card를 반복하지 않는다.
- helper는 한 줄 중심이고 `독립` 표현을 반복하지 않는다.
- 3열 card 안과 mobile 1열 모두 horizontal overflow가 없다.

## 6. 프로젝트 개요 복귀

다음 Journey를 각각 연다.

- 사업 기획
- 사업 검증
- 출시 준비
- 가상 인터뷰
- 마케팅 전략
- 최종 보고서

기대:

- `프로젝트 개요 / 현재 Journey` breadcrumb가 유지된다.
- 같은 row 오른쪽에 chevron-left icon action이 하나 있다.
- keyboard focus가 보인다.
- accessible name/title은 `프로젝트 개요로 돌아가기`다.
- 클릭하면 browser history와 관계없이 해당 프로젝트 개요 route로 이동한다.
- 직접 URL, 새 tab, report route 진입에서도 프로젝트 밖으로 이탈하지 않는다.

프로젝트 개요 화면 자체에는 self-return icon이 없어야 한다.

## 7. Report Page 의미 구분

Launch Readiness Report Page에서 확인한다.

- 공통 icon: 프로젝트 개요로 이동
- `출시 준비로 돌아가기`: 현재 Journey의 출시 준비 화면으로 이동

두 action은 목적이 다르므로 모두 유지되어야 한다.

## 8. 보호 회귀

다음을 짧게 확인한다.

- Technology/Operations DOCX 분석 시작 가능
- Finance DOCX import와 계산 정상
- Finance는 Market/BM 없이 시작 가능
- 실행 중 JobEvent와 Work Center 상세 정상
- 보고서는 React screen과 print가 같은 문서
- `PDF로 저장`은 `window.print()` 사용
- Finance raw KRW + 한국식 읽기 표현 유지
- 통합 보고서 기술 → 운영 → 재무 순서 유지
- 기존 backend PDF compatibility endpoint 유지

## 문제 보고 시 함께 제공할 정보

- project ID와 현재 Journey
- 업로드한 Finance 실제 filename
- route 재진입/새로고침 여부
- viewport 크기
- toolbar에서 선택한 보고서 순서
- overview icon 클릭 전 URL과 이동 후 URL
- 문제 화면 screenshot

