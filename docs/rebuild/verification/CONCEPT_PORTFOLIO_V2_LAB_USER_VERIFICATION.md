# Concept Portfolio V2 Lab 사용자 검증

## 1. 준비

저장소 루트에서:

```powershell
cd ai
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
```

예상 시간: 기존 venv가 있으면 1~5분.

성공 기준: 설치가 오류 없이 끝나고 `jupyter --version`이 출력된다.

## 2. targeted test

```powershell
python -m pytest -q tests\concept_portfolio_v2\test_engine.py
```

예상 시간: 1분 미만.

성공 기준: `23 passed`.

## 3. fresh-kernel MOCK Run All

```powershell
jupyter nbconvert --to notebook --execute notebooks\concept_portfolio_v2_lab.ipynb `
  --output concept_portfolio_v2_lab.executed.ipynb `
  --ExecutePreprocessor.timeout=300
```

예상 시간: 1~3분.

성공 기준: 명령 exit code 0, executed Notebook 생성, `READY_FULL`, `Final Portfolio=5`, downstream `PASS`, 마지막 runtime stage `READY`.

실패 시 수집:

- 실패 cell 번호와 traceback
- `python --version`
- `python -m pip show pydantic httpx jupyterlab nbconvert nbformat pandas`
- 비밀키를 제거한 Trace/Provider Usage

## 4. Notebook 수동 관찰

```powershell
jupyter lab notebooks\concept_portfolio_v2_lab.ipynb
```

확인할 항목:

1. `MODE="MOCK"`에서 Restart Kernel → Run All 성공
2. Seed 표에 required/optional/LOCK 표시
3. Design Space에 anchor/lock/open 분리
4. Plan·Candidate pairwise 표에 business mechanics 차이 표시
5. `Structural risk precheck — not final legal review` 문구
6. Legal redesign fixture에서 `C1 → C1-R1`, 같은 `lineageId`, `redesignRound=1`
7. Legal replan fixture에서 `P1~P5`와 다른 replacement plan
8. lock conflict fixture에서 `NEEDS_INPUT`과 5개 conflict 필드
9. Handoff에 7개 final hypothesis, 두 production contract, field mapping, `PASS`
10. Trace에 secret/Authorization/chain-of-thought가 없음

## 5. LIVE 1회 acceptance

비밀키 값은 기록하거나 공유하지 않는다.

```powershell
$env:AI_PROVIDER = "openai"
$env:AI_API_KEY = "<비밀키>"
$env:AI_MODEL = "<사용 모델>"
$env:AI_PROVIDER_TIMEOUT_SECONDS = "60"
$env:MOLEG_API_KEY = "<법제처 키>"
$env:MOLEG_API_BASE_URL = "https://www.law.go.kr/DRF"
$env:LEGAL_REGISTRY_VERSION = "legal-registry-v1"
jupyter lab notebooks\concept_portfolio_v2_lab.ipynb
```

Notebook에서 `MODE="LIVE"`로 바꾼 뒤 food_minimal 한 번만 Run All한다.

성공 기준:

- `LIVE Provider calls enabled`
- Safety PASS
- Plan/Candidate strict schema PASS
- Legal source가 official evidence를 반환하거나 명확한 `NEEDS_INPUT`
- 유효 final portfolio가 max 5 이하
- 7개 hypothesis 확정 후 downstream compatibility PASS
- Provider Usage 호출 수·retry·duration 표시
- recording에 API key/Authorization이 없음

실패 시 수집:

- safe error code/reason/retryable/retryAfterMs
- 실패 stage의 필터 Trace
- Provider schema name
- Legal source status/warnings
- 비밀을 제거한 recording metadata

## 6. REPLAY

LIVE 성공 기록 생성 후 `MODE="REPLAY"`로 같은 입력을 Run All한다.

성공 기준: LIVE와 동일 structured 결과이며 외부 Provider 호출이 없다. 입력을 바꾸면 `REPLAY_MISS`가 명확히 나타나야 한다.

## 7. 다음 단계 진행 조건

- targeted 23개 PASS
- MOCK fresh-kernel Run All PASS
- LIVE 1회에서 Provider schema와 official Legal 경로 확인
- downstream compatibility PASS
- known risk를 승인

이 조건 전에는 production route/worker/DB/Frontend를 V2로 전환하지 않는다.
