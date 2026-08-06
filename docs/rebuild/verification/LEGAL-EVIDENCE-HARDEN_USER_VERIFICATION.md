# LEGAL-EVIDENCE-HARDEN User Verification

Run from PowerShell in `C:\Users\seewo\Desktop\big_proj_01\new_3`.

## 1. Targeted checks

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\backend'
.\gradlew.bat compileJava
.\gradlew.bat test --tests 'com.aivle.backend.pipeline.legal.LegalEvidenceHardeningTests' --tests 'com.aivle.backend.pipeline.concept.ConceptFactorySqlContractTests' --tests 'com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests'

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\ai'
.\.venv\Scripts\python.exe -m compileall -q app tests
.\.venv\Scripts\python.exe -m pytest tests/test_legal_source_contract.py tests/test_regulatory_boundary_contract.py tests/test_concept_factory_schema.py tests/test_concept_legal_evidence.py -q

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd'
npx.cmd vitest run src/features/concept-factory/components/ConceptReveal.test.jsx
npx.cmd eslint src/features/concept-factory/components/ConceptReveal.jsx src/features/concept-factory/components/ConceptReveal.test.jsx

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
git diff --check
```

## 2. Clean DB reset — only after this unit

Baseline V1 changed and the reset is destructive. Back up anything needed first. Do not run this step before the implementation and targeted checks above are complete.

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
$DbUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'aivle' }
$DbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'aivle' }
docker compose stop backend
docker compose up -d postgres
docker compose exec -T postgres psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
docker compose build ai-server backend frontend
docker compose up -d ai-server backend frontend
docker compose ps
```

Expected: backend startup applies baseline V1 successfully. This deletes all application data in the configured database schema.

## 3. Real official-Evidence provider smoke

Ensure `.env`/the current process has real `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `MOLEG_API_KEY`, `AI_INTERNAL_SERVICE_TOKEN`, and `LEGAL_REGISTRY_VERSION=legal-registry-v1`. Do not enable fixture mode.

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
docker compose exec -T ai-server python -m app.tools.concept_factory_provider_smoke
```

Expected output contains only:

- `status`
- `evidenceCount` greater than zero
- `redesignCompleted`

It must not print secrets, prompts, provider response bodies, full statutory text, or raw user input.

Inspect safe error classification if the smoke fails:

```powershell
docker compose logs --since=10m ai-server | Select-String -Pattern 'MOLEG_|LEGAL_CONFIGURATION_INVALID|RESULT_SCHEMA_INVALID'
```

- Timeout/5xx should be retryable `MOLEG_DEPENDENCY_UNAVAILABLE`.
- Missing/invalid key or registry configuration should be permanent.
- No relevant provision or ambiguous activity should result in `NEEDS_FACTS`, not an Evidence-free pass.

## 4. Run one Concept Factory flow

Create and confirm a new Idea Brief after the clean reset. Then supply the owner token, project id, and confirmed snapshot id.

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

1..120 | ForEach-Object {
  $Run = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v3/projects/$ProjectId/concept-factory-runs/$RunId" -Headers $Headers
  $Run.data | Select-Object runId,status,replacementRounds,inspectedCandidateCount
  if ($Run.data.status -in @('COMPLETED', 'NEEDS_INPUT', 'FAILED')) { break }
  Start-Sleep -Seconds 2
}
```

## 5. Verify persisted Evidence and cache identity

```powershell
$DbUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'aivle' }
$DbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'aivle' }
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT source_snapshot_hash,registry_version,canonical_context_json,provenance_json FROM legal_context_packs WHERE source_snapshot_id='$SnapshotId';"
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT source_type,law_id,official_identifier,law_name,article_reference,title,official_source_uri,jurisdiction,promulgation_date,effective_date,retrieved_at,content_hash,query_key,registry_version FROM legal_evidence WHERE context_pack_id IN (SELECT id FROM legal_context_packs WHERE source_snapshot_id='$SnapshotId') ORDER BY law_name,article_reference;"
docker compose exec -T postgres psql -U $DbUser -d $DbName -c "SELECT ca.status,le.law_name,le.article_reference,le.effective_date,le.official_source_uri FROM concept_legal_assessments ca JOIN concept_legal_evidence_links link ON link.assessment_id=ca.id JOIN legal_evidence le ON le.id=link.evidence_id WHERE ca.project_id=$ProjectId ORDER BY ca.created_at,le.law_name,le.article_reference;"
```

Acceptance criteria:

- Canonical context contains only allowed Idea Brief keys and records `SOURCE_EXTRACTED`/`DERIVED_CONTEXT` provenance.
- Every Evidence row is `OFFICIAL_LAW`, has an official identifier, law name, article reference, official non-homepage URI, retrieved time, content hash, query key, and registry version.
- Duplicate law/article/effective-date/content evidence is not stored twice for the same context.
- Every eligible assessment has linked Evidence.
- No `https://www.law.go.kr/` homepage-only row exists.

## 6. Verify the user-safe report

```powershell
$Concepts = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v3/projects/$ProjectId/concepts" -Headers $Headers
$Concepts.data.concepts | ConvertTo-Json -Depth 12
```

Confirm the response/UI displays:

- pre-review status and reviewed activities
- controls, partner/qualification requirements, disclosures, prohibited variants, and unknown facts
- law name, article, effective-date basis, retrieval time, and official link
- expert-review recommendation and pre-review limitations

Confirm it does not contain prompt text, authorization, secrets, provider body, stack trace, `boundedProvisionSummary`, or full provision text.

## 7. Job Event safety

```powershell
$Events = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v2/jobs/$JobId/events?after=0" -Headers $Headers
$Events.data | ConvertTo-Json -Depth 8
```

Events may signal legal-context and validation progress, but must not include legal text, Evidence pack bodies, raw Idea Brief values, provider payloads, or credentials.
