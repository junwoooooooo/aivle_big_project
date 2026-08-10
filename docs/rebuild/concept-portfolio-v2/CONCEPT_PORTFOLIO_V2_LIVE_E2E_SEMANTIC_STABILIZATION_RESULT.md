# Concept Portfolio V2 — LIVE E2E Semantic Stabilization 결과

## [STATUS]

IMPLEMENTATION COMPLETE  
STATIC / TARGETED VERIFICATION COMPLETE  
LIVE IDEA / PLAN ALREADY OBSERVED WORKING  
LIVE CANDIDATE ALREADY OBSERVED WORKING  
LIVE FULL LEGAL ALREADY OBSERVED WORKING  
LIVE FINAL PORTFOLIO ALREADY OBSERVED WORKING WITH 4 CONCEPTS  
LIVE HYPOTHESIS / HANDOFF ALREADY OBSERVED END-TO-END, SEMANTIC PLACEHOLDER HARDENING PENDING USER RETEST  
LIVE CONTRACT_PASS ALREADY OBSERVED, STRICT SEMANTIC CONTRACT PASS PENDING USER RETEST  
PRODUCTION INTEGRATION NOT STARTED

## [AUTHORITATIVE HEAD]

- Branch: `rebuild/new-pipeline-v1`
- 작업 시작 HEAD: `d5667680958386d74c7513a86977561daa3dd11b`
- branch 전환, commit, push를 수행하지 않았다.

## [LATEST USER LIVE BASELINE]

- Schema/Idea/Safety/Interpretation/Kernel PASS
- Plan requested 7, returned 6, selected 5, reserve 1
- Candidate 5/5 PASS
- Legal Fact Completeness 4 COMPLETE, 1 COMPLETABLE
- C5 completion/validation PASS 후 `intermediaryRole` false rejection으로 제외
- Legal-ready 4개 모두 ACCEPT
- Final Portfolio 4, 수동 C1 선택, 7 Hypothesis, Market/Marketing `CONTRACT_PASS`
- Python exception 없음

## [WORKING FLOW FROZEN]

Generic kernel/descriptor, relation 정책, adaptive planning, candidate recovery/fidelity, 공식근거·citation hardening, legal recovery, hypothesis/delta/handoff, production entrypoint의 현재 E2E 구조를 유지했다. 새 단계 재설계나 threshold 변경을 하지 않았다.

## [LEGAL FACT FALSE REJECTION]

기존 모순 검사가 `중개하지 않음`의 “중개”를 실제 중개 행위로 읽던 문제를 수정했다. 역할 값은 `PRESENT / EXPLICIT_ABSENCE / UNKNOWN / EMPTY`로 분류하며 실제 `PRESENT` 역할끼리만 direct/intermediary 모순을 검사한다.

## [EXPLICIT ROLE ABSENCE]

- `중개하지 않음`, `직접 판매자가 아님`, `외부 파트너를 사용하지 않음`은 완결된 음의 사업 사실이다.
- `미정`, `확인 필요`, `관련 역할`, `추후 결정`은 unresolved다.
- 비어 있는 intermediaryRole도 transactionFlow가 직접 계약·직접 이행을 명확히 하면 context상 complete가 될 수 있다.
- Fact Completion prompt는 없는 역할을 만들지 않고 명시적 부재를 작성하도록 보강했다.

## [HYPOTHESIS SEMANTIC VALIDITY]

`hypothesis_validation.py`를 추가했다. 7개 Hypothesis를 `VALID / UNRESOLVED / INVALID`로 평가하고 `HypothesisDecision`에 semantic status/reason을 보존한다. TARGET_REGION, REVENUE_MODEL, PRICE, CHANNELS, DIFFERENTIATORS는 실제 사업값을 요구하며 SOM은 수치·기간·산식·가정을 검사한다.

## [PLACEHOLDER POLICY]

`명시되지 않았`, `제공되지 않았`, `미제공`, `정보 없음`, `입력되지 않았`, `아직 정해지지`, `추후 결정`, `확인/검증 필요`, `미정`, `TBD`, `UNKNOWN`, `NOT PROVIDED` 의미군을 보강했다. 짧은 field-level 미정 진술만 placeholder로 보아 정상 business sentence 오탐을 제한한다.

OPEN targetRegion은 기존 system default `대한민국`을 유지한다. PRICE는 deterministic 숫자를 만들지 않으며 unresolved이면 선택 후 handoff gate에서 차단한다.

## [CONFIRM ALL SAFETY]

`confirm_all_proposed=True`는 semantic `VALID` 값만 ACCEPTED로 바꾼다. UNRESOLVED/INVALID 값은 `PROPOSED`, `finalValue=None`으로 남는다. `run_full(auto_confirm_hypotheses=True)`도 unresolved가 있으면 handoff를 만들지 않고 `PENDING_HYPOTHESIS_CONFIRMATION`을 유지한다.

## [DOWNSTREAM CONTRACT GATE]

CurrentDownstreamAdapter가 final hypothesis 값을 독립 재검증한다. ACCEPTED로 위장된 placeholder도 `UNRESOLVED_HYPOTHESES: ...` 오류로 `CONTRACT_FAIL` 처리한다. 7개 결정 승인, 7개 semantic valid, Legal ACCEPT, Delta Legal 완료, interpretation 및 두 snapshot shape가 모두 통과해야 `CONTRACT_PASS`다.

## [LEGAL STATUS INVARIANT]

- IMPLEMENTABLE 계열: `redesignRequirements=[]`, `unknownFacts=[]`
- REDESIGNABLE: redesign requirement 필수, external unknown facts 금지
- NEEDS_FACTS: unknown facts 필수, redesign requirement 금지

불일치 Provider 결과는 silent normalize하지 않고 `LEGAL_RESULT_CONTRACT_REPAIR`를 1회 수행한다. status·요약·검토 활동·근거·금지 variant 등 보호된 판단이 바뀌면 실패한다. 기존 citation-only repair와 mutation protection은 유지했다.

## [SOURCE PARTIAL UX]

Legal route와 evidence coverage를 분리했다. `ACCEPT + SOURCE_PARTIAL`을 허용하며, Notebook에는 production status, source status, final evidence judgment 여부, evidence count와 “일부 법률 소스 조회 범위 제한” 진단을 표시한다.

## [ARCHITECTURE QUALITY]

Taxonomy와 fallback 정책은 유지했다. Plan prompt가 운영·판매/계약·파트너·거래·수익·이행·접점을 business sentence로 구체화하도록 했고, Candidate semantic fallback 입력에 provider/seller/intermediary/payment facts를 추가했다. 근거가 낮으면 OTHER를 허용하며 validity gate로 사용하지 않는다.

## [READINESS KNOWN ISSUE]

`READY_FOR_REVIEW + score=0 + missing=[]` 불일치는 기존처럼 `READINESS_INCONSISTENT` 비차단 warning으로 유지한다. 이번 범위에서 임의 score를 생성하거나 readiness 계약 전체를 변경하지 않았다.

## [TEST RESULTS]

- `python -m compileall -q ai/app` PASS
- 지정 targeted/legal/completeness/hypothesis/handoff/evidence/generic suites: `187 passed in 4.47s`
- production entrypoint 주입형 MOCK smoke: `READY_FULL`, concepts=5, legalAccepted=5, downstream hypothesis confirmation 대기
- Notebook JSON parse 및 94개 code cell compile PASS
- `git diff --check` PASS

## [NOT RUN]

- AI Provider LIVE
- MOLEG LIVE
- 전체 regression/Postgres/Docker/browser/frontend build
- Java V1, DB migration, frontend production flow, production route 통합

## [USER LIVE RETEST ORDER]

1. Kernel Restart 후 `MODE=LIVE`
2. Schema/Idea/Safety/Interpretation/Kernel 확인
3. Plan selected 5 및 reserve shortfall diagnostic 확인
4. Candidate 5 validation 확인
5. C5 유사 direct seller의 `intermediaryRole=중개하지 않음`이 COMPLETE인지 확인
6. Legal-ready 최대 5와 Full Legal route/SourceStatus 확인
7. Final Portfolio 4이면 READY_LIMITED, 5이면 READY_FULL 확인
8. Concept 수동 선택 후 7 Hypothesis SemanticStatus 확인
9. placeholder가 있으면 confirm-all 후에도 PROPOSED/finalValue=None인지 확인
10. unresolved 시 Handoff `NOT_READY / UNRESOLVED_HYPOTHESES` 확인
11. 실제 7개 값 준비 및 필요 시 Delta Legal 후 Market/Marketing `CONTRACT_PASS` 확인

## [FILES MODIFIED]

- `ai/app/concept_portfolio_v2/legal_fact_completeness.py`
- `ai/app/concept_portfolio_v2/hypothesis_validation.py`
- `ai/app/concept_portfolio_v2/{models,engine,adapters,language_policy,providers}.py`
- `ai/app/concept_portfolio_v2/diagnostics/notebook_view.py`
- `ai/app/tasks/concept_legal_review/{models,service}.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`의 진단 소스 셀
- 관련 completeness/hypothesis/legal/downstream tests
- 결과·진행·사용자 검증 문서

사용자 소유 checkpoint와 LIVE recordings는 수정하거나 삭제하지 않았다.

## [GIT DIFF --STAT]

Notebook과 recordings의 기존 사용자 LIVE 실행 이력이 함께 dirty 상태이므로 전체 diff 통계는 순수 구현량보다 크게 보인다. 구현 범위는 `[FILES MODIFIED]`에 한정된다.
