# MAIN-FULL-V7 사용자 LIVE 검증

실제 비밀값, API key, prompt, provider raw response는 출력하거나 공유하지 않는다. 유료 실행은 아래 무료 gate가 통과한 뒤 각 기능당 1회만 수행한다.

## 1. 무료 사전검증

저장소 루트:

```powershell
python scripts/check_local_env.py --compose
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

Twin은 다음처럼 보여야 한다.

```text
TWIN_BANK_HOST_DIR       EXISTS
```

`TWIN_BANK_PATH`만 설정했다면 실패와 다음 안내가 나와야 한다.

```text
note: deprecated/unknown TWIN_BANK_PATH detected; rename to TWIN_BANK_HOST_DIR
```

경로값 자체가 출력되면 실패다.

## 2. 공통 로그 관찰

별도 PowerShell 창에서 실행한다.

```powershell
docker compose logs -f --since=2m ai-server backend
```

로그를 공유할 때 bearer token, cookie, request body, prompt, API key를 제거한다.

## 3. TechOps advisory 1회

현재 Market FULL, BM, 확정 TechOps 입력이 있는 프로젝트에서 TechOps 자문을 한 번 실행한다.

최소 확인값:

| 항목 | 기록 |
|---|---|
| taskRunId | |
| attemptId | |
| terminal state | |
| progress | SCALING → EVIDENCE → GENERATING → VALIDATING |
| error code | 없음 |

관련 로그만 추린다.

```powershell
docker compose logs --since=10m ai-server backend | Select-String -Pattern 'TECH_OPS_ADVISORY|SCALING|EVIDENCE|GENERATING|VALIDATING|UNEXPECTED_INTERNAL_ERROR'
```

통과 기준:

- `SafeTaskProgressSender.emit() takes 2 positional arguments but 4 were given` 없음
- TaskRun이 즉시 500으로 끝나지 않음
- progress callback 실패가 advisory 결과를 실패시키지 않음
- programming error가 발생하면 `AI_RESULT_INVALID`가 아니라 `INTERNAL_ERROR`로 분류됨
- 최종 report가 current source lineage에 결속됨

## 4. Marketing create 1회

current Marketing Source가 있는 프로젝트에서 Marketing Content를 한 번 생성한다.

| 항목 | 기록 |
|---|---|
| contentId | |
| taskRunId | |
| initial state | QUEUED |
| terminal state | |
| error code | 없음 |

```powershell
docker compose logs --since=10m backend ai-server | Select-String -Pattern 'MARKETING_CONTENT_GENERATION|CANONICAL_INPUT_HASH_MISMATCH|job.marketing'
```

통과 기준:

- public create가 2xx로 TaskRun을 생성
- `CANONICAL_INPUT_HASH_MISMATCH` 없음
- 내부 request의 `taskSchemaVersion=1.0`
- 동일 idempotency key와 동일 입력은 같은 TaskRun을 반환
- 같은 key에 다른 입력은 idempotency conflict

## 5. Finance provider schema smoke 1회

이 명령은 실제 provider 호출 비용이 발생한다. 승인 후 정확히 한 번만 실행한다.

```powershell
docker compose exec ai-server python -m app.tools.finance_report_provider_smoke
```

성공 출력은 비밀값 없이 다음 세 줄 의미여야 한다.

```text
schemaName=finance_analysis_report_v1
httpCategory=SUCCESS
resultValidation=PASSED
```

실패 시에도 schemaName, 안전한 HTTP category, validation 상태만 보존한다. prompt/raw provider response를 수집하지 않는다.

## 6. Finance Report UI 1회

확정 Finance Snapshot에서 분석 보고서를 한 번 실행한다.

| 항목 | 기록 |
|---|---|
| taskRunId | |
| attemptId | |
| terminal state | |
| report source | `AI_GENERATED_REPORT` 또는 안전한 deterministic fallback |
| providerStatus | |
| safeFailureReason | null 또는 안전한 fallback reason |

```powershell
docker compose logs --since=10m ai-server backend | Select-String -Pattern 'FINANCE_ANALYSIS_REPORT|finance_analysis_report_v1|PROVIDER_RESPONSE_SCHEMA_REJECTED|response_format|job.finance.analysis'
```

통과 기준:

- provider가 strict schema를 수락하거나, provider 장애 시 deterministic fallback이 보존됨
- 성공 결과에 `source=AI_GENERATED_REPORT`, `providerStatus=SUCCEEDED`, `safeFailureReason=null`
- `response_format` 400이 재발하지 않음
- deterministic calculation/Monte Carlo 결과는 보고서 성공 여부와 무관하게 유지됨

## 7. Twin mount 확인

```powershell
docker compose config | Select-String -Pattern 'TWIN_BANK|/app/app/twin/bank' -Context 1,2
docker compose exec ai-server python -c "from app.twin.bank import load; _, rows = load(); print('TWIN_BANK_LOAD=PASSED' if rows else 'TWIN_BANK_LOAD=EMPTY')"
```

실제 path 값이 포함된 출력은 외부 보고서에 붙이지 않는다. 컨테이너 내부 `TWIN_BANK_DIR`과 host `TWIN_BANK_HOST_DIR`의 역할을 혼동하지 않는다.

## 8. 최종 판정 기록

| Gate | PASS/FAIL | taskRunId | error code | 비고 |
|---|---|---|---|---|
| Twin precheck/mount | | N/A | | |
| TechOps advisory | | | | |
| Marketing create | | | | |
| Finance provider smoke | | N/A | | |
| Finance report | | | | |

하나라도 미실행이면 `UNVERIFIED`로 둔다. 실패 시 재실행하기 전에 첫 실행의 taskRunId, attemptId, UTC timestamp와 terminal error를 먼저 보존한다.
