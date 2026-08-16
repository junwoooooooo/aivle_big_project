# Concept Portfolio V2 Generic LIVE Portfolio Engine Normalization — 사용자 검증

## MOCK

1. `ai/notebooks/concept_portfolio_v2_lab.ipynb`를 새 커널로 연다.
2. `MODE='MOCK'`로 Run All한다.
3. 11에서 generic OpportunityKernel을 확인한다.
4. 13~14에서 planningRounds와 adaptiveReplenishmentUsed를 확인한다.
5. 15~18에서 Family, Thesis, Architecture, VARIANT/DISTINCT 관계를 확인한다.
6. 21~25에서 Candidate actual descriptor와 Portfolio 관계를 확인한다.
7. 44에서 READY_FULL 또는 의미 있는 READY_LIMITED와 CONTRACT_PASS를 확인한다.

## LIVE

1. `MODE='LIVE'` 후 커널을 재시작한다.
2. Environment와 Schema Preflight를 확인한다.
3. Idea Brief/Interpretation/OpportunityKernel을 실행한다.
4. Plan Pool LIVE를 실행한다.
5. 6~8개 Plan이 모두 다른 Architecture일 필요는 없음을 확인한다.
6. 이름만 다른 결과는 DUPLICATE, 같은 Family의 의미 있는 target/use/value/offer 차이는 VARIANT인지 확인한다.
7. accepted Plan이 5개 미만이면 adaptive replenishment가 실행되는지 확인한다.
8. open space가 소진되면 READY_LIMITED가 허용되는지 확인한다.
9. Candidate C1을 생성하고 actual descriptor가 Plan 객체 복사가 아닌지 확인한다.
10. Fidelity가 PASS 또는 ADAPTED인지 확인한다.
11. Remaining Candidates 후 DUPLICATE만 제거되는지 확인한다.
12. Legal C1부터 기존 Legal staged 순서를 실행한다.
13. Concept 선택, 7 hypothesis, 필요한 Delta Legal 후 CONTRACT_PASS를 확인한다.

## 기대 실패 의미

- DUPLICATE: 실제로 같은 Concept
- OUT_OF_SCOPE: OpportunityKernel 이탈
- LOCK_CONFLICT: 사용자 확정값과 명확한 충돌
- PLAN_FIDELITY_FAILED: core value/solution/identity가 실제로 교체됨

VARIANT는 실패나 reject reason이 아니다.
