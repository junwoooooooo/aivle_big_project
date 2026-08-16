# IDEA-SYNTHESIS-AND-CONCEPT-FACTORY-RUNTIME-CORRECTION User Verification

This is the mandatory runtime gate. Use real providers: `AI_FIXTURE_MODE=false` and valid AI/MOLEG/internal-service credentials.

## 1. Rebuild and start clean runtime

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
docker compose down -v
docker compose build ai-server backend frontend
docker compose up -d postgres minio minio-init ai-server backend frontend
docker compose ps
```

Expected: all services become healthy. `GET /health/ready` proves process readiness; `GET /health/capabilities/concept-factory` must report every configuration/registry check available.

## 2. Automated fast gates

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\backend'
.\gradlew.bat compileJava
.\gradlew.bat test --tests 'com.aivle.backend.pipeline.idea.*' --tests 'com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests' --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryReplacementIntegrationTests'

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\ai'
.\.venv\Scripts\python.exe -m pytest tests/test_idea_brief_schema.py tests/test_legal_source_contract.py tests/test_concept_legal_evidence.py tests/test_concept_factory_schema.py -q

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd'
npm.cmd test -- --run src/features/idea-intake src/features/concept-factory
npx.cmd eslint src/features/idea-intake/hooks/useIdeaIntake.js src/features/idea-intake/pages/IdeaIntakePage.jsx src/features/concept-factory

Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
git diff --check
```

## 3. Clean project and Idea initial analysis

Create a new test project in the browser. Enter an Idea that requires clarification and start AI organization. Confirm one `IDEA_BRIEF_DERIVATION` TaskRun whose input has `mode=INITIAL`, and record its job id.

## 4. Follow-up rounds 1 and 2

Answer every active question in round 1. Confirm the response is `DERIVING`, has a new `activeJobId`, and the TaskRun input has `mode=CLARIFICATION`. Repeat for round 2. The clarification round must never exceed 2.

## 5. Final answer creates FINAL_SYNTHESIS

Answer every final active question. Confirm immediately:

- response status is `DERIVING`;
- `activeJobId` is new and non-null;
- a new TaskRun input has `mode=FINAL_SYNTHESIS`;
- clarification round remains 2;
- provider result contains `questions=[]`.

After completion, confirm the Review summary, contradictions, missing fields, and readiness reflect the final answer. Max round by itself must never produce Review or `readyForConfirm=true`.

## 6. Review edit freshness gate

On Review, edit a canonical field and click the primary action once. Confirm:

- Confirm is not sent;
- UI says `변경 내용을 다시 정리하고 있습니다.`;
- PATCH response is `DERIVING`, `assessmentCurrent=false`, and contains a new job id;
- direct Confirm during this interval is rejected;
- after final synthesis, `assessmentCurrent=true` and updated summary/contradictions/readiness render;
- only then does `이 내용으로 컨셉 만들기` confirm successfully.

Refresh while final synthesis is running. The page must restore the same active job and remain on the running screen.

## 7. Real provider smoke

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3\ai'
.\.venv\Scripts\python.exe -m app.tools.idea_brief_provider_smoke
.\.venv\Scripts\python.exe -m app.tools.concept_factory_provider_smoke
```

Expected: strict schemas are accepted. There must be no `LEGAL_CITATION_COVERAGE_INVALID`, `CONCEPT_LEGAL_FINDING_EVIDENCE_REQUIRED`, or provider response-schema rejection. If provider smoke fails, do not accept browser Concept Factory success.

## 8. Real Concept Factory run

Start Concept Factory from the newly confirmed snapshot. Observe SSE using the returned `activeJobId`. Confirm all five slots create Candidates and `inspectedCandidateCount` initially represents five Candidates, not ten legal calls.

The Slot cards must show candidate generation count, legal review status, and redesign count. They must not show provider/repair attempts as “검사 후보”.

## 9. Technical legal failure preservation

Using a safe test configuration that causes a Legal Review schema/provider/source failure, confirm:

- Candidate attempt retains non-null `result_json`;
- a separate `LEGAL_REVIEW` attempt records the safe root code;
- Slot becomes `REVIEW_RETRY_PENDING` (or the API-equivalent retryable state);
- no `job.concept.slot.rejected` event is published;
- `replacement_rounds` does not increase;
- API returns `candidatePreserved=true`, root failure phase/code, and `canResume=true`;
- UI says the generated Candidate was preserved and does not label it a legal rejection.

## 10. Resume creates a new job

POST retry with an idempotency key. Confirm the failed parent TaskRun remains terminal and a new TaskRun/new `activeJobId` is returned. Repeat the identical request and confirm it returns the same new job. The frontend SSE connection must follow the new job id. Eligible slots must remain eligible; the failed slot must resume at Legal Review without another Candidate generation.

## 11. NEEDS_INPUT and STALE actions

For `NEEDS_FACTS`, verify retry is not offered and the user is sent to Idea Brief completion. Confirm an old run whose source snapshot differs from the current confirmed Idea Brief cannot resume and offers a new run instead. On a failed terminal run verify both `이어서 시도` (when retryable) and `처음부터 새로 만들기` actions.

## 12. Normal five-slot reveal

Run the normal real-provider path to completion. Acceptance requires exactly five `ELIGIBLE` slots and exactly five concepts revealed simultaneously only after the run reaches `COMPLETED`. No technical failure may appear as `REJECTED`.

## 13. Database and log evidence

```powershell
docker compose exec -T postgres psql -U aivle -d aivle -c "SELECT id,state,last_error_code FROM task_runs WHERE subject_id='<RUN_ID>' ORDER BY created_at;"
docker compose exec -T postgres psql -U aivle -d aivle -c "SELECT slot_number,status,attempt_count,legal_redesign_count FROM concept_slots WHERE run_id='<RUN_ID>' ORDER BY slot_number;"
docker compose exec -T postgres psql -U aivle -d aivle -c "SELECT cs.slot_number,ca.attempt_number,ca.phase,ca.error_classification,ca.safe_error_code,ca.retryable,(ca.result_json IS NOT NULL) AS result_preserved FROM concept_attempts ca JOIN concept_slots cs ON cs.id=ca.slot_id WHERE cs.run_id='<RUN_ID>' ORDER BY cs.slot_number,ca.attempt_number;"
docker compose exec -T postgres psql -U aivle -d aivle -c "SELECT sequence,event_type,status,technical_code FROM job_events WHERE job_id='<JOB_ID>' ORDER BY sequence;"
docker compose logs --since=30m backend ai-server | Select-String -Pattern 'FINAL_SYNTHESIS|LEGAL_REVIEW|review_retrying|review_failed|safeErrorCode'
```

Logs may include run id, slot number, run/slot status, phase, safe error code, and exception class. They must not expose stack traces in API/browser payloads, prompts, secrets, provider bodies, or raw official-law text.

## Acceptance condition

All steps above must pass, including both provider smoke and the normal five-slot browser run. Until then this execution unit is not runtime-accepted and no next feature implementation should begin.
