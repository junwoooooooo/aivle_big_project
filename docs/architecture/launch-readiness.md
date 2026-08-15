# 출시 준비 모듈 구조

출시 준비는 기술, 운영, 재무 분석과 통합 보고서 다운로드를 한 기능으로 제공한다.

## Frontend

`frontEnd/src/features/launch-readiness/`

- `pages/LaunchReadinessPage.jsx`: 출시 준비 단일 화면
- `api/professionalReadinessApi.js`: 기술·운영 전문 분석 API
- `finance/api/financeApi.js`: 재무 입력·분석 API
- `finance/hooks/useFinance.js`: 재무 업로드·분석 상태
- `finance/styles/finance.css`: 재무 카드 공통 스타일
- `styles/launch-readiness.css`: 출시 준비 화면 전용 스타일

이전의 독립 `features/tech-ops`와 `features/finance/pages` 화면은 라우팅에서 제거되어 삭제했다.

## Backend

`backend/src/main/java/com/aivle/backend/launchreadiness/`

- `controller`: 기술·운영 분석 및 통합 PDF 다운로드 API
- `service`: 기술·운영 AI 분석, 보고서 생성, 통합 출처 수집
- `domain`, `repository`: 기술·운영 분석 결과 저장

재무 계산·입력 Snapshot은 다른 파이프라인과도 공유되는 도메인 모델이므로
`backend/src/main/java/com/aivle/backend/pipeline/finance/`에 유지한다. 출시 준비 화면에서
사용하는 재무 API와 통합 PDF 연결은 `launchreadiness` 컨트롤러가 담당한다.

예전 `pipeline/techops`는 이전 데이터 계약 및 최종 보고서 호환에 아직 참조되므로, 데이터
마이그레이션 없이 삭제하지 않는다. 웹 라우트와 프론트엔드 구현은 제거됐다.

## AI server

`ai/app/tasks/launch_readiness/`

- `professional/`: 기술·운영 전문 입력 분석
- `finance_estimate/`: 재무 입력값 AI 추정
- `finance_analysis_report/`: 재무 분석 보고서 생성

`ai/app/api/launch_readiness.py`가 기술·운영 분석 엔드포인트를 제공하고,
`ai/app/api/executions.py`가 재무 비동기 작업을 이 작업 폴더에서 불러온다.
