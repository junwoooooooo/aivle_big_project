# Concept Portfolio V2 마지막 차단 문제 수정 및 Core 동결 판정 결과

## [현재 상태]

- 마지막 차단 문제 수정: 완료
- 정적/적대적/회귀 테스트: 완료
- 사용자 LIVE: 대기
- Core 동결: 대기

구현과 비-LIVE 검증은 완료했다. 실제 AI Provider와 MOLEG LIVE는 지시대로 실행하지 않았다.

## [사용자 실제 오류 기준선]

1. B2B LIVE에서 요청한 `C4 BUSINESS_PARTNER`가 존재해도 같은 C4의 `PERSONAL_DATA`, `PHYSICAL_ACTIVITY`가 추가 반환되면 `LEGAL_FACT_DEPENDENCY_BATCH_IDENTITY_MISMATCH`로 전체 batch가 실패했다.
2. 단계별 FULL_E2E Notebook은 C1 단일 Legal 호출이 기술 실패하면 C1 terminal 상태를 만들지 못해 C2 이후 검토 조건까지 막았다.
3. “판매 주체 확인 필요”, “자격 여부 확인 필요” 같은 사실 질문이 사업구조 변경 요구와 같은 redesign route로 처리될 수 있었다.

## [수정한 3개 Blocker]

1. 같은 요청 Candidate의 지원되는 추가 판정은 사용하지 않고 폐기하며 batch를 계속하도록 수정했다.
2. Notebook C1 Legal도 Engine의 후보 안전 batch 경계인 `review_legal()`을 사용하도록 바꿨다.
3. Legal 요구의 성격을 `FACT_REQUIRED / STRUCTURAL_CHANGE / AMBIGUOUS`로 분류하고, 구조 변경 invariant가 없는 질문은 후보 단위 `NEEDS_INPUT`으로 보수 처리했다.

## [Safe Extra Batch 정책]

- 요청 결과 누락: 실패
- 동일 key 중복: 실패
- 요청 Candidate 집합 밖 ID: 실패
- 지원하지 않는 종류/schema 위반: 실패
- 같은 요청 Candidate의 지원되는 요청 외 key: 폐기 후 계속
- 폐기 결과는 반환값, dependency/role 상태, Candidate, Legal 입력을 덮어쓰지 않는다.
- 진단은 Provider 사용 정보와 Engine trace에 `BATCH_EXTRA_RESULTS_IGNORED`, `ignoredKeys`, 한국어 안전 요약으로 남긴다.

정확한 LIVE 재현 fixture인 `C4 BUSINESS_PARTNER + C4 PERSONAL_DATA + C4 PHYSICAL_ACTIVITY`에서 요청한 `BUSINESS_PARTNER` 하나만 반환되는 것을 고정했다. 같은 정책은 Business Role batch에만 적용했다. Hypothesis와 Architecture batch는 고유 key 계약 특성상 요청 외 결과를 계속 실패시킨다.

## [Candidate Legal Failure Isolation]

- C1 후보의 비공통 Legal 계약 오류는 `SYSTEM_FAILURE`로 기록한다.
- C2, C3, C4는 계속 검토한다.
- 인증/설정/Registry/모델 의존성/Legal source 전체 장애는 기존 global failure로 즉시 중단한다.
- 동일 계약 오류가 여러 후보에서 반복되면 기존 공통 장애 승격 정책을 유지한다.
- Notebook의 C1 단계는 `review_legal_candidate()` 직접 호출 대신 `review_legal(seed, candidates[:1])`을 사용한다. 후속 remaining Legal도 같은 Engine API를 사용하므로 Notebook 전용 business policy는 추가하지 않았다.

## [Legal Fact vs Structural Change]

- `FACT_REQUIRED`: 현재 판매 주체, 자격 보유 여부, 실제 계약 여부 같은 사실 확인 요구. `NEEDS_INPUT`으로 전환하고 구체 질문을 남긴다.
- `STRUCTURAL_CHANGE`: 변경 대상 field/mechanism, 현재 값, 요구 구조, 명시적 변경 동작이 모두 있을 때만 redesign을 허용한다.
- `AMBIGUOUS`: 법률 결론을 추론하지 않고 후보 단위 `NEEDS_INPUT`으로 보수 처리한다.
- `ACCEPT` 및 다른 Legal 결론은 이 분류로 변경하지 않는다.
- Provider의 원래 설명과 production status는 보존하고, 요구 성격과 질문을 진단에 덧붙인다.

## [수정하지 않은 동결 영역]

Idea Brief, Safety, Opportunity Kernel, Planning/Selection, Portfolio 관계 정책, Candidate 생성/Fidelity/Recovery, Architecture 및 Business Role 의미 정책, Legal Fact Completion/정합성 기본 구조, Official Legal/MOLEG/Evidence, redesign compliance/replan 기본 구조, 7 Hypothesis, 사용자 확인, Delta Legal, Snapshot/Handoff, `run_full()`의 전체 orchestration은 변경하지 않았다.

추가로 발견 가능한 표현·다양성·`OTHER`·자동 보완 성공률 문제는 production blocker가 아니므로 이번 범위에서 다루지 않았다.

## [Adversarial Test]

- 응답 순서 변경: 통과
- 같은 Candidate의 지원되는 추가 dependency/role: 폐기 + 통과
- 누락: 실패
- 중복: 실패
- 다른 Candidate ID: 실패
- 지원하지 않는 dependency: 실패
- Hypothesis/Architecture extra: 계속 실패
- C1 Legal 계약 오류: C2~C4 계속
- Legal source 전체 장애: global failure
- 판매 방식/판매 주체/자격 확인 문장: `FACT_REQUIRED → NEEDS_INPUT`
- 직접 판매 제거 및 자격 보유 외부 판매자로 계약 주체 변경: `STRUCTURAL_CHANGE → REDESIGN_WITHIN_LINEAGE`

## [기존 성공 회귀]

MOCK `run_full()` 및 Handoff 회귀에서 다음 5개 Scenario가 모두 사용 가능한 Portfolio와 `CONTRACT_PASS`를 유지했다.

- `FOOD_PHYSICAL_COMMERCE`
- `WEEKEND_TRIP_PLANNER`
- `B2B_AI_SALES_ASSISTANT`
- `OFFICE_EQUIPMENT_SUBSCRIPTION`
- `CAMPUS_SECONDHAND`

Legal evidence, Hypothesis 의미 검증, Handoff, production entrypoint 계약도 Concept Portfolio 전체 targeted 묶음에서 통과했다.

## [테스트 결과]

- `python -m compileall -q app`: PASS
- 마지막 blocker 집중 테스트: PASS
- Concept Portfolio 전체 targeted + Legal evidence: PASS, 267개
- production entrypoint smoke: PASS
- Notebook JSON parse: PASS
- Notebook code cell compile: PASS, 47개
- `git diff --check`: PASS

테스트 개수는 종료 조건으로 사용하지 않았다. 267개는 실제 LIVE 실패 패턴과 기존 계약 회귀를 함께 실행한 결과일 뿐, 숫자 자체를 완료 기준으로 삼지 않았다.

## [사용자 재검증 대상]

1. `B2B_AI_SALES_ASSISTANT`: fresh kernel FULL_E2E를 가능하면 2~3회 실행해 같은 Candidate의 safe extra가 전체 실패를 만들지 않는지 확인한다.
2. `OFFICE_EQUIPMENT_SUBSCRIPTION`: C1 Legal 기술 오류가 발생해도 C2 이후 결과가 계속 표시되는지 확인한다.
3. `CAMPUS_SECONDHAND`: “확인 필요” 요구가 `NEEDS_INPUT`으로 남고 redesign 반복으로 진입하지 않는지 확인한다.
4. 위 FULL_E2E가 안정되면 B2B/Travel/Food 중 하나만 ONE_CLICK `run_full()` LIVE smoke로 확인한다.

Food와 Travel은 최신 FULL_E2E Handoff 성공 기준선이므로 새 회귀 징후가 없다면 우선 반복하지 않는다.

## [Core Freeze 조건]

- safe extra 무작위 실패 제거
- 실제 누락/중복/잘못된 ID/unsupported key 차단 유지
- 후보 Legal 기술 오류 격리와 공통 장애 중단 모두 확인
- 사실 질문과 구조 변경 구분 확인
- 서로 다른 최소 3개 Domain의 현재 버전 Handoff 성공
- 마지막 production `run_full()` LIVE smoke 성공

## [현재 Freeze 가능 여부]

아직 동결 선언 전이다. 구현·정적·적대적·MOCK 회귀는 완료했지만, 사용자 B2B/Office/Campus FULL_E2E와 마지막 LIVE `run_full()` smoke가 남았다. 이 조건이 통과하면 Concept Portfolio V2 Core를 `FROZEN`으로 선언할 수 있다.

## [다음 단계]

사용자 LIVE 검증 통과 후 Core를 동결하고 Production Workflow, Backend service, DB 저장 모델, Frontend Workflow, Market Analysis 및 Marketing 연결 단계로 이동한다. 실패가 있으면 전면 개선하지 않고 production blocker만 분리해 수정한다.

## [수정 파일]

- `ai/app/concept_portfolio_v2/models.py`
- `ai/app/concept_portfolio_v2/providers.py`
- `ai/app/concept_portfolio_v2/engine.py`
- `ai/app/concept_portfolio_v2/legal_requirement_nature.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb` — 기존 사용자 출력 보존, C1 Legal source 한 줄만 변경
- `ai/tests/concept_portfolio_v2/test_final_blocker_fix_and_core_freeze.py`
- 본 결과 문서와 progress/verification 문서

사용자가 생성한 recordings, Notebook checkpoint/Scenario 사본, 삭제 상태 파일은 변경·복원·정리하지 않았다.

## [git diff --stat]

추적된 Core 파일과 canonical Notebook에 대한 명령 출력은 다음과 같다.

```text
ai/app/concept_portfolio_v2/engine.py       |   21 +-
ai/app/concept_portfolio_v2/models.py       |   16 +
ai/app/concept_portfolio_v2/providers.py    |   52 +-
ai/notebooks/concept_portfolio_v2_lab.ipynb | 5492 +++++++++++----------------
4 files changed, 2385 insertions(+), 3196 deletions(-)
```

Notebook 수치는 작업 전부터 존재한 사용자 LIVE output diff 때문에 크다. 이번 작업은 그 파일에서 C1 Legal 호출 source 한 줄만 변경했다. 위 stat은 아직 untracked인 신규 `legal_requirement_nature.py` 130줄, adversarial test 235줄, 문서 3개를 포함하지 않는다. recordings, checkpoint, Scenario Notebook 사본의 기존 변경은 본 작업 변경이 아니다.
