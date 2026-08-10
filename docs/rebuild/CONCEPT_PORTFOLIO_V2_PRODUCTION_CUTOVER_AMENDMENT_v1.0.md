# Concept Portfolio V2 Production Cutover Amendment v1.0

## 1. 지위와 적용 범위

이 문서는 Concept Portfolio V2 Production Cutover 범위에 한정한 최상위 Amendment다. 다음 영역에서 기존 `docs/rebuild` 계약과 충돌하면 이 문서가 우선한다.

- Confirmed Idea Brief 이후 Concept Portfolio 단계
- Concept 생성과 법률검토
- Portfolio 결과 공개와 제품 상태
- Concept 비교와 사용자 선택
- Candidate 단위 `NEEDS_INPUT`과 사용자 추가정보 continuation
- 선택 Concept의 7개 Hypothesis와 Delta Legal
- 최종 Legal Regulatory Report
- `MarketAnalysisSeedSnapshot` handoff
- 위 영역의 비동기 실행, 이벤트, UI 상태

그 밖의 프로젝트 단계에는 기존 rebuild 문서의 우선순위를 그대로 적용한다. 기존 문서의 충돌 문장은 Product Cutover cleanup에서 일괄 정리한다.

## 2. Concept 개수와 Portfolio 완료

- 요청 최대치는 `maxConcepts=5`다.
- 5는 목표이자 최대치이며 성공조건이 아니다.
- 법률검토를 통과한 Concept 1~5개는 모두 정상 Portfolio 결과다.
- `READY_FULL`은 요청 최대치를 충족한 정상 결과다.
- `READY_LIMITED`는 유효 Concept가 있으나 요청 최대치보다 적은 정상 결과다.
- 정확히 5개 확보 전 공개 금지, 5개 미만 실패, 5개가 있어야 다음 단계 진행, 빈 Slot 채우기, 5개 전부 준비 후 동시 공개 규칙은 이 범위에서 폐기한다.
- 적격 Concept가 하나라도 있으면 다른 후보의 실패나 입력 대기와 무관하게 공개하고 다음 행동을 허용한다.

## 3. 비교와 선택

- 전체 Portfolio는 1~5개 Concept를 표시할 수 있다.
- 비교는 선택사항이며 최소 2개, 최대 3개다.
- Concept가 1개면 비교 없이 직접 선택할 수 있다.
- Product의 current selection은 사용자가 명시적 선택 API를 호출한 뒤에만 생성한다.
- `ConceptPortfolioEngine.run_full()` 결과의 내부 `selectedConceptId` 또는 `concepts[0]`은 사용자 선택 authority가 아니다.

## 4. Candidate 단위 NEEDS_INPUT

AI가 설계해야 할 구조 누락과 사용자만 아는 실제 사업 사실의 누락을 구분한다.

- Concept 설계 자체의 누락은 기존처럼 자동 completion, redesign 또는 rejection 대상이다.
- 실제 사업 사실이 없어 Legal 판단을 이어갈 수 없으면 `NEEDS_INPUT`은 정상적인 actionable Product 상태가 될 수 있다.
- 실제 사업 사실에는 판매 주체, 서비스 제공 주체, 거래 방식, 결제·수취 구조, 개인정보 이용, 실제 파트너·자격, 실제 물리활동이 포함될 수 있다.
- Candidate 입력 요청은 Candidate와 lineage에 귀속하며 다른 적격 Concept를 차단하지 않는다.
- 예를 들어 `2 ACCEPT + 1 NEEDS_INPUT`이면 ACCEPT 2개는 즉시 비교·선택할 수 있고, 나머지 1개는 별도 continuation으로 이어간다.
- `0 ACCEPT + actionable Candidate input + system failure 없음`이면 Raw Engine status가 `FAILED`여도 Product 상태는 `NEEDS_INPUT`으로 매핑할 수 있다.
- Provider, Schema, Registry, persistence, TaskRun, ownership/auth 등 실제 기술 실패는 `FAILED`로 유지한다.

## 5. Continuation과 비동기 이력

기존 비동기 terminal immutability 원칙을 유지한다.

1. 사용자 답변을 새 Input Response로 저장한다.
2. 새 continuation TaskRun과 새 Job을 만든다.
3. 동일 Candidate lineage의 저장된 continuation 상태에서 검토를 이어간다.
4. 필요한 Candidate validation과 Legal Review 후 Portfolio 정본을 갱신한다.

`NEEDS_INPUT`으로 종료된 과거 TaskRun이나 Job을 다시 `RUNNING`으로 바꾸지 않는다. 과거 실행은 immutable history로 보존하며, 사용자 확정 사실은 후속 AI가 임의로 변경하지 않는다.

## 6. Core Frozen과 Production Integration 경계

`ai/app/concept_portfolio_v2/**`의 알고리즘은 FROZEN이다. Production 이식은 Product Integration Layer에서 수행한다.

필요한 경우 다음 비의미적 seam만 허용한다.

- read-only trace observer
- continuation DTO export
- serialization/restoration seam
- Core 메서드를 호출하는 thin production facade

Planning, candidate policy, provider prompt, selection 정책, Legal 판단과 알고리즘 결과의 의미는 변경하지 않는다.

## 7. Hypothesis, Delta Legal과 Market handoff

- 사용자 선택 이후 기존 7개 Hypothesis를 확인·확정한다.
- 법률 민감 가설 변경에는 필요한 Delta Legal을 수행한다.
- 최종 Legal Regulatory Report는 선택 Concept의 Legal 결과, 사용자 확정 Hypothesis, Delta Legal, Official Evidence를 materialize한 Product resource다.
- 시장분석의 유일한 canonical input은 Confirmed Idea Brief, Selected Concept, 사용자 확정 7개 Hypothesis, 필요한 Delta Legal, 최종 Legal Result, Official Evidence를 묶은 immutable `MarketAnalysisSeedSnapshot`이다.
- stale Legal Report나 stale Market Seed로 후속 시장분석을 시작하지 않는다.

## 8. 유지되는 공통 원칙

- durable `TaskRun`, `JobEvent`, SSE, replay, polling fallback과 terminal immutability
- 사용자 소유권과 프로젝트 경계
- Query API가 화면 상태의 정본이고 Event는 갱신 신호라는 원칙
- immutable Snapshot과 history
- Core 알고리즘 비재구현
- 사용자 화면에 raw Provider 오류·영문 질문·내부 기술 상태를 직접 노출하지 않는 원칙
