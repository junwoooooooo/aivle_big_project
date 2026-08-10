# Concept Portfolio V2 LIVE E2E Semantic Stabilization — 사용자 검증

정본: `ai/notebooks/concept_portfolio_v2_lab.ipynb`

1. Kernel Restart 후 `MODE=LIVE`로 Schema/Idea/Safety/Interpretation/Kernel을 실행한다.
2. Plan selected 5개를 확보하면 reserve 1이어도 계속 진행되는지 확인한다.
3. Candidate 5개 validation을 확인한다.
4. direct seller Candidate의 `intermediaryRole="중개하지 않음"`이 `EXPLICIT_ABSENCE` 의미로 COMPLETE인지 확인한다.
5. completion 후 같은 값이 UNKNOWN으로 되돌아가거나 factExhausted가 증가하지 않는지 확인한다.
6. Full Legal 표에서 ProductionStatus와 SourceStatus가 별도 열인지 확인한다.
7. `SOURCE_PARTIAL`이어도 evidence-backed ACCEPT route가 유지되고 coverage 제한 문구가 보이는지 확인한다.
8. IMPLEMENTABLE 계열 결과에 redesignRequirements가 남지 않는지 확인한다.
9. Final Portfolio 4는 READY_LIMITED, 5는 READY_FULL인지 확인한다.
10. Concept을 수동 선택하고 7 Hypothesis의 ProposedValue/SemanticStatus/DecisionStatus를 확인한다.
11. `대상 지역은 명시되지 않았습니다`, `가격 정보는 미제공` 같은 값이 UNRESOLVED인지 확인한다.
12. CONFIRM_ALL 후 UNRESOLVED가 ACCEPTED로 변하지 않고 finalValue가 비어 있는지 확인한다.
13. unresolved가 있으면 Market/Marketing/Handoff가 `NOT_READY / UNRESOLVED_HYPOTHESES`인지 확인한다.
14. 실제 7개 값을 입력·확정하고 법률 민감값 수정 시 Delta Legal을 실행한다.
15. 7개 semantic valid, Legal ACCEPT, Delta Legal 완료 후에만 Market/Marketing `CONTRACT_PASS`인지 확인한다.
