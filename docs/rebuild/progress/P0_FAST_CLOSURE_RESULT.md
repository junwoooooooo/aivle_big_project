# P0 Fast Closure 결과

## 기준

- 시작 SHA: `a74970177fb8582a76810a7d3c926755fc80c556`
- 범위: Launch Readiness 제품 계약과 Market 실패 진단 가시성

## 변경 계약

- Technology/Operations Launch는 프로젝트 소유권과 해당 모듈의 유효한 professional DOCX만으로 시작한다.
- Launch 시작·재시도·현재 상태·완료 판정은 선택 컨셉, Market, BM lineage를 요구하지 않는다.
- legacy lineage DB 컬럼은 유지하며 새 Launch 스냅샷에는 `null`을 기록한다.
- Launch AI 입력의 정본은 `professionalInput`이다.
- Market subprocess 실패 detail은 secret 및 raw payload를 제거하고 600자로 제한한 뒤 `ProviderFailure.safe_diagnostics`에 보존한다.
- 내부 실행 logger는 `safeDiagnostics`를 서버 로그에 기록하며 HTTP 실패 응답에는 diagnostics를 노출하지 않는다.

## 변경 파일

- Launch backend service, DTO, snapshot entity, report bundle 및 관련 테스트
- Launch AI request/prompt adapter 및 관련 테스트
- Market runner, execution logger 및 provider-free diagnostics 테스트
- Launch 전용 Concept resolver와 해당 단위 테스트 삭제

## 실행한 검증

- Launch focused: 22 passed
- Market/Launch AI focused: 6 passed
- 정상 Market HTTP fixture focused: 3 passed
- Backend full: 693 passed
- AI full: 815 passed, 1 skipped
- Frontend baseline: 696 passed, 기존 허용 실패 6건, 신규 실패 0건
- Frontend lint: 성공
- Frontend build: 성공(기존 chunk size 경고)
- Backend compile/build: 성공
- `git diff --check`: 성공(LF/CRLF 안내 경고만 출력)

## 의도적으로 생략한 검증

- 실제 유료 provider 호출
- Docker 및 브라우저 수동 검증(이번 fast closure 요청의 test gate 범위 밖)

## 남은 위험과 계속 지점

- 현재 runtime의 `TRANSIENT_EXECUTION_FAILURE` 근본 provider 예외는 다음 실패 시 ai-server의 `safeDiagnostics` 로그에서 확인해야 한다.
- 다음 계속 지점은 실제 Market 재실행 후 `safeDiagnostics.component=market-research`와 redacted `detail`을 수집해 A~J 분류를 확정하는 것이다.

