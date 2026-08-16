# V27 Launch Readiness Authority Alignment Result

## Status

IMPLEMENTED. TEST EXECUTION DEFERRED — FINAL INTEGRATION GATE.

Start SHA: `b6d0f81b1dd2acfae9b4d986116b656803b5360c`  
Donor SHA (read-only): `4ee74359a1b231359dc3131fb8eecb126462d2bf`

## Implemented

- Fixed the V26 Marketing STALE rollback paths. Retry, edit, and finalize now return a committed historical STALE view; worker start returns a durable stale precondition result and does not call the provider.
- Reused `CurrentConceptSourceResolver` for technology, operations, and finance. New inputs bind the exact Market Seed snapshot, Selection ID/revision, and BM plan revision.
- Launch Readiness input contract v2 combines current-concept context with the submitted professional DOCX input. Professional input remains the factual authority.
- File fingerprint, parsed professional input, module, and exact concept binding now form command identity before artifact storage. Same-key changed document/source is an idempotency conflict.
- Technology/operations use one TaskRun attempt per user command. Retry is explicit, FAILED-only, exact-source-only, and capped at three durable analysis attempts.
- Current projection is exact-source aware and exposes friendly STALE reason, retry availability, historical result, and current input status without requiring storage interpretation in the client.
- Late success is adopted and preserved as a historical stale report when source authority drifted. Late failure marks the bound input stale and cannot become current.
- A new document supersedes only its own technology or operations module.
- Finance user-document and deterministic snapshots now carry the same current-concept binding. Concept drift projects Finance as STALE; calculation code was not replaced.
- Integrated report generation rejects stale modules and records module result identity/hash, input snapshot identity/hash, and exact concept binding in its manifest.
- PDF and page copy explain that readiness is based on the current confirmed business concept plus submitted professional input and is decision support, not certification.
- Frontend distinguishes concept drift from document supersession, keeps stale results readable, provides manual retry/new-analysis actions, and performs one current GET after an ambiguous start/retry without resending the POST.
- Journey presentation is aligned to step 7, “출시 준비”.
- Added additive migration `V39__launch_readiness_current_concept_lineage.sql`. Legacy rows are not fabricated into current lineage and therefore project conservatively as historical/stale.

## Tests authored

- Marketing stale retry/edit/finalize persistence and worker provider-skip coverage.
- Launch exact source binding, module-scoped document supersession, canonical idempotency, explicit max-three retry, late-result isolation, and integrated-manifest guards.
- Finance current-concept binding and deterministic-calculation preservation guards.
- AI current-concept context acceptance plus professional-input/no-fabrication boundary.
- Frontend stale messages, historical readability, retry, ambiguous mutation recovery, API retry, journey step, and integrated-report availability behavior.

## Checks actually run

- Backend `compileJava`: PASS after correcting the discovered Selection ID Java/SQL type mismatch. No test task was executed.
- AI AST parse: PASS.
- Selective ESLint for changed Launch Readiness JS/JSX/test files: PASS.
- Backend tests: DEFERRED.
- AI tests: DEFERRED.
- Frontend tests: DEFERRED.
- Real provider, Docker, browser, PDF visual acceptance: 0.

## Final integration debt

- Execute the authored V25 Twin Panel tests that remain deferred.
- Execute V27 focused Backend, AI, and Frontend tests together with the final source-lineage integration gate.
- Verify Business Validation → Refinement → Market Interview → Twin Panel → Marketing → Launch Readiness exact lineage.
- Perform desktop/mobile and generated-PDF visual review.

## Continuation point

READY FOR V28 — FINAL REPORT AUTHORITY ALIGNMENT.
