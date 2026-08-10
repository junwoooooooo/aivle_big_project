# Concept Portfolio V2 LIVE Normalization 사용자 검증

## 목적과 예상 소요

실제 Provider strict schema acceptance와 staged Candidate/Legal/downstream 계약을 확인한다. MOCK은 약 1~3분, LIVE는 응답 시간에 따라 약 10~30분을 예상한다. 각 LIVE 단계는 외부 호출과 비용을 발생시킬 수 있다.

## Step 0 — 환경과 Kernel

권장 Python 3.12 venv에서 `ai` 디렉터리를 작업 위치로 Jupyter를 시작한다.

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3\ai
.\.venv\Scripts\Activate.ps1
jupyter lab notebooks\concept_portfolio_v2_lab.ipynb
```

Notebook에서 Kernel Restart를 실행한다. Python 3.14.x는 local smoke일 뿐 production-equivalent가 아니다.

## Step 1 — MOCK Run All

1. `MODE='MOCK'` 유지.
2. Run All.
3. 다음을 확인한다.

- Schema Preflight `ALL PASS`, Provider Calls 0
- One-click MOCK `READY_FULL` 또는 의도된 `READY_LIMITED`
- Market/Marketing `CONTRACT_PASS`
- Provider Usage의 외부 Provider 호출 0
- traceback 없이 마지막 셀까지 완료

## Step 2 — LIVE Schema Preflight만

1. Kernel Restart.
2. `MODE='LIVE'`로 변경.
3. 04 Schema Preflight 셀까지만 실행한다.

성공 기준:

- 네 schema 모두 PASS
- `Provider Calls: 0`
- 외부 요청/비용 없음

FAIL이면 다음 셀을 실행하지 않고 schema name/path/reason을 수집한다.

## Step 3 — Safety와 Seed Analysis

07 Safety와 08~10 analysis 셀까지 실행한다. Safety PASS, source lock/business lock/anchor/open dimension 분리를 확인한다.

## Step 4 — Plan-only LIVE 1회

11 Generate Plan Pool만 실행한 뒤 12 normalization을 확인한다.

성공 기준:

- HTTP 400 `PROVIDER_RESPONSE_SCHEMA_REJECTED` 없음
- Plan Draft 5~7개
- raw Draft에 system-owned dynamic map 요구 없음
- normalized Plan에 결정론적 `P1..Pn`, user locks, anchors 존재
- 고정 lens 없음

실패하면 Candidate 셀을 실행하지 않는다. `show_provider_failure(engine.gateway)`와 Provider Usage를 저장한다.

## Step 5 — Plan Diversity

13~14 실행. 의미상 유사한 Plan이 무조건 DISTINCT가 되지 않고, 명확한 mechanics 차이는 DISTINCT인지 확인한다.

## Step 6 — Candidate 1-only LIVE

15~16만 실행한다.

성공 기준:

- Candidate schema accepted
- value semantics 31개
- user LOCK 값 보존
- EXPLORE/REFINE conceptDefinition은 `CONCEPT_GENERATED/REVIEWABLE`
- anchor와 Plan fidelity PASS

확인 후에만 17~18 remaining Candidates를 실행한다.

## Step 7 — Legal 1-only LIVE

19 precheck 후 20 Full Legal Candidate 1만 실행한다.

확인 항목:

- official evidence reference
- route
- required controls
- required partners/qualifications
- redesign requirements/prohibited variants

확인 후에만 21 remaining Legal을 실행한다. 이 결과는 법률 자문이 아니다.

## Step 8 — Recovery와 Final Portfolio

22~24 실행. Redesign은 동일 lineage/round 1인지, Replan은 다른 Plan에서 full validation 후 Legal로 진입했는지 확인한다. Legal-LOCK 충돌은 `NEEDS_INPUT`이어야 한다.

## Step 9 — 수동 선택·Hypothesis·Delta Legal

25에서 Concept를 수동 선택한다. 26~27에서 7개 hypothesis를 확인한다. LIVE의 `AUTO_CONFIRM_HYPOTHESES`는 False여야 한다. 편집한 법률 민감 hypothesis는 28의 실제 delta legal 완료 전 `PENDING`이어야 한다.

## Step 10 — Downstream 계약

29~31 실행.

성공 기준:

- `market-analysis-seed-snapshot-v1`
- `marketing-source-snapshot-v1`
- `STRUCTURE_PASS`
- `CONTRACT_PASS`
- market legalResult의 `requiredPartnersAndQualifications`
- 7개 final hypotheses

## Step 11 — 진단 보존

32 Trace, 33 Provider Usage, 34 safe failure 상태를 저장한다. API key, Authorization, raw token, MOLEG key 값은 복사하거나 첨부하지 않는다.

## Step 12 — One-click LIVE

Step 2~11이 모두 성공했을 때만 36에서 `RUN_ONE_CLICK_LIVE=True`로 바꾸고 실행한다. `auto_confirm_hypotheses=False`이므로 hypothesis 확인 전 handoff가 pending인 것은 정상이다.

## 실패 시 수집할 정보

- 실패 section 번호와 cell 제목
- exception type과 safe message
- schema name, provider status/type/param
- Trace
- 논리 작업 수와 외부 호출 수
- 입력 fixture에서 비밀이 아닌 field 값

## 다음 단계 진행 조건

Plan schema가 실제 LIVE Provider에서 수용되고, Candidate 1 및 Legal 1 smoke가 성공하며, 수동 hypothesis 확인 후 downstream `CONTRACT_PASS`가 확인되어야 한다. 그 전에는 다음 단계나 production cutover로 진행하지 않는다.
