# Concept Portfolio V2 Lab 실행 안내

## 1. 무엇을 실행하는가

이 Lab은 기존 production Concept Factory를 바꾸지 않고, 격리된 Python V2 Core를 Notebook에서 단계별 또는 한 번에 실행합니다. 기본 `MOCK`은 비용과 DB 없이 전체 상태·Legal recovery·downstream 계약을 검증합니다.

## 2. 요구 환경

- 기준 Python: 3.12 (`ai/Dockerfile` 기준)
- 로컬 검증 환경에서는 Python 3.14.5도 동작 확인
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
5. Handoff에서 `market-analysis-seed-snapshot-v1`, `marketing-source-snapshot-v1`, `compatibility=PASS`를 확인합니다.

단계별 셀은 `analyze_seed`, `plan_portfolio`, `validate_plans`, `expand_plans`, `validate_candidates`, `review_legal`, `resolve_legal`, `build_downstream_handoff` 순서입니다. 마지막에는 `run_full` 전체 실행 셀이 있습니다.

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

## 7. LIVE 실행 전 환경변수

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

그 후 Notebook에서 `MODE = "LIVE"`로 바꾸고 Run All을 실행합니다. 상단에 `LIVE Provider calls enabled`가 표시되고, Trace와 Provider Usage에서 호출 수를 확인할 수 있습니다. Codex 검증에서는 LIVE를 반복 호출하지 않습니다.

## 8. REPLAY 사용

LIVE 성공 응답은 설정된 recordings 디렉터리에 task type, schema version, canonical request hash, redacted request, response, duration, timestamp, provider metadata로 저장됩니다. 비밀키·Authorization header는 저장하지 않습니다.

`MODE = "REPLAY"`로 바꾸면 동일 canonical request hash 기록만 사용합니다. 기록이 없으면 `REPLAY_MISS`로 실패하며 MOCK으로 대체하지 않습니다.

## 9. 결과와 로그 읽기

- `READY_FULL`: 요청 최대치까지 유효 Concept 확보
- `READY_LIMITED`: 유효한 대안만 반환해 최대치 미만
- `NEEDS_INPUT`: 사용자 LOCK, Legal 또는 필수 입력 결정 필요
- `FAILED`: Provider/schema/replay/system 실패
- `PLAN_DUPLICATE`: problem/target이 아니라 business mechanics 중복
- `LEGAL_REDESIGN_REQUIRED`: 같은 lineage 안에서 최소 mechanics 보완
- `LEGAL_REPLAN_REQUIRED`: 실패 plan을 다른 plan으로 교체
- Trace에는 chain-of-thought, 키, 토큰, Authorization header가 포함되지 않습니다.

`Structural risk precheck — not final legal review`는 빠른 구조 위험 표시이며 최종 법률검토가 아닙니다. LIVE Legal 결과도 법률 자문이 아닙니다.

## 10. 흔한 오류

- `ModuleNotFoundError`: 반드시 `ai`에서 Jupyter를 시작하고 `requirements-dev.txt`를 설치합니다.
- `AI_CONFIGURATION_INVALID`: `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`을 확인합니다.
- Provider auth/rate limit: Provider 계정과 `retryable`, `retryAfterMs`를 확인합니다.
- `REPLAY_MISS`: 입력을 원래 기록과 같게 하거나 LIVE에서 새 기록을 만듭니다.
- `MOLEG_AUTHENTICATION_FAILED`: `MOLEG_API_KEY`를 확인합니다.
- `LEGAL_REGISTRY_VERSION_MISMATCH`: `LEGAL_REGISTRY_VERSION=legal-registry-v1`인지 확인합니다.
- `LEGAL_SOURCE_EVIDENCE_UNAVAILABLE`: MOLEG 연결과 공식 근거 조회 결과를 확인합니다.
- Handoff `FAIL`: 7개 hypothesis가 모두 `ACCEPTED` 또는 `USER_EDITED_ACCEPTED`인지 확인합니다.

## 11. Colab

1차 지원 환경은 repository local Jupyter입니다. Colab에서는 저장소를 clone하거나 Drive에 mount한 뒤 `ai`를 Python path에 추가하고 `requirements-dev.txt`를 설치합니다. Colab 전용 코드는 Core에 포함하지 않았습니다.

## 12. 절대 하지 말아야 할 것

- DB volume이나 migration을 삭제·변경하지 않습니다.
- 기존 Concept Factory route/worker/UI를 V2로 교체하지 않습니다.
- 비밀키를 Notebook 출력·recording·git에 저장하지 않습니다.
- MOCK 성공을 LIVE Legal/Provider acceptance로 해석하지 않습니다.
