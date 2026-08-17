# P0 Market Child Error Transport 사용자 검증

1. 배포 후 실제 Product Market을 한 번 실행한다.
2. 실패하면 같은 taskRunId/correlationId의 ai-server `AI execution failed` 로그를 찾는다.
3. `safeDiagnostics.stage`, `safeDiagnostics.detail`, upstream/provider/schema/retry metadata가 실제 child 원인으로 기록되는지 확인한다.
4. detail이 `app.providers.structured.ProviderFailure: TRANSIENT_EXECUTION_FAILURE` 한 줄로만 축약되지 않았는지 확인한다.
5. API key, Authorization, bearer, `sk-*`, prompt/request/response/document/content 원문이 error output 및 서버 로그에 없는지 확인한다.
6. 성공 실행에서는 기존 Market 결과 JSON과 상태 전이가 변하지 않는지 확인한다.

