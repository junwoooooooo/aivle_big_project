# P0 Market Child Error Transport 결과

## 기준과 범위

- 시작 SHA: `efbdeb3f0a28530db44d8f02490ea0a45e679b6b`
- 변경 범위: Product Market child process 오류 전송
- Launch, Finance, Frontend, Business Validation orchestration, BM은 변경하지 않았다.

## 변경 파일

- `ai/app/research/product_runner.py`
- `ai/app/research/product_pipeline.py`
- `ai/tests/test_product_integration.py`
- `ai/tests/test_market_child_error_transport.py`

## 구현 계약

- child CLI에 `--error-output`을 추가하고 ProviderFailure를 안전한 JSON envelope로 기록한다.
- code/reason/retryable/status, upstream/provider/schema/retry metadata, safe provider message와 safe diagnostics를 process boundary 너머로 보존한다.
- 일반 예외는 exception class, stage, sanitized message만 기록한다.
- parent는 허용된 내부 code/reason/status/retry 조합을 검증한 뒤 ProviderFailure를 복원한다.
- structured envelope가 없거나 손상된 경우에만 sanitized stderr 마지막 줄을 fallback으로 사용한다.
- Market algorithm, schema, model, budget, timeout, retry 및 결과 생성은 변경하지 않는다.

## Redaction

- Authorization, bearer, `sk-*`, API key, token, secret, password를 제거한다.
- prompt/input/request/response/document/content 계열 diagnostics 값은 `[REDACTED]`로 바꾼다.
- 문자열은 600자, 객체·배열은 20개 필드, 중첩은 3단계로 제한한다.
- parent가 error envelope를 읽을 때 같은 sanitizer를 다시 적용한다.

## 실행한 검증

- focused: 11 passed
- transport focused 재검증: 5 passed
- AI full: 820 passed, 1 skipped
- `git diff --check`: 성공(LF/CRLF 안내만 출력)

## 의도적으로 생략한 검증

- 실제 유료 provider 호출
- Docker 및 브라우저 실행
- 변경이 없는 backend/frontend 테스트

## 남은 위험과 계속 지점

- 실제 runtime의 다음 Product Market 실패에서 `safeDiagnostics.stage/detail`과 provider metadata가 ai-server 로그에 나타나는지 확인해야 한다.
- 계속 지점은 동일 taskRunId/correlationId 로그에서 transport된 원인을 수집해 provider/config/API 호환성 분류를 확정하는 것이다.

