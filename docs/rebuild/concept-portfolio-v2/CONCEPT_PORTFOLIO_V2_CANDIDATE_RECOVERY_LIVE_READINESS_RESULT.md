# Concept Portfolio V2 — Candidate Recovery & LIVE Readiness 결과

## [STATUS]

- IMPLEMENTATION COMPLETE
- STATIC / TARGETED / GENERIC-DOMAIN VERIFICATION COMPLETE
- LIVE PLAN PORTFOLIO: ALREADY OBSERVED WORKING
- LIVE CANDIDATE GENERATION: ALREADY OBSERVED WORKING
- LIVE CANDIDATE RECOVERY: PENDING USER RETEST
- LIVE FULL LEGAL: PENDING USER RETEST
- LIVE FINAL PORTFOLIO: PENDING USER RETEST
- LIVE DOWNSTREAM CONTRACT_PASS: PENDING USER RETEST
- PRODUCTION INTEGRATION: NOT STARTED

## [AUTHORITATIVE HEAD]

- branch: `rebuild/new-pipeline-v1`
- 작업 시작 HEAD: `536752af5f8ee4410b00acddd0c8089d6b073d2c`
- branch 변경, commit, push 없음

## [USER LIVE EVIDENCE]

사용자가 저장한 LIVE Notebook은 Plan 7개, selected 5개, reserve 2개, rejected 0개를 기록했다. Candidate는 C1~C3 PASS, C4~C5 `PLAN_FIDELITY_FAILED`였고 `RUN_FULL_LEGAL_C1=False`였다. 원본은 checkpoint에 보존했으며 SHA-256은 `84f49b54c220b3aeebd855a096e9824624d4d306835b24b51bee00bd9d9e9115`이다.

## [WHAT ALREADY WORKED]

Generic OpportunityKernel, Thesis/Architecture 분리, system-owned Plan/Candidate descriptor, Variant 허용, adaptive Plan replenishment, 한국어 content, Idea Brief Interpretation, Legal evidence hardening, 7 Hypothesis, Delta Legal, handoff, production entrypoint를 유지했다.

## [CANDIDATE FIDELITY ROOT CAUSE]

결정론적 Fidelity가 `PASS/ADAPTED/FAIL`만 반환해 `AMBIGUOUS` semantic fallback이 실행될 수 없었다. 실패 Candidate는 reserve나 replenishment 없이 즉시 손실됐다.

## [SEMANTIC FIDELITY]

결정론적 결과를 `PASS/ADAPTED/AMBIGUOUS/FAIL`로 확장했다. 명백한 identity 보존과 교체만 결정론적으로 확정하고 중간 사례는 작은 Plan/Candidate identity payload로 semantic judge를 호출한다. semantic `PASS/ADAPTED`는 정상 허용한다.

## [CANDIDATE REGENERATION]

Fidelity만 실패한 Candidate는 Plan별 한 번 `REGENERATE_CANDIDATE_FOR_FIDELITY`를 실행한다. `C4 → C4-G1`처럼 attempt와 parent를 기록하며 Schema, Language, LOCK, Anchor, Fidelity, Portfolio duplicate 전체 검사를 다시 수행한다.

## [RESERVE PLAN RECOVERY]

regeneration 이후 부족한 slot은 현재 accepted Portfolio 대비 Opportunity fit, clarity, redundancy, marginal difference를 계산해 가장 가치가 높은 reserve Plan으로 채운다. 생성 순서 우선 정책은 사용하지 않는다.

## [ADAPTIVE REPLENISHMENT]

reserve 이후에도 부족하면 기존 Plan replenishment Gateway를 재사용해 새 Plan validation → Candidate generation → full Candidate validation을 수행한다. budget 소진 후 3~4개만 유효하면 `READY_LIMITED`다.

## [FINAL CANDIDATE PORTFOLIO POLICY]

최대 5개이며 강제 충원하지 않는다. Fidelity 실패는 same-Plan regeneration, Candidate duplicate는 reserve/new Plan을 우선한다. Legal은 Candidate Portfolio 확정 후 실행한다.

## [PORTFOLIO SELECTION SCORING]

Plan base quality는 Opportunity fit, Concept clarity, constraint fit, feasibility signal로 계산한다. 이후 greedy marginal coverage와 Architecture bonus, soft same-family penalty를 결합한다. 모든 Architecture가 달라야 한다는 조건은 없다.

## [GENERIC CANONICALIZATION CONFIDENCE]

Architecture 각 축에 `code/confidence/source` diagnostics를 추가했다. 근거가 없거나 규칙이 충돌하면 직접운영·자체운영·디지털·건별로 추정하지 않고 `OTHER/LOW/UNKNOWN`으로 둔다. 중요한 low-confidence 축만 Plan/Candidate 공통 strict batch semantic classifier로 보완한다.

## [RELATION POLICY]

`DUPLICATE/VARIANT/DISTINCT` 정책을 유지했다. low-confidence primary code 차이 하나만으로 DISTINCT를 확정하지 않으며 relation confidence를 기록한다. DUPLICATE만 high-confidence에서 제거한다.

## [LEGAL FACT PATTERN]

Notebook에서 C1의 platform/provider/seller/intermediary role, transaction/payment flow, personal data, physical activity, partner, qualification, advertising claim을 Legal 실행 전에 표시한다. Legal 결과에는 production status, controls, partners/qualifications, disclosures, prohibited variants, evidence와 diagnostics를 표시한다.

## [QUALIFICATION / DATA / PHYSICAL DEPENDENCY]

`없음`, `필요 시 확인`, `해당 활동에 필요한 자격`, `관련 자격`, `필요한 경우 개인정보 처리` 같은 placeholder는 Legal precheck dependency로 계산하지 않는다. 구체적인 자격 주체, 데이터 항목/목적, 물리 활동만 dependency를 활성화한다. Candidate LIVE prompt도 placeholder 생성을 금지한다.

## [IDEA READINESS CONSISTENCY]

`READY_FOR_REVIEW + missingFieldKeys=[] + score=0`은 V2 gating을 막지 않되 `READINESS_INCONSISTENT` trace/Notebook diagnostic으로 표시한다. 임의 점수는 생성하지 않는다.

## [PRODUCTION ENTRYPOINT]

`app/tasks/concept_portfolio_v2/service.py`를 유지했다. Notebook staged mode와 production `run_full()` 모두 `prepare_portfolio_plans → prepare_candidate_portfolio → Legal`의 동일 Engine 경로를 사용한다.

## [TEST RESULTS]

- compileall PASS
- 기존 Generic/Legal/Hypothesis/Handoff 회귀 포함 targeted tests PASS
- Candidate recovery 18개 시나리오 PASS
- 3개 generic domain recovery PASS
- selection shuffle 안정성 PASS
- canonicalization confidence/fallback PASS
- Legal placeholder filter PASS
- Notebook syntax/nbformat PASS
- fresh MOCK Notebook Run All PASS
- fresh MOCK→REPLAY `REPLAY_READY`, `CONTRACT_PASS` PASS
- `git diff --check` PASS (줄 끝 정규화 안내 외 오류 없음)

## [NOT RUN]

- 실제 AI Provider LIVE Candidate recovery
- 실제 Full Legal C1 / MOLEG
- remaining LIVE Legal, Final Portfolio, downstream CONTRACT_PASS
- Docker/browser/full regression/postgresTest/frontend build
- V1 Java/DB/frontend production integration

## [USER LIVE RETEST ORDER]

1. Kernel Restart 후 `MODE='LIVE'`
2. Schema Preflight
3. Idea Brief / Safety / Interpretation
4. Opportunity Kernel
5. Plan Pool 6~8개 확인
6. Portfolio Selection selected 최대 5 / reserve / relation / score 확인
7. Candidate 1
8. Candidate 전체
9. Candidate Recovery의 initial accepted, semantic fidelity, regeneration, reserve, replenishment, final count 확인
10. C1 Legal Fact Pattern 확인
11. `RUN_FULL_LEGAL_C1=True`
12. C1 Legal 결과 확인
13. `RUN_REMAINING_LEGAL=True`
14. Legal Recovery
15. Final Portfolio
16. Manual Concept Select
17. 7 Hypothesis
18. 필요 시 Delta Legal
19. Market / Marketing `CONTRACT_PASS`

## [FILES MODIFIED]

- `ai/app/concept_portfolio_v2/`: models, mechanics, distinctness, fidelity, providers, engine, diagnostics
- `ai/tests/concept_portfolio_v2/`: 기존 generic assertion 및 Candidate recovery 회귀
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`
- `ai/notebooks/CONCEPT_PORTFOLIO_V2_LAB_README.md`
- 본 결과/진행/사용자 검증 문서

## [GIT DIFF --STAT]

tracked 기준 11개 파일, 887 insertions, 3,473 deletions이다. 삭제 대부분은 canonical Notebook의 저장된 LIVE output/execution metadata 제거다. 별도로 Candidate recovery test 1개와 결과/진행/사용자 검증 문서 3개를 새로 추가했다.
