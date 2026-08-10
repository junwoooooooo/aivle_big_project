# Concept Portfolio V2 Lab 실행 안내

## 1. 무엇을 실행하는가

이 Lab은 기존 production Concept Factory를 바꾸지 않고, 향후 AI Server가 그대로 import할 generic Python V2 Core를 Notebook에서 단계별 또는 한 번에 실행합니다. 기본 `MOCK`은 비용과 DB 없이 adaptive planning, Concept Family/Variant, Legal recovery, downstream 계약을 검증합니다.

## 2. 요구 환경

- 권장·production-equivalent Python: 3.12 (`ai/Dockerfile` 기준)
- 사용자 로컬 smoke: Python 3.14.x도 동작할 수 있지만 production-equivalent가 아닙니다.
- Pydantic/httpx/Jupyter 및 Provider 동작 차이를 줄이려면 Python 3.12 venv를 권장합니다.
- 작업 시작 위치: 저장소의 `ai` 디렉터리

## 3. 가상환경과 dependency 설치

Windows PowerShell:

```powershell
cd ai
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
```

Mac/Linux:

```bash
cd ai
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements-dev.txt
```

## 4. Jupyter 실행과 Notebook 열기

```powershell
jupyter lab notebooks/concept_portfolio_v2_lab.ipynb
```

또는:

```powershell
jupyter notebook notebooks/concept_portfolio_v2_lab.ipynb
```

Notebook 경로는 `ai/notebooks/concept_portfolio_v2_lab.ipynb`입니다.

## 5. MOCK부터 실행

1. Notebook 상단의 `MODE = "MOCK"`를 유지합니다.
2. Kernel Restart 후 Run All을 실행합니다.
3. `RUN SUMMARY`에서 `READY_FULL` 또는 의도한 `READY_LIMITED/NEEDS_INPUT`을 확인합니다.
4. Final Portfolio, Plan/Candidate pairwise 표, Legal 결과, Trace를 확인합니다.
5. Handoff에서 `market-analysis-seed-snapshot-v1`, `marketing-source-snapshot-v1`, `CONTRACT_PASS`를 확인합니다.

단계별 셀은 00~46으로 고정되어 있습니다. Idea Brief 1회 파생 결과와 generic OpportunityKernel을 먼저 확인하고, adaptive Plan pool, Family/Variant/Distinct, actual Candidate descriptor, Legal C1, 나머지 Legal, 수동 hypothesis, 실제 Delta Legal, downstream 순으로 실행합니다.

## 6. 입력 변경

Notebook의 `TEST_INPUT` 셀만 바꿉니다. 필수 필드는 `ideaOverview`, `problem`, `targetUsers`입니다. 선택 필드는 `targetRegion`, `knownCompetitors`, `revenueModel`, `price`, `channels`, `differentiators`, `budgetConstraint`, `teamConstraint`, `timelineConstraint`, `otherConstraint`입니다.

LOCK 예:

```python
"price": {
    "value": "월 19,900원",
    "decisionState": "LOCKED",
    "source": "USER_INPUT",
}
```

## 7. LIVE 실행 전 환경변수와 필수 실행 순서

LIVE는 AI Provider 및 Legal evidence 외부 호출 비용·제약이 있습니다. 키 값은 문서나 Notebook에 기록하지 마십시오.

```powershell
$env:AI_PROVIDER = "openai"
$env:AI_API_KEY = "<설정한 비밀키>"
$env:AI_MODEL = "<사용 모델>"
$env:AI_BASE_URL = "https://api.openai.com/v1"  # openai은 생략 가능
$env:AI_PROVIDER_TIMEOUT_SECONDS = "60"
$env:MOLEG_API_KEY = "<법제처 Open API 키>"
$env:MOLEG_API_BASE_URL = "https://www.law.go.kr/DRF"
$env:LEGAL_REGISTRY_VERSION = "legal-registry-v1"
```

환경변수 설정 후 Kernel을 Restart합니다. LIVE 첫 단계는 반드시 **Schema Preflight만 실행**하는 것입니다. 이 단계의 기대값은 `ALL PASS`, `Provider Calls=0`입니다.

비용과 실패 범위를 통제하기 위해 다음 순서를 지킵니다.

1. `MODE = "MOCK"`로 Run All하고 `CONTRACT_PASS`를 확인합니다.
2. `MODE = "LIVE"`로 바꾼 뒤 Schema Preflight까지만 실행합니다.
3. Safety와 Seed Analysis를 실행합니다.
4. Plan pool LIVE 1회만 실행하고 Draft/정규화 Plan 및 locks를 확인합니다.
5. Portfolio Family와 `DUPLICATE/VARIANT/DISTINCT` 관계, adaptive replenishment 여부를 확인합니다. VARIANT는 정상 후보입니다.
6. Candidate 1-only smoke를 실행해 31개 semantics, user lock, provenance와 actual descriptor를 확인합니다. Fidelity 최종 판정은 전체 Candidate Recovery에서 수행합니다.
7. 확인 후 remaining Candidates를 생성합니다.
8. Candidate Recovery에서 초기 accepted 수, semantic fidelity, targeted regeneration, reserve 활성화, replenishment 및 최종 Candidate 수를 확인합니다.
9. C1 Legal Fact Pattern의 역할·거래·결제·개인정보·물리 활동·파트너·자격·광고 필드를 확인합니다.
10. `RUN_FULL_LEGAL_C1=True`로 C1 route, production status, controls, qualifications, disclosures, evidence와 diagnostics를 확인합니다.
11. `RUN_REMAINING_LEGAL=True`로 나머지 Legal을 실행합니다.
12. 필요 시 Redesign/Replan을 실행하고 Final Portfolio를 확인합니다.
13. Concept를 수동 선택하고 7개 hypothesis를 확인합니다.
14. 필요한 경우 Delta Legal을 실행합니다.
15. Market/Marketing handoff의 `CONTRACT_PASS`를 확인합니다.
16. One-click LIVE Run All은 위 단계가 모두 성공한 뒤 마지막에만 사용합니다.

`auto_confirm_hypotheses=True`는 **Lab shortcut**입니다. MOCK 자동 회귀를 위한 편의 기능일 뿐 사용자 확인이나 production 결정을 뜻하지 않습니다. Core 기본값과 Notebook의 `CONFIRM_ALL_PROPOSED`는 모두 `False`입니다. 사용자가 이를 `True`로 바꾸거나 `HYPOTHESIS_EDITS`를 입력해야 확정됩니다. 법률 민감 hypothesis를 편집하면 `RUN_DELTA_LEGAL=True`로 실제 `review_delta_legal`을 완료하기 전까지 downstream 계약은 실패합니다.

상단에는 LIVE 외부 작업 활성 여부가 표시됩니다. Notebook과 production entrypoint는 동일 `ConceptPortfolioEngine`과 `prepare_portfolio_plans`를 사용합니다. MODE는 Gateway 구현만 바꾸며 Portfolio 정책을 바꾸지 않습니다. Provider Usage의 `topLevelExternalOperations`는 orchestration 상위 작업 수이며, Legal 내부 네트워크 호출 수와 같다고 해석하지 않습니다.

## 8. REPLAY 사용

LIVE 성공 응답은 설정된 recordings 디렉터리에 operation, operationVersion, promptVersion, schemaVersion, canonicalInputHash, canonical request hash, redacted request, response, duration, timestamp, provider metadata로 저장됩니다. 비밀키·Authorization header는 저장하지 않습니다. Idea Brief 파생 결과도 동일 계약에 포함됩니다.

`MODE = "REPLAY"`로 바꾸면 현재 입력과 모든 버전이 일치하는 기록만 사용합니다. 프롬프트 버전이 바뀌거나 기록이 없으면 `REPLAY_MISS`로 실패하며 MOCK으로 대체하지 않습니다. 43번 셀은 현재 디렉터리를 `REPLAY_READY`, `REPLAY_PARTIAL`, `REPLAY_MISS`로 표시합니다.

## 9. 결과와 로그 읽기

- `READY_FULL`: 요청 최대치까지 유효 Concept 확보
- `READY_LIMITED`: 유효한 대안만 반환해 최대치 미만
- `NEEDS_INPUT`: 사용자 LOCK, Legal 또는 필수 입력 결정 필요
- `FAILED`: Provider/schema/replay/system 실패
- `PLAN_DUPLICATE`: Concept Thesis와 Business Architecture가 사실상 동일
- `VARIANT`: 같은 Family/Architecture를 공유하지만 target/use/value/offer가 의미 있게 다른 정상 후보
- `DISTINCT`: solution 또는 주요 Architecture 선택이 다른 정상 후보
- `OUT_OF_SCOPE`: OpportunityKernel을 실제로 벗어나 제외된 Plan/Candidate
- `RECOVERABLE_FIDELITY_FAILURE`: 동일 Plan targeted regeneration 1회 대상
- Candidate fidelity `AMBIGUOUS`: 바로 탈락하지 않고 semantic fidelity로 판정
- `LEGAL_REDESIGN_REQUIRED`: 같은 lineage 안에서 최소 mechanics 보완
- `LEGAL_REPLAN_REQUIRED`: 실패 plan을 다른 plan으로 교체
- Trace에는 chain-of-thought, 키, 토큰, Authorization header가 포함되지 않습니다.

`Structural risk precheck — not final legal review`는 빠른 구조 위험 표시이며 최종 법률검토가 아닙니다. LIVE Legal 결과도 법률 자문이 아닙니다.

## 10. 흔한 오류

- `ModuleNotFoundError`: 반드시 `ai`에서 Jupyter를 시작하고 `requirements-dev.txt`를 설치합니다.
- `AI_CONFIGURATION_INVALID`: `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`을 확인합니다.
- Provider auth/rate limit: Provider 계정과 `retryable`, `retryAfterMs`를 확인합니다.
- `SCHEMA_PREFLIGHT_FAILED`: 표시된 schema path/reason을 수정하기 전 Plan/Candidate 셀을 계속 실행하지 않습니다.
- `PROVIDER_RESPONSE_SCHEMA_REJECTED`: Plan은 생성되지 않았습니다. safe provider diagnostic의 schema name/type/param을 확인하고 Candidate 셀을 실행하지 않습니다.
- `REPLAY_MISS`: 입력을 원래 기록과 같게 하거나 LIVE에서 새 기록을 만듭니다.
- `MOLEG_AUTHENTICATION_FAILED`: `MOLEG_API_KEY`를 확인합니다.
- `LEGAL_REGISTRY_VERSION_MISMATCH`: `LEGAL_REGISTRY_VERSION=legal-registry-v1`인지 확인합니다.
- `LEGAL_SOURCE_EVIDENCE_UNAVAILABLE`: MOLEG 연결과 공식 근거 조회 결과를 확인합니다.
- `LEGAL_EVIDENCE_BINDING_REPAIR_FAILED`: 표에 표시된 allowed/invalid index를 확인하고 Legal 계약 수정 후 C1부터 재실행합니다.
- Handoff `FAIL`: 7개 hypothesis가 모두 `ACCEPTED` 또는 `USER_EDITED_ACCEPTED`인지 확인합니다.

## 11. Generic canonicalization과 adaptive planning

- Provider Plan draft는 canonical code나 Family를 생성하지 않습니다.
- System-owned `GenericConceptNormalizer`가 Plan과 actual Candidate를 같은 `ConceptThesis + BusinessArchitecture` 계약으로 정규화합니다.
- 규칙 근거가 없는 Architecture 축은 직접운영 같은 기본값으로 추정하지 않고 `OTHER/LOW`로 유지하며 필요한 항목만 batch semantic classifier를 사용합니다.
- 같은 Family 최대 2개는 선택 preference이며 hard reject가 아닙니다.
- Plan은 base quality와 현재 Portfolio에 대한 marginal coverage를 결합한 greedy score로 선택되며 생성 순서는 품질 기준이 아닙니다.
- 초기 validation 후 최대치가 부족하면 제한된 round에서 새 Thesis 또는 Architecture Plan을 보충합니다.
- Candidate 실패는 fidelity regeneration → reserve Plan → adaptive Plan replenishment 순으로 복구합니다.
- 의미 있는 후보가 더 없으면 5개를 억지로 만들지 않고 `READY_LIMITED`로 종료합니다.
- 관계 기반 Plan reject는 `DUPLICATE`, `OUT_OF_SCOPE`, `LOCK_CONFLICT`입니다.

## 12. Colab

1차 지원 환경은 repository local Jupyter입니다. Colab에서는 저장소를 clone하거나 Drive에 mount한 뒤 `ai`를 Python path에 추가하고 `requirements-dev.txt`를 설치합니다. Colab 전용 코드는 Core에 포함하지 않았습니다.

## 13. 절대 하지 말아야 할 것

- DB volume이나 migration을 삭제·변경하지 않습니다.
- 기존 Concept Factory route/worker/UI를 V2로 교체하지 않습니다.
- 비밀키를 Notebook 출력·recording·git에 저장하지 않습니다.
- MOCK 성공을 LIVE Legal/Provider acceptance로 해석하지 않습니다.
