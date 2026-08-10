# Concept Portfolio V2 — Generic LIVE Portfolio Engine Normalization 결과

## [STATUS]

- IMPLEMENTATION COMPLETE
- STATIC / MOCK / GENERIC-DOMAIN / REPLAY VERIFICATION COMPLETE
- USER LIVE RETEST PENDING
- V1 production route/DB/frontend cutover NOT STARTED
- commit/push 미수행

## [GENERICIZATION]

기존 V2 Engine과 Legal/Hypothesis/Handoff를 유지한 채 비교 모델을 `OpportunityKernel → ConceptThesis + BusinessArchitecture → Portfolio Relation/Selection`으로 전환했다. Notebook과 production entrypoint는 동일 `ConceptPortfolioEngine`을 사용한다.

## [REMOVED FOOD-SPECIFIC POLICY]

Core에서 다음을 제거했다.

- `SUPPLY_INTENT`와 식품 keyword 기반 intent 검사
- 가구 입력에 특정 하위 세그먼트를 주입하는 규칙
- 기업·학교 급식 및 구내식당 전용 drift 규칙
- 식품 solution별 controlled code
- Provider-owned arbitrary mechanics code
- 두 축 이상 차이를 요구하는 universal DISTINCT 규칙

사용자 최신 LIVE 증거에서는 한국어 Plan 6개가 의미 있을 수 있었지만 1개만 accepted되고 Candidate가 fidelity owner mismatch로 전부 실패했다. 이 출력은 checkpoint SHA-256 `e0ac887aa7a459e44f3ffc89c67f6db2d43f87bc20bb8f6a881e4029694d8c79`에 보존했다.

## [OPPORTUNITY KERNEL]

Idea Brief interpretation의 interpretedProblem, interpretedTargetUsers, usageContext, conciseIdeaDefinition을 사용해 단일 `OpportunityKernel`을 생성한다. 필드는 problemCore, targetCore, useContexts, intentComponents, mustPreserve, maySpecialize, forbiddenDriftSummary다. Plan/Candidate/Redesign/Replan이 같은 Kernel을 공유한다.

결정론 검사는 빈 값과 명확한 충돌만 거부한다. 표면 token으로 확정할 수 없는 관계는 Gateway semantic scope 판정으로 보낸다.

## [CONCEPT THESIS]

명시적 thesis 필드를 추가했다.

- targetSegmentThesis
- useCaseThesis
- valuePropositionThesis
- offerThesis
- solutionThesis

Plan draft에도 targetSegment, problemFocus, useContext, valueProposition, offerThesis, solutionThesis가 존재하므로 전체 문장을 problem/target/solution에 동시에 넣는 검사를 제거했다.

## [BUSINESS ARCHITECTURE]

System-owned generic code set은 Business Role, Operating, Partner, Delivery, Transaction, Monetization, Customer Interaction, Data/Physical Dependency로 구성한다. `OTHER`를 허용하지만 domain-specific solution을 거대한 enum으로 만들지 않는다. domain solution은 normalized semantic `solutionThesis`와 `mechanismFamily`로 보존한다.

## [DUPLICATE / VARIANT / DISTINCT]

- `DUPLICATE`: Thesis와 Architecture가 사실상 동일
- `VARIANT`: 같은 Architecture/Family라도 target/use/value/offer가 의미 있게 다름
- `DISTINCT`: solution 또는 primary Architecture 선택이 다름
- `OUT_OF_SCOPE`: OpportunityKernel에서 이탈

Portfolio acceptance는 DUPLICATE와 OUT_OF_SCOPE만 관계상 제거하며 VARIANT와 DISTINCT를 모두 정상 후보로 유지한다. primary 차이는 한 축만으로도 DISTINCT가 될 수 있다.

## [PORTFOLIO FAMILY POLICY]

Family는 `businessRole:operatingModel`로 system이 소유하고 한국어 label을 별도로 제공한다. 같은 Family 최대 2개는 selection preference다. 다른 Family가 부족하면 세 번째 의미 있는 Variant도 허용하며 hard reject하지 않는다.

## [SYSTEM-OWNED CANONICALIZATION]

Provider Plan schema에서 canonical descriptor/code/family를 제거했다. Provider는 business draft만 생성한다. `GenericConceptNormalizer`가 controlled code를 소유하며 Provider가 새 arbitrary code를 추가할 수 없다.

## [PLAN NORMALIZATION]

`PortfolioPlanDraft → GenericConceptNormalizer.from_plan() → CanonicalConceptDescriptor` 경로를 사용한다. Provider strict schema에는 canonical code 필드가 없다.

## [CANDIDATE NORMALIZATION]

actual `ConceptCandidateResult → GenericConceptNormalizer.from_candidate() → CanonicalConceptDescriptor` 경로를 사용한다. Plan descriptor를 복사하지 않으며 Candidate actual business fields를 사용한다.

## [PLAN FIDELITY]

Fidelity는 `PASS / ADAPTED / FAIL`이다. Opportunity, target/use, core value, offer, solution thesis를 우선 보며 secondary Architecture 구체화는 ADAPTED로 허용한다. 같은 Plan에서 정상 확장된 Candidate는 generic 7-domain tests에서 모두 PASS 또는 ADAPTED였다.

## [ADAPTIVE PLANNING]

초기 target은 maxConcepts+2(최대 8)다. 검증 후 usable Plan이 부족하면 Candidate로 즉시 내려가지 않고 `prepare_portfolio_plans()`가 최대 2개 replenishment round를 실행한다.

## [PORTFOLIO REPLENISHMENT]

Gateway 입력은 OpportunityKernel, locks, 기존 Plan/Family, rejected reason, missing coverage, round를 포함한다. Prompt는 모든 Architecture가 달라야 한다고 요구하지 않고 새 Thesis 또는 Architecture의 사용자 비교 가치를 요구한다. 보충 결과가 없으면 5개를 억지로 생성하지 않고 READY_LIMITED로 진행한다.

## [LIVE CANONICAL PATH]

Core의 MODE별 business policy 분기를 제거했다. Idea Brief를 포함한 외부 작업 선택은 Gateway가 담당한다. Planning, replenishment, candidate, relation, fidelity, legal, redesign, replan, delta legal은 모두 Gateway/Provider 경계를 사용한다.

## [PRODUCTION ENTRYPOINT]

`app/tasks/concept_portfolio_v2/service.py::execute_concept_portfolio_v2()`를 추가했다. 기본은 LIVE Gateway이며 테스트에서는 동일 Engine을 주입한다. 별도 prototype business logic이 없다.

## [LEGAL REGRESSION]

다음 기존 수정은 유지했다.

- dynamic evidence reference enum
- allowedEvidenceReferenceIndexes
- nested finding min 1
- citation repair 1회와 안전 diagnostics
- locked targetRegion external fact
- same-lineage redesign
- targeted legal replan
- candidate-scoped NEEDS_INPUT → READY_LIMITED
- 실제 Delta Legal result gate

## [HYPOTHESIS / HANDOFF REGRESSION]

한국어 content, `targetRegion=대한민국` AI hypothesis, 7 hypothesis provenance, manual confirm, Delta Legal, Market Seed, Marketing Source, canonical hash, CONTRACT_PASS 경로를 유지했다.

## [GENERIC DOMAIN TESTS]

다음 fixture를 Core keyword 하드코딩 없이 실행했다.

- food delivery
- B2B SaaS
- local service marketplace
- education service
- travel planning
- secondhand marketplace
- AI productivity tool

모든 domain이 최소 3개 이상 유효 Concept과 downstream CONTRACT_PASS를 만들었고 non-food SaaS/교육/여행 입력이 ANCHOR_DRIFT 또는 OTHER collapse로 실패하지 않았다.

## [TEST RESULTS]

- compileall: PASS
- 기존 V2 + LIVE E2E normalization + generic portfolio + shared Legal + Idea Brief targeted: `128 passed`
- generic relation 5개 시나리오: PASS
- duplicate-heavy adaptive replenishment: 최종 5개 PASS
- exhausted open space: READY_LIMITED 3 PASS
- production entrypoint same Engine contract: PASS
- Notebook strict nbformat/47 code cell syntax: PASS
- Notebook fresh MOCK Run All: PASS
- fresh generic MOCK recording → full REPLAY: READY_FULL 5 / CONTRACT_PASS / REPLAY_READY / topLevelExternalOperations 0
- food-specific Core static scan: PASS

## [NOT RUN]

- AI Provider LIVE
- MOLEG LIVE
- Docker/browser/provider smoke
- full regression/full postgresTest
- frontend production build
- production route/worker/frontend cutover

## [USER LIVE TEST ORDER]

1. Idea Brief + Interpretation
2. Generic OpportunityKernel
3. Plan Pool LIVE
4. Portfolio Family / VARIANT / DISTINCT 결과
5. Adaptive replenishment 여부와 최종 Plan 최대 5개
6. Candidate C1
7. Plan Fidelity PASS/ADAPTED
8. Remaining Candidates
9. Candidate Portfolio 관계에서 DUPLICATE만 제거되는지 확인
10. Legal C1
11. Remaining Legal
12. Redesign/Replan
13. Final Portfolio
14. Concept 선택
15. 7 Hypothesis
16. 필요한 경우 Delta Legal
17. Market/Marketing CONTRACT_PASS

