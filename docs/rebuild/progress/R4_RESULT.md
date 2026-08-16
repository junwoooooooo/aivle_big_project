# R4 Integrated Result — Five-Concept Comparison, Selection and Market Handoff Shell

## Outcome

R4A and R4B now provide one bounded path from the five public legally eligible R3 concepts to user-led comparison, explicit selection, immutable Selected Concept Snapshot creation, idempotent market Handoff preparation, and a truthful external-module Integration Shell.

- R4A: five-card and score-free comparison views, deterministic tags, complete legal-detail modal, 2–5 compare set, mobile two-card replacement, and session-local preparation draft.
- R4B: explicit reasoned selection, immutable/hash-linked Snapshot history, schema-aligned market input, idempotent Handoff/Run persistence, old-Run preservation/effective staleness, and `/market` `NOT_CONNECTED` UI.

No external market-analysis algorithm or legacy Journey dependency was added.

## Checks actually run across R4

- R4A model/card/comparison tests passed; R4A targeted ESLint passed.
- R4B Snapshot hash, Selection/Handoff idempotency, and Schema fixture tests passed.
- R4B Selection confirmation test and targeted frontend ESLint passed.
- R4B Java compilation passed.
- Stage-level `git diff --check` result is reported after final documentation update.

## Checks intentionally omitted and risks

Frontend production build/baseline, full backend/AI/PostgreSQL/Testcontainers suites, Docker rebuild, live V10 migration, actual external Provider/callback, and browser/mobile/accessibility testing were omitted under Fast Mode. These remain user acceptance gates; targeted green status is not integrated R4 acceptance.

## Exact continuation point

Execute `docs/rebuild/verification/R4_USER_VERIFICATION.md` end to end. If every gate passes, stop and request the next stage separately. Any later market adapter must use the persisted immutable input contract; R5 must begin from accepted market result/change-proposal contracts and must not modify R4 history in place.
