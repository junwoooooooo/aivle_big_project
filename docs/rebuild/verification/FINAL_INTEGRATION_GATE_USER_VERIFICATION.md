# Final Integration Gate User Verification

## Current status

- Browser acceptance: **NOT RUN**
- Real provider smoke: **NOT RUN**
- Visual acceptance: **USER REVIEW PENDING**
- Docker automated acceptance: **BLOCKED — Docker CLI unavailable in the Gate environment**

Do not mark an item PASS until it has been observed directly. Use a disposable project and avoid placing API keys, tokens, raw provider requests, or raw Twin bank identifiers in screenshots or notes.

## Before browser verification

1. Install or expose a working Docker CLI and confirm `docker version` and `docker compose version` succeed.
2. Run the blocked automated commands:
   - `docker compose --env-file .env.e2e.example -f compose.yaml -f compose.e2e.yaml config --quiet`
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-e2e-smoke.ps1 -EnvFile .env.e2e.example`
   - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-failure-e2e.ps1 -EnvFile .env.e2e.example -Scenario all`
3. Run the clean disposable migration check and record the requested Flyway queries. Do not delete a non-disposable development volume.
4. Confirm the normal and failure Docker Gates pass before enabling a paid provider.

## Optional paid 20-person Market Interview checkpoint

This remains approval-gated. After automated Gates 0–7 pass:

1. Store `AI_API_KEY` only in the approved local environment file; never paste or echo it into a terminal log.
2. Configure the existing provider plus:
   - `MARKET_INTERVIEW_MODEL`
   - `MARKET_INTERVIEW_TEMPERATURE=1.0`
   - `MARKET_INTERVIEW_REASONING_EFFORT` if supported
   - `MARKET_INTERVIEW_CONCURRENCY=4`
3. Start the official stack with the real-provider environment.
4. In the browser, select sample size 20 and click the explicit Market Interview start action once.
5. Record model, provider, duration, requested/attempted/usable/failed counts, and retry count without recording secrets or raw requests.
6. Do not start an 80-person run. That requires separate approval after the 20-person result is reviewed.

## Manual journey checklist

| Step | Check | Result |
|---|---|---|
| 1. 현황 점검 | Current project input is visible, save is explicit, and refresh preserves the current state. | NOT RUN |
| 2. 문제 발굴 | Candidates remain distinct, selection is explicit, and no analysis silently changes the selected concept. | NOT RUN |
| 3. 사업성 검증 | Exact Market/BM results bind to the selected revision; `financialHandoff` is preserved; duplicate start does not create duplicate work. | NOT RUN |
| 4. 시장 인터뷰 | The screen says AI virtual/synthetic interview, never actual customers; 20-person counts are truthful; failed respondents are absent; no raw panel identity is visible. | NOT RUN |
| 5. 트윈 패널 조사 | The title and disclaimer identify an AI virtual-panel simulation; sample choices are 50/100/300; no population-representative claim appears. | NOT RUN |
| 6. 마케팅 실행 | Output is visibly an AI draft; current concept is the authority; retry and “different draft” remain distinct; no automatic navigation/test starts. | NOT RUN |
| 7. 출시 준비 | Technology, operations, and finance show independent current/stale states; professional input remains factual authority; stale modules cannot enter an integrated report. | NOT RUN |
| 8. 결과 보고서 | Step is 8; Market Interview and Twin Panel are separate; optional missing modules do not block core generation; raw IDs/hashes are hidden; PDF print layout is readable. | NOT RUN |

## Failure and recovery checklist

| Scenario | Expected behavior | Result |
|---|---|---|
| Double-click/start replay | Same idempotency identity replays one command; changed identity conflicts safely. | NOT RUN |
| Refresh while running | GET/current recovers state without automatic POST replay. | NOT RUN |
| Provider/AI failure | No false result is created; prior successful result remains readable. | NOT RUN |
| Source revision changes | Old result becomes stale/historical; late completion cannot become current. | NOT RUN |
| Retry | Only failed, same-source work can retry; stale work requires a new explicit start. | NOT RUN |
| MinIO unavailable | Persistence authority remains consistent and no partial canonical result is promoted. | NOT RUN |
| Malformed/checksum failure | Strict contracts fail closed and preserve the previous successful result. | NOT RUN |
| Different user | Another user's project and artifacts are inaccessible. | NOT RUN |

## Privacy and network checklist

| Check | Result |
|---|---|
| Browser network/log contains no `AI_INTERNAL_SERVICE_TOKEN`. | NOT RUN |
| API responses contain no API key, Authorization header, or raw provider exception. | NOT RUN |
| Market Interview exposes execution-local respondent IDs only. | NOT RUN |
| Raw Twin/profile-bank identifiers and source microdata are absent. | NOT RUN |
| Individual discount/fee percentages are allowed, while respondent/customer population percentages and purchase probabilities are rejected. | NOT RUN |

## Evidence to return

Return each checklist item as `PASS`, `FAIL`, or `NOT RUN`, plus concise reproduction details for failures. Include the Docker command outputs, Flyway query results, 20-person smoke metrics if separately approved, and screenshots only when they do not contain secrets or private source data.
