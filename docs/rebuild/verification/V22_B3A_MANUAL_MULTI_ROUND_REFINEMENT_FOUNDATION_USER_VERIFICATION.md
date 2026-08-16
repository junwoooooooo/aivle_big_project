# V22-B3A 사용자 검증

환경 검증 시 다음만 확인한다.

1. Round 1 `AWAITING_DECISION`에서 현재 `proposalSetHash`와 `Idempotency-Key`로 `POST /api/v3/projects/{projectId}/business-validation/refinement/next`를 호출한다.
2. 부모가 `DECLINED`, child가 Round 2 `PROPOSING`이며 Hypothesis/BM/Seed가 호출만으로 바뀌지 않았는지 확인한다.
3. 적용 완료된 `APPLIED_PENDING_FINALIZATION`에서는 `expectedDecisionHash`로 next를 호출하고, child baseline revision과 overlay가 적용 후 값을 가리키는지 확인한다.
4. Round 3에서 next가 거부되고 TaskRun이 추가되지 않는지 확인한다.
5. 이전 round가 적용된 뒤 마지막 round에서 KEEP_CURRENT/NO_CHANGES를 선택해도 Final outcome과 selectedChanges가 누적 결과를 보존하는지 확인한다.

Frontend 버튼/시각 검증은 V22-B3B까지 보류한다.
