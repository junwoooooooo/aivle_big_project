# Concept Portfolio V2 Candidate Recovery — 사용자 검증

## MOCK

1. `ai/notebooks/concept_portfolio_v2_lab.ipynb`를 새 커널로 연다.
2. 기본 `MODE='MOCK'`로 Run All한다.
3. Plan 표에서 `selectionScore`, `selectionReason`, `relationToPortfolio`, Selected/Reserve를 확인한다.
4. Candidate Recovery에서 `candidateAcceptedInitially`, `candidateRegenerated`, `candidateRecovered`, `reservePlansActivated`, `candidateRecoveryReplans`를 확인한다.
5. C1 Legal Fact Pattern과 placeholder-filtered Structural Precheck를 비교한다.
6. Final Portfolio와 downstream `CONTRACT_PASS`를 확인한다.

## LIVE

1. Kernel Restart 후 `MODE='LIVE'`로 바꾼다.
2. Schema Preflight부터 Plan Selection까지 순차 실행한다.
3. Candidate 1과 remaining Candidates를 생성한다.
4. Candidate Recovery 표에서 C4/C5가 semantic `ADAPTED`, `*-G1`, reserve 또는 replenishment로 복구되는지 확인한다.
5. 최종 Candidate 수가 5 미만이면 억지 충원 대신 recovery budget과 `READY_LIMITED` 근거를 확인한다.
6. C1의 역할, 거래, 결제, 개인정보, 물리 활동, 파트너, 자격, 광고 필드를 검토한다.
7. generic placeholder가 qualification/personalData/physical dependency를 `True`로 만들지 않는지 확인한다.
8. `RUN_FULL_LEGAL_C1=True` 후 route, production status, safe summary, controls, qualifications, disclosures, prohibited variants, evidence, diagnostics를 확인한다.
9. C1 성공 후 `RUN_REMAINING_LEGAL=True`로 진행한다.
10. Legal Recovery → Final Portfolio → Manual Select → 7 Hypothesis → 필요 시 Delta Legal → Market/Marketing `CONTRACT_PASS` 순서로 실행한다.

## 기대 상태

- LIVE CANDIDATE RECOVERY: 사용자 재검증 전까지 PENDING
- LIVE FULL LEGAL: 사용자 재검증 전까지 PENDING
- Candidate Portfolio는 최대 5이며 유효 후보가 적으면 `READY_LIMITED`
- `VARIANT`는 정상 후보이고 `DUPLICATE`만 제거 대상
