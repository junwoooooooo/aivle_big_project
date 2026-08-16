# V22-B1 사용자 검증

1. CURRENT+COMPLETED 사업 검증에서 `POST /api/v3/projects/{projectId}/business-validation/refinement/start`를 호출하면 round가 `PROPOSING`이어야 한다.
2. 완료되지 않았거나 stale인 사업 검증에서는 TaskRun이 생기지 않아야 한다.
3. 성공 후 current 응답은 exact source-bound proposal을 `AWAITING_DECISION`으로 보여야 하며 기존 사업안·BM·가설·법률·Market Seed는 바뀌지 않아야 한다.
4. 실패 후 Selection은 기존 상태를 유지하고 round는 `FAILED`, retry는 최대 3회까지만 가능해야 한다.
5. 실행 중 source가 바뀐 결과는 채택되지 않고 current 응답이 `STALE`이어야 한다.

V30의 실제 PostgreSQL 적용과 외부 AI 장시간 실행은 **USER/ENVIRONMENT VERIFICATION PENDING**이다.
