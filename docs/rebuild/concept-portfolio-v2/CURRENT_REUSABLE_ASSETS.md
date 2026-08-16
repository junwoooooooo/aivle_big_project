# Concept Portfolio V2 현행 재사용 자산 조사

## 조사 기준

- 기준 브랜치: `rebuild/new-pipeline-v1`
- 조사 HEAD: `c2b1921f0dc73c33b89b3a345d651f28cd1540e7`
- 기준: 문서 추정이 아니라 현재 Python/Java 코드의 실제 타입·필드·변환을 확인했다.
- 결론: V2는 production route/DB/orchestration을 변경하지 않고 Python Lab adapter로 현행 계약만 재사용한다.

## 재사용 자산

| 항목 | 현재 실제 경로 | 역할 | V2 재사용 | 재사용 방식 | 비고 |
|---|---|---|---|---|---|
| Idea Brief AI 입력 | `ai/app/tasks/idea_brief/models.py` (`IdeaBriefDerivationInput`, `FieldKey`) | 13개 Seed 필드, `LOCKED/OPEN`, Safety/Interpretation strict schema | 예 | `CurrentIdeaBriefAdapter`가 import·검증 | required는 `ideaOverview`, `problem`, `targetUsers` |
| Idea Brief 도메인 필드 | `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefFieldCatalog.java` | required/optional 목록과 기본 decision state | 예 | Python의 현행 `FieldKey`와 교차 확인 | required 기본 `LOCKED`, optional 기본 `OPEN` |
| 사용자 확정/LOCK 의미 | `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefField.java` | AI가 LOCK하거나 USER source를 만들 수 없고, 사용자 입력·확정만 LOCK 가능 | 예 | adapter의 source/decision 보존 | `USER_INPUT`, `USER_CONFIRMED`를 사용자 권위로 유지 |
| Safety | `ai/app/tasks/idea_brief/service.py` | `ALLOW`, `ALLOW_WITH_RESTRICTIONS`, `BLOCK_OR_REFRAME` Gate | 예 | LIVE는 `execute_idea_brief_derivation`; MOCK은 같은 결과 계약 | Safety 차단 시 planning 미실행 |
| Interpretation | `ai/app/tasks/idea_brief/models.py` (`IdeaInterpretation`) | problem/target/context/category/scope/정의 구조화 | 예 | Canonical Seed에 그대로 보존 | 새 사업안을 만드는 입력으로 오용하지 않음 |
| Optional Seed | `ai/app/tasks/idea_brief/models.py` (`CommitmentFieldKey`) | 지역, 경쟁자, 수익, 가격, 채널, 차별점, 예산·팀·일정·기타 제약 | 예 | adapter 필드 보존 | 비어 있으면 OPEN/MISSING |
| Exploration 정책 참고 | `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptGenerationStrategyPolicy.java` | optional LOCK 밀도에 따른 `EXPLORE/REFINE/AS_IS` | 의미만 재사용 | V2 Python analyzer가 동등 의미를 독립 구현 | 고정 lens는 사용하지 않음 |
| Provider Gateway | `ai/app/providers/structured.py` | OpenAI-compatible strict structured output, 60초 기본 timeout, 오류·retryable 분류 | 예 | LIVE provider가 `execute_structured_prompt` import | API key/Authorization을 trace에 저장하지 않음 |
| Provider 재시도 기준 | `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ProviderRetryPolicy.java` | transient 재시도, 2초/5초 fallback, 최대 15초 Retry-After | Lab에서는 분류만 | V2는 transient/permanent를 구분하고 자동 반복은 하지 않음 | production 이식 시 worker 정책에 맡길 경계 |
| Candidate V2 | `ai/app/tasks/concept_candidate/models.py` (`ConceptCandidateResult`) | 31개 business/value 필드와 31개 `valueSemantics` strict 계약 | 예 | V2 Candidate 내부 canonical 모델로 직접 import | 기존 slot/focus orchestration은 미사용 |
| Business Fingerprint | `ai/app/contracts/concept_fingerprint.py` (`BusinessFingerprint`) | 21개 business mechanics 비교 필드 | 예 | adapter와 distinctness 입력에 직접 import | problem/target만으로 중복 판정하지 않음 |
| 기존 semantic judge | `ai/app/tasks/concept_distinctness_judge/models.py` | 애매한 pair의 `DISTINCT/DUPLICATE` structured 결과 | 선택적 | V2 LIVE의 향후 semantic fallback 경계 | Java 정책 재구현 없음 |
| Legal Fact Pattern | `ai/app/tasks/concept_legal_review/models.py` (`LegalFactPattern`) | 역할·거래·결제·개인정보·물리활동·자격·광고·가설 계약 | 예 | `CurrentLegalAdapter`가 Candidate에서 동일 shape 생성 | SOM은 Legal 입력에서 제외 |
| Full Legal Review | `ai/app/tasks/concept_legal_review/service.py` | 공식 evidence 기반 `IMPLEMENTABLE/.../REJECTED` | 예 | LIVE에서 `execute_concept_legal_review` 직접 호출 | V2 route로 변환 |
| Legal evidence | `ai/app/legal/pipeline.py`, `ai/app/legal/moleg.py`, `ai/app/legal/registry.py` | official law routing/retrieval/screening, MOLEG, registry version | 예 | LIVE Legal task를 통해 간접 재사용 | `MOLEG_API_KEY`, `MOLEG_API_BASE_URL`, `LEGAL_REGISTRY_VERSION` |
| Legal redesign | `ai/app/tasks/concept_redesign/service.py` | 기존 Candidate의 법률 mechanics 보완 | 예 | LIVE redesign adapter가 직접 호출 | V2가 lineage/parent/round를 별도 소유 |
| 7개 hypothesis | `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/HypothesisType.java` | 지역·수익·가격·채널·차별점·SOM share·SOM | 예 | V2에서 같은 7개 key와 confirmation 의미 사용 | 전부 확정돼야 downstream ready |
| hypothesis 확정 의미 | `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionService.java` | Candidate 값/semantics에서 초기화, LOCK 자동 확정, 나머지 사용자 accept/edit | 예 | Lab `build_or_load...`/`confirm_hypotheses` | fake handoff 방지 |
| Market analysis seed | `backend/src/main/java/com/aivle/backend/pipeline/marketseed/application/MarketAnalysisSeedSnapshotFactory.java` | originalSeed, interpretation, selectedConcept, finalHypotheses, legalResult | 예 | `CurrentDownstreamAdapter`가 동일 contract/shape 생성 | `market-analysis-seed-snapshot-v1`, schema `2.0` |
| Marketing source snapshot | `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshotFactory.java` | 마케팅 모듈용 concept/가설/legal 투영 | 예 | 동일 필드명·변환으로 Lab payload 생성 | `marketing-source-snapshot-v1`, schema `2.0` |
| 외부 모듈 handoff | `backend/src/main/java/com/aivle/backend/pipeline/integration/application/ModuleIntegrationService.java` | immutable snapshot 기반 module handoff | 계약 참고 | Lab은 DB 쓰기 없이 snapshot compatibility만 검증 | production 연결은 이번 범위 아님 |

## V2에서 재사용하지 않는 v1 자산

| 자산 | 현재 경로 | 미사용 이유 |
|---|---|---|
| 고정 5 variation focus | `ai/app/tasks/concept_candidate/models.py`의 `VariationFocus` 및 v1 worker 입력 | 열린 design space와 무관한 고정 lens이므로 V2 철학과 충돌 |
| exact-five 완료 정책 | `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryCompletionPolicy.java` | V2는 최대 5이며 유효한 수가 적으면 `READY_LIMITED` 허용 |
| slot 중심 orchestration | `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java` | V2의 plan pool → 검증 → 선택 → 확장 순서와 다름 |
| Java distinctness 결정 | `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/*` | V2 business decision의 canonical owner는 Python Core |
| redesign을 새 slot 후보처럼 다루는 흐름 | 기존 Concept Factory worker/replacement 흐름 | V2는 parent/lineage/redesignRound를 유지하고 parent와 self-duplicate 검사하지 않음 |

## adapter 경계

- LOCAL LAB CONTRACT ADAPTER: MOCK fixture가 현행 Candidate/Legal/downstream shape를 그대로 사용한다.
- LIVE PRODUCTION ADAPTER: 기존 Python Provider·Legal task를 import하되 DB·worker·route를 호출하지 않는다.
- REPLAY ADAPTER: task type + schema version + canonical request hash가 일치하는 기록만 사용하고 miss 시 MOCK fallback을 금지한다.
