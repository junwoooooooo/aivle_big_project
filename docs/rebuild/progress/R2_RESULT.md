# R2 Integrated Result — Canonical Idea Brief

## Outcome

R2A–R2C together replace the conversational idea workspace with a canonical, versioned Idea Brief pipeline:

- R2A: accessible form, Question Cards, shared Draft review UI, and canonical route.
- R2B: owner-scoped persistence, command/query API, idempotency, queued TaskRun, and immutable confirmed snapshot.
- R2C: strict AI derivation task, durable Worker/domain commit, safe Job Events, and live frontend SSE/poll/query integration.

The active route no longer depends on Conversation entities or legacy Journey components. A future chat adapter can call the same `/api/v3/.../idea-brief` contract without becoming the source of truth.

## Checks actually run across R2

- R2A model and Question Card tests passed; targeted frontend ESLint passed.
- R2B field invariant, snapshot, and API tests passed; backend compile passed.
- R2C AI schema/mapper tests, Worker test, and frontend async hook test passed.
- R2C final Python compile, backend compile, targeted frontend ESLint, and `git diff --check` passed.

## Checks omitted and risks

Full suites, PostgreSQL/Testcontainers, Docker E2E, actual Provider smoke, frontend production build, and browser/mobile/a11y checks were intentionally omitted. These are acceptance gates in `R2_USER_VERIFICATION.md`. Attachment upload remains a later integration boundary; R2 accepts existing stored-file IDs only.

## Exact continuation point

Run and accept `docs/rebuild/verification/R2_USER_VERIFICATION.md`. If all gates pass, stop and request a separate R3 execution beginning at the confirmed Idea Brief snapshot handoff. Do not begin R3 automatically.
