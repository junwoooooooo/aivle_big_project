# Concept Portfolio V2 Legal Completion Recovery — 사용자 LIVE 검증

정본 파일: `ai/notebooks/concept_portfolio_v2_lab.ipynb`

1. Kernel Restart
2. `MODE=LIVE`
3. Schema Preflight PASS 확인
4. Idea Brief / Safety PASS 확인
5. Plan 및 최종 Plan 최대 5 확인
6. Candidate와 Candidate Recovery 결과 확인
7. C1 Legal Fact Completeness 상태 확인
8. `COMPLETABLE`이면 C1-F1 1회와 full Candidate validation PASS 확인
9. 준비된 C1 Legal Fact Pattern에서 판매/제공/중개/결제/이행/data/partner가 구체적인지 확인
10. `RUN_FULL_LEGAL_C1=True`
11. initial route와 `resolvedByFactPatternCount`, `finalEvidenceJudgmentExecuted` 확인
12. ACCEPT이면 C1 terminal 확인
13. REDESIGN이면 C1 lineage child만 먼저 실행됐는지 확인
14. redesign requirement와 compliance PASS 여부 확인
15. 필요 시 compliance repair가 1회만 실행됐는지 확인
16. C1 second Legal route가 ACCEPT/NEEDS_INPUT/REPLAN/SYSTEM_FAILURE/loop 중 하나로 종결됐는지 확인
17. C1 terminal 이후에만 remaining 4 Legal 실행
18. recovery trace와 attempted/validated/accepted/exhausted metrics 확인
19. 일부 실패 시 READY_LIMITED, 전부 실패 시 FAILED이며 `LEGAL_PENDING`이 아닌지 확인
20. Final Portfolio 확인
21. Concept 수동 선택 및 7 Hypothesis 확인
22. 필요 시 Delta Legal 실행
23. Market/Marketing `CONTRACT_PASS` 확인

LIVE 실행 결과에서 특히 이전의 `유통 채널/가격/특정 지역 정보가 필요합니다` 3개가 fact completion 또는 fact-pattern reconciliation 후 다시 동일 REDESIGN requirement로 반복되지 않는지 비교한다.
