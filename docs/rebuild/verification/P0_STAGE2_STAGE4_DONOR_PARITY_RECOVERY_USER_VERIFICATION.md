# P0 Stage 2·4 Donor Parity Recovery 사용자 검증

## 현재 상태

- 구현 및 공급자 호출 없는 자동 회귀: PASS
- 브라우저 앱 접속: NOT RUN — 로컬 3000 포트 연결 거부
- 실제 Market provider 실행: NOT RUN — 유료 provider 호출 금지
- 실패 TaskRun / backend / AI 로그 확인: NOT RUN — 해당 실행 환경과 DB가 없음

## 1. 사전 증거 수집

실제 실패가 재현되는 stack에서 secret을 출력하지 말고 다음을 보존한다.

- 실패 TaskRun의 `state`, `last_error_code`, attempt, deadline, lineage
- backend의 동일 TaskRun ID root exception
- AI server의 동일 request/task ID root exception
- provider HTTP status와 오류 분류만 보존하고 key, Authorization header, raw secret은 제외
- Market result/version과 Business Validation session의 pinned market version

## 2. Stage 2 브라우저 계약

1. 프로젝트에서 `2. 사업 검증`으로 이동한다.
2. 시장 분석을 시작하고 RUNNING → COMPLETED 및 결과 표시를 확인한다.
3. BM이 성공한 동일 Market version을 사용해 실행되는지 확인한다.
4. BM COMPLETED 뒤 컨셉 다듬기가 활성화되는지 확인한다.
5. refinement를 확인·적용하고 Journey/sidebar/dashboard 상태가 일치하는지 확인한다.

실패 계약도 별도로 확인한다.

- Market 실패: 정확한 오류 표시, BM 미실행, Market retry 가능
- BM 실패: 성공 Market 결과 유지, BM-only retry 가능
- Market 새 version: BM이 새 current version을 사용하고 이전 lineage를 섞지 않음
- 동시 클릭/재시도: active TaskRun 한 개만 존재
- refinement: current completed Market/BM session에서만 활성화

## 3. Stage 4 브라우저 계약

1. 프로젝트에서 `4. 가상 인터뷰`로 이동한다.
2. Journey/sidebar/dashboard에 canonical 인터뷰 slot 하나만 필수 단계로 보이는지 확인한다.
3. Market Interview RUNNING → COMPLETED 뒤 Stage 4가 COMPLETED인지 확인한다.
4. legacy Twin Survey가 NOT_READY여도 완료를 block하지 않는지 확인한다.
5. legacy Twin-only 완료 프로젝트가 canonical marketInterview 완료로 보이는지 확인한다.
6. Twin API와 선택적/compatibility 기능 자체가 유지되는지 확인한다.

## 4. 반환할 증거

1. redacted 실패 TaskRun row와 양쪽 root exception
2. Market → BM에서 사용된 Market version/session ID 일치 여부
3. BM → Refinement source session 일치 여부
4. Stage 2 각 전이의 화면과 상태
5. Stage 4 Journey/sidebar/dashboard의 동일 상태
6. provider 호출 여부와 비용 승인 범위

현재 판정은 **IMPLEMENTATION READY FOR MANUAL ACCEPTANCE**이며, 위 브라우저 계약과 실제
`MARKET_FAILED` 실행 증거가 확인되기 전에는 acceptance 완료로 올리지 않는다.
