# CONCEPT-FACTORY-TERMINALIZE User Verification

Run these commands from PowerShell. They intentionally include the heavy Docker/runtime gates that were omitted during fast execution.

## 1. Build and start the runtime

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
docker compose build backend frontend
docker compose up -d postgres minio minio-init ai-server backend frontend
docker compose ps
```

All configured secrets and provider settings must already be present in `.env`. Keep `AI_FIXTURE_MODE=false` for real-provider acceptance.

## 2. Run the targeted automated gates

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\backend'
.\gradlew.bat compileJava
.\gradlew.bat test --tests 'com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests' --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryStateMachineTests' --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryLimitTests' --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryFiveSlotTests' --tests 'com.aivle.backend.taskrun.TaskRunDomainTests'

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\ai'
.\.venv\Scripts\python.exe -m pytest tests/test_concept_factory_schema.py -q

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
git diff --check
```

Expected: all commands pass. Worker tests cover all-success, transient retry, transient exhaustion/replacement, schema repair, redesign, `NEEDS_INPUT`, replacement exhaustion, permanent failure, eligible-slot preservation, and parent terminalization.

## 3. Start a real Concept Factory run

Supply a valid owner access token, project id, and confirmed Idea Brief snapshot id.

```powershell
$BaseUrl = 'http://localhost:3000'
$Token = '<OWNER_ACCESS_TOKEN>'
$ProjectId = <PROJECT_ID>
$SnapshotId = '<CONFIRMED_IDEA_BRIEF_SNAPSHOT_ID>'
$Headers = @{ Authorization = "Bearer $Token"; 'Content-Type' = 'application/json' }
$Body = @{ ideaBriefSnapshotId = $SnapshotId } | ConvertTo-Json -Compress

$Created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v3/projects/$ProjectId/concept-factory-runs" -Headers $Headers -Body $Body
$RunId = $Created.data.runId
$JobId = $Created.data.activeJobId
$RunId
$JobId
```

## 4. Observe run, slots, events, and public reveal

```powershell
1..120 | ForEach-Object {
  $Run = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v3/projects/$ProjectId/concept-factory-runs/$RunId" -Headers $Headers
  $Slots = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v3/projects/$ProjectId/concept-factory-runs/$RunId/slots" -Headers $Headers
  [pscustomobject]@{
    Status = $Run.data.status
    ReplacementRounds = $Run.data.replacementRounds
    InspectedCandidates = $Run.data.inspectedCandidateCount
    EligibleSlots = @($Slots.data | Where-Object status -eq 'ELIGIBLE').Count
    FailedSlots = @($Slots.data | Where-Object status -eq 'FAILED').Count
  }
  if ($Run.data.status -in @('COMPLETED', 'NEEDS_INPUT', 'FAILED')) { break }
  Start-Sleep -Seconds 2
}

$Events = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v2/jobs/$JobId/events?after=0" -Headers $Headers
$Events.data | Sort-Object sequence | Select-Object sequence,eventType,status,technicalCode
$Public = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v3/projects/$ProjectId/concepts" -Headers $Headers
$Public.data.concepts.Count
```

Expected normal path:

- Run ends `COMPLETED`.
- Exactly five slots end `ELIGIBLE`.
- Public concept count remains zero before completion and becomes exactly five after completion.
- `replacementRounds` is at most 2 and `inspectedCandidateCount` is at most 15.
- Events include `job.concept.run.started`, both legal-context events, slot lifecycle events, and exactly one `job.concept.run.completed` terminal event.

Expected insufficient-facts path:

- Run and parent TaskRun end `NEEDS_INPUT`.
- `job.concept.run.needs_input` is terminal.
- Already eligible slot results remain stored, while public concepts remain hidden because the run is not complete.

Expected failure path:

- Run and parent TaskRun end `FAILED`.
- Exhausted replacement produces a failed slot and `job.concept.run.failed`.
- A permanent provider failure does not start an unbounded replacement loop.

## 5. Verify persisted terminal invariants

Use the Compose database defaults below, or replace them with the values from `.env`.

```powershell
$DbUser = 'aivle'
$DbName = 'aivle'
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT id,status,replacement_rounds,inspected_candidate_count,task_run_id FROM concept_factory_runs WHERE id='$RunId';"
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT slot_number,status,attempt_count,legal_redesign_count FROM concept_slots WHERE run_id='$RunId' ORDER BY slot_number;"
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT tr.id,tr.state,tr.attempt_count,tr.last_error_code,ta.state AS attempt_state FROM task_runs tr LEFT JOIN task_attempts ta ON ta.id=tr.current_attempt_id WHERE tr.id='$JobId';"
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT cs.slot_number,ca.attempt_number,ca.phase,ca.error_classification,ca.retryable FROM concept_attempts ca JOIN concept_slots cs ON cs.id=ca.slot_id WHERE cs.run_id='$RunId' ORDER BY cs.slot_number,ca.attempt_number;"
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT sequence,event_type,status,technical_code FROM job_events WHERE job_id='$JobId' ORDER BY sequence;"
```

Acceptance criteria:

- No claimed Concept Factory TaskRun remains `RUNNING` after the domain run reaches `COMPLETED`, `NEEDS_INPUT`, or `FAILED`.
- A `NEEDS_INPUT` TaskRun is terminal and not claimable.
- Each slot has at most one `REDESIGN` attempt.
- Replacement round never exceeds 2; inspected candidates never exceed 15.
- Failed/rejected attempts are never published as eligible concepts.
- Job Event payloads contain no prompt, authorization, provider body, raw user input, legal source body, secret, or stack trace.

## 6. Inspect runtime logs

```powershell
docker compose logs --since=30m backend ai-server | Select-String -Pattern 'CONCEPT_FACTORY_RUN|CONCEPT_CANDIDATE|CONCEPT_REDESIGN|CONCEPT_LEGAL_REVIEW|job.concept.run'
```

Confirm that retries and replacements are bounded and that the terminal event agrees with the Run and TaskRun query state.
