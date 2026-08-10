# LEGAL-EVIDENCE-HARDEN Result

Date: 2026-08-07

## Outcome

The Concept Factory no longer creates a 국가법령정보센터 homepage placeholder. Concept legal pre-review now derives activities from the canonical Idea Brief catalog and the generated candidate, retrieves and normalizes official provision-level evidence through the existing MOLEG legal-source pipeline, validates finding-level citation coverage, persists reproducibility metadata, and exposes a bounded user-safe report.

The product wording is fixed to `공식 근거 기반 법률 구현 가능성 사전검토`; it does not claim a complete legal review or legal advice.

## Files changed

- `ai/app/legal/moleg.py`
- `ai/app/legal/pipeline.py`
- `ai/app/models/legal_source.py`
- `ai/app/tasks/concept_legal_review/models.py`
- `ai/app/tasks/concept_legal_review/service.py`
- `ai/app/tools/concept_factory_provider_smoke.py`
- `ai/tests/test_legal_source_contract.py`
- `ai/tests/test_concept_legal_evidence.py`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/api/ConceptFactoryApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/application/CanonicalLegalContextAssembler.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/domain/LegalContextPack.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/domain/LegalEvidence.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/repository/LegalEvidenceRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionService.java`
- `backend/src/main/java/com/aivle/backend/taskrun/integration/InternalAiExecutionClient.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactorySqlContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/legal/LegalEvidenceHardeningTests.java`
- `compose.yaml`
- `frontEnd/src/features/concept-factory/components/ConceptReveal.jsx`
- `frontEnd/src/features/concept-factory/components/ConceptReveal.test.jsx`
- `docs/rebuild/progress/LEGAL-EVIDENCE-HARDEN_RESULT.md`
- `docs/rebuild/verification/LEGAL-EVIDENCE-HARDEN_USER_VERIFICATION.md`

## Contracts implemented

- Legal Context reads only `problem`, `targetCustomers`, `usageContext`, `targetRegion`, `fixedConditions`, `prohibitedMethods`, `physicalActivity`, `personalData`, `payment`, and `requiredPartners` from the canonical catalog.
- Removed direct reads of nonexistent `industry`, `platformRole`, `transactionFlow`, and `labelingAndAdvertising` Idea Brief fields.
- Canonical fields carry `SOURCE_EXTRACTED`; candidate role, transaction, operation, data, physical activity, partner, channel, and pricing context is recorded as `DERIVED_CONTEXT`.
- The existing versioned legal registry and MOLEG client are reused by the active Concept legal-review task.
- Source timeout/5xx remains retryable `MOLEG_DEPENDENCY_UNAVAILABLE`; missing key/invalid configuration remains permanent `LEGAL_CONFIGURATION_INVALID` or `MOLEG_AUTHENTICATION_FAILED`; empty/ambiguous evidence becomes `NEEDS_FACTS` instead of passing.
- Retrieval cache keys include normalized request parameters, registry version, and retrieved date. Persisted Evidence is tied to the Idea Brief snapshot hash through `LegalContextPack`.
- Evidence persists `sourceType`, `lawId`, official identifier, law name, article reference, title, official URI, jurisdiction, promulgation/effective dates, retrieved time, content hash, bounded normalized provision summary, query key, and registry version.
- Evidence uniqueness uses context pack + query key + article reference + content hash. The homepage URL is rejected by both domain validation and the baseline SQL constraint.
- Provider input contains reference index, law/article metadata, effective date, bounded normalized rule summary, official URI, and retrieved time; it does not receive an unbounded legal text body.
- Every item in `requiredControls`, `requiredPartnersAndQualifications`, `requiredDisclosures`, and `prohibitedVariants` requires at least one valid Evidence reference. Unknown, duplicate, missing, or out-of-range references are rejected.
- `IMPLEMENTABLE` and `IMPLEMENTABLE_WITH_CONTROLS` cannot be produced without official Evidence.
- Backend repeats the Evidence and material-finding coverage checks before persisting an eligible Concept.
- User Concept details expose reviewed activities, controls, partner/qualification requirements, disclosures, prohibited variants, unknown facts, law/article/effective-date/source metadata, expert-review recommendation, and the pre-review limitation notice.
- Assessment JSON excludes the official Evidence pack. User responses and selected snapshots contain bounded source metadata but no prompt, provider body, authorization, secret, stack trace, or full provision text.
- The provider smoke now performs real official-source retrieval and prints only status, Evidence count, and redesign completion.

## Checks actually run

- Preflight: branch `rebuild/new-pipeline-v1`, HEAD `3efd98230b8e4f7d88cdb1af39dc0a877338c1fc`, clean starting worktree.
- `backend\\gradlew.bat compileJava` — passed.
- Targeted backend tests — 16 passed:
  - `LegalEvidenceHardeningTests` — 3 passed.
  - `ConceptFactorySqlContractTests` — 1 passed.
  - affected `ConceptFactoryWorkerTests` — 12 passed.
- `ai\\.venv\\Scripts\\python.exe -m compileall -q app tests` — passed.
- Targeted pytest (`test_legal_source_contract.py`, `test_regulatory_boundary_contract.py`, `test_concept_factory_schema.py`, `test_concept_legal_evidence.py`) — 27 passed.
- Targeted frontend Vitest `ConceptReveal.test.jsx` — 1 passed.
- Targeted frontend ESLint for `ConceptReveal.jsx` and its test — passed.
- Static placeholder/key search — passed; no active homepage fallback or nonexistent Legal Context field lookup remains.
- `git diff --check` — passed.

The first backend compile found one downstream selected-snapshot getter still using the removed generic `sourceUri`; it was aligned to the official source metadata contract, and the rerun passed.

## Checks intentionally omitted

- `python -m app.tools.concept_factory_provider_smoke` was not run because `MOLEG_API_KEY` and `AI_API_KEY` were not configured in the current process. No fixture fallback was used.
- Full backend tests and full `postgresTest`.
- Docker build/runtime, clean database reset, browser E2E, full frontend baseline, and production build.
- Live MOLEG/provider calls and legal-professional review of the normalized summaries.

## Remaining risks

- Live 국가법령정보센터 payload variations, rate limits, and provider latency still require the documented smoke/runtime verification.
- Promulgation and effective dates are preserved as official-source strings because MOLEG may return compact date formats; the UI presents them as source basis values rather than asserting normalized legal effect.
- The in-process source cache is bounded by TTL, while durable reproducibility is provided by Evidence query/hash/date metadata. Cross-instance shared cache infrastructure is not introduced in this unit.
- Baseline V1 changed. An already initialized database will not have the new Legal Context/Evidence columns and constraints until the clean reset described in the verification document.

## Exact continuation point

Only after this unit is complete, back up any required local data and perform the clean database reset in `LEGAL-EVIDENCE-HARDEN_USER_VERIFICATION.md`. Rebuild the backend/AI images, run the real official-evidence provider smoke, then run one Concept Factory flow and verify the persisted provision metadata and safe user response. Stop after runtime acceptance; do not continue to another unit without an explicit instruction.
