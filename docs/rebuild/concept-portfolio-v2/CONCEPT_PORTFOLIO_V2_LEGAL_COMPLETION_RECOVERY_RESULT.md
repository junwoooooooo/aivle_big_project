# Concept Portfolio V2 — Legal Completion & Recovery 결과

## [STATUS]

IMPLEMENTATION COMPLETE  
STATIC / TARGETED LEGAL VERIFICATION COMPLETE  
LIVE PLAN ALREADY OBSERVED WORKING  
LIVE CANDIDATE ALREADY OBSERVED WORKING  
LIVE LEGAL EVIDENCE RETRIEVAL ALREADY OBSERVED WORKING  
LIVE LEGAL FACT COMPLETION PENDING USER RETEST  
LIVE FULL EVIDENCE JUDGMENT PENDING USER RETEST  
LIVE REDESIGN RECOVERY PENDING USER RETEST  
LIVE FINAL PORTFOLIO PENDING USER RETEST  
LIVE DOWNSTREAM CONTRACT_PASS PENDING USER RETEST  
PRODUCTION INTEGRATION NOT STARTED

## [AUTHORITATIVE HEAD]

- Branch: `rebuild/new-pipeline-v1`
- 작업 시작 HEAD: `b7cab6ade1fd94470af864f9b77d5d2b6607e64a`
- branch 전환, commit, push는 수행하지 않았다.

## [USER LIVE BASELINE]

- Schema/Idea/Safety/Interpretation/Kernel PASS
- Plan 7, selected 5, reserve 2
- Candidate 5/5 validation PASS
- 공식 법률 근거 조회 5/5 수행
- 초기 route 5/5 `REDESIGN_WITHIN_LINEAGE`
- C1-R1~C5-R1 생성 및 Candidate validation PASS
- 기존 최종값: legalAccepted=0, redesigned=0, replanned=0, final=0

## [CURRENT LEGAL ROOT CAUSE]

Candidate의 역할·결제·이행·데이터·파트너 사실이 충분히 구체화되기 전에 Legal Source의 `requiredUserInputs`가 `_question_kind()`의 기본 `DESIGN_GAP`에 의해 조기 `REDESIGNABLE`로 변환됐다. 가격·채널·지역의 “정보가 필요합니다” 문구도 governance placeholder로 인식되지 않았다.

## [LEGAL FACT COMPLETENESS]

`legal_fact_completeness.py`를 추가했다. 법률 지식을 넣지 않고 역할, 거래/결제, 물리 이행, 개인정보, 파트너 구조의 명시 여부와 모순만 `COMPLETE / COMPLETABLE / INVALID`로 판정한다. 물리 활동이 없는 순수 digital service를 허용한다.

## [BUSINESS DESIGN COMPLETION]

`prepare_legal_candidates()`가 Legal 호출 전에 Candidate별 완결성을 검사한다. `COMPLETABLE`이면 동일 Plan/lineage의 `C*-F1`을 1회 생성하고 전체 Candidate validation과 완결성 재검사를 통과한 경우에만 Legal로 보낸다. 실패는 `LEGAL_FACT_COMPLETION_EXHAUSTED`로 후보 단위 종결한다.

## [PROPOSED DESIGN ASSUMPTION POLICY]

`CONCEPT_GENERATED`, `AI_HYPOTHESIS`, `PROPOSED` 사업 설계값은 “알 수 없는 외부 사실”이 아니라 이번 사전검토의 구현 가정으로 취급하도록 Legal Router와 최종 Legal prompt를 수정했다. provenance만으로 사용자 확인을 요구하지 않는다.

## [LEGAL QUESTION CLASSIFICATION]

기본 `DESIGN_GAP` fallback을 `AMBIGUOUS`로 교체했다. 명확한 질문은 `DESIGN_GAP / UNAVOIDABLE_EXTERNAL_FACT / CONTROL_CONVERTIBLE`로 분류하고, 모호한 질문은 strict batch classifier로 `LEGAL_CLARIFICATION`까지 포함해 분류한다. “통제조건으로 자격 보유 파트너 사용”과 “현재 계약 보유”를 구분한다.

## [DESIGN GAP RECONCILIATION]

결제·정산·판매·제공·중개·데이터·물리 활동·파트너·가격·채널·지역 질문이 Legal Fact Pattern의 substantive 값으로 이미 답변됐으면 `resolvedByFactPattern`으로 제거한다. 실제 미응답 design gap만 조기 REDESIGN으로 남긴다.

## [FULL EVIDENCE JUDGMENT]

완결된 설계이고 실제 외부 보유 사실이 필요하지 않으며 공식 evidence가 있으면 최종 `ConceptLegalReviewProviderResult`까지 진행한다. 기존 allowed evidence index, finding별 citation, citation-only repair 및 판단 불변 검사는 유지했다.

## [LEGAL REDESIGN]

같은 lineage에서 redesign 1회를 수행하고 전체 Candidate validation → 요구 충족 → Legal Fact Completeness → Legal 재검토 순서로 실행한다.

## [REDESIGN REQUIREMENT COMPLIANCE]

`validate_redesign_requirements()`가 parent/child와 redesign requirement를 비교해 `PASS / AMBIGUOUS / FAIL`을 반환한다. 미충족이면 `LEGAL_REDESIGN_COMPLIANCE_REPAIR`를 1회 실행하고 전체 validation과 compliance를 다시 확인한다.

## [REDESIGN LOOP DETECTION]

정규화된 redesign requirement가 두 번째 Legal에서도 반복되면 `LEGAL_REDESIGN_LOOP_DETECTED`로 종결한다. redesign budget을 넘겨 새 child를 만들지 않는다.

## [SECOND LEGAL ROUTE HANDLING]

자식의 두 번째 route를 `ACCEPT / NEEDS_INPUT / REPLAN_REQUIRED / SYSTEM_FAILURE / REDESIGN_WITHIN_LINEAGE` 모두 명시적으로 처리한다. silent drop을 제거했다.

## [LEGAL REPLAN]

reserve 또는 targeted replacement Plan → expansion → 전체 Candidate validation → Legal Fact Completeness → Legal 재검토 경로를 사용한다. replan 자식의 redesign/needs-input/system-failure도 종결한다.

## [NEEDS INPUT]

실제 보유 인허가, 기존 필수 계약, 실제 고정 관할 같은 외부 현실 사실만 `NEEDS_INPUT` 대상이다. Candidate별 사실은 `CANDIDATE`, LOCK 충돌은 `GLOBAL` scope를 유지한다.

## [PARTIAL PORTFOLIO POLICY]

일부 후보가 completion/redesign/replan을 소진해도 Legal ACCEPT 후보가 있으면 `READY_LIMITED`로 진행한다. 모든 후보가 종결됐는데 0개면 `FAILED`이며 완료 후 `LEGAL_PENDING`으로 남기지 않는다.

## [LEGAL METRICS]

RunSummary에 fact completion, redesign, replan의 `attempted / validated / accepted / exhausted`를 각각 추가했다. LegalReview에는 question 분류·reconciliation·full judgment·recovery resolution 진단을 추가했다.

## [NOTEBOOK OBSERVABILITY]

정본 Notebook 소스에 Legal Fact Completeness/Completion 결과, 준비된 C1 Fact Pattern, C1-only initial Legal과 staged recovery, compliance/second route, remaining 4 실행 gate, exhaustive recovery trace 및 terminal 상태를 표시했다. 기존 사용자 LIVE 출력과 checkpoint/recording은 보존했다.

## [TEST RESULTS]

- `python -m compileall -q ai/app/concept_portfolio_v2 ai/app/tasks/concept_legal_review ai/app/legal/pipeline.py` PASS
- 관련 targeted suite: `156 passed in 4.98s`
- production entrypoint 주입형 fresh MOCK smoke: `READY_FULL`, 5 concepts, legalAccepted=5, downstream hypothesis confirmation 대기
- Notebook JSON parse + 모든 code cell syntax compile PASS (`94` cells)
- `git diff --check` PASS
- generic fixture: food delivery, B2B SaaS, local service marketplace, education, travel, secondhand marketplace, AI productivity tool

## [NOT RUN]

- AI Provider LIVE 재호출
- MOLEG LIVE 재호출
- 전체 regression/postgres/Docker/browser/frontend production build
- production route/DB/frontend 통합

## [USER LIVE RETEST ORDER]

1. Kernel Restart
2. `MODE=LIVE`
3. Schema Preflight
4. Idea Brief / Safety
5. Plan
6. Candidate
7. Candidate Recovery
8. Legal Fact Completeness C1
9. C1 Fact Completion 필요 여부 확인
10. C1 Legal Fact Pattern 확인
11. `RUN_FULL_LEGAL_C1=True`
12. C1 initial Legal route 확인
13. REDESIGN이면 C1 lineage child만 실행
14. Redesign Compliance 확인
15. C1 second Legal route 확인
16. C1 정상 terminal 확인
17. 그 후 remaining 4 Legal 실행
18. Legal Recovery summary
19. Final Portfolio
20. Concept 수동 선택
21. 7 Hypothesis
22. Delta Legal 필요 시 실행
23. Market/Marketing `CONTRACT_PASS`

## [FILES MODIFIED]

- `ai/app/concept_portfolio_v2/`: models, engine, providers, adapters, diagnostics, language policy, 신규 legal fact completeness
- `ai/app/tasks/concept_legal_review/`: models, service
- `ai/app/legal/pipeline.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb` 소스 셀
- 관련 targeted tests 및 신규 legal completion/recovery tests
- 본 결과/진행/사용자 검증 문서

사용자 소유 checkpoint와 LIVE recordings의 기존 변경은 수정하거나 삭제하지 않았다.

## [GIT DIFF --STAT]

최종 `git diff --stat`은 Notebook의 기존 사용자 LIVE 출력과 recordings 변경을 함께 포함하므로 순수 구현량보다 크게 보인다. 구현 범위는 위 `[FILES MODIFIED]`에 한정된다.
