# P0 Fast Closure 사용자 검증

## Launch Readiness

1. 컨셉 선택, Market 결과, BM 결과가 없는 소유 프로젝트를 준비한다.
2. Technology 화면에서 실제 Technology 양식 DOCX를 채워 업로드한다.
3. `POST /api/v3/projects/{id}/launch-readiness/technology/analysis-runs`가 HTTP 202를 반환하는지 확인한다.
4. Operations도 같은 조건으로 HTTP 202를 반환하는지 확인한다.
5. 손상된 DOCX를 업로드해 HTTP 4xx와 `VALIDATION_FAILED`가 반환되고 generic 500이 아닌지 확인한다.
6. Market 또는 BM을 새로 실행해도 기존 Launch 결과가 stale로 바뀌지 않는지 확인한다.

## Market 진단

1. 실제 Market 실패 직후 ai-server 로그에서 같은 taskRunId/correlationId의 `AI execution failed` 항목을 찾는다.
2. `safeDiagnostics`에 `component: market-research`와 실패 detail이 있는지 확인한다.
3. API key, Authorization, bearer token, `sk-*`, prompt/request/response/document/content 원문이 로그에 없는지 확인한다.
4. HTTP 응답에는 `TRANSIENT_EXECUTION_FAILURE`만 있고 raw diagnostics가 없는지 확인한다.

