# R4 Integrated User Verification

## 1. Automated gates

Run the R4A commands in `docs/rebuild/verification/R4A_USER_VERIFICATION.md`, then the R4B commands in `docs/rebuild/verification/R4B_USER_VERIFICATION.md`.

Success: every R4A comparison test and every R4B Snapshot/idempotency/Schema/selection test passes; targeted lint, Java compile, and frontend production build pass.

## 2. Database and Docker gate

Start PostgreSQL, rebuild `backend` and `frontend`, and confirm Flyway V10 plus Hibernate validation. AI server rebuild is not required for R4.

Success: the four new tables exist with project ownership, current-selection, sequence/parent, hash, idempotency, and Module status constraints; no database reset is required for an existing valid V9 database.

## 3. End-to-end R4 browser gate

1. Begin with one completed R3 project exposing exactly five eligible public concepts.
2. Compare 2–5 concepts in cards, desktop table, and mobile two-card groups.
3. Inspect full legal details and verify every tag is supported by server data.
4. Confirm no total score, automatic rank, or hidden draft appears.
5. Mark one compared concept, give a reason, and explicitly confirm it.
6. Verify the immutable Snapshot contains complete concept planning, complete legal Assessment/control/partner/disclosure/prohibited/evidence data, hash, sequence/parent, time, and reason.
7. Repeat identical selection and Handoff requests and verify idempotent identities.
8. Change selection before any real market start and verify a new Snapshot/Handoff is created without overwriting history.
9. Open `/market` before and after Selection. The page always opens; only the action is gated.
10. Verify market state remains `NOT_CONNECTED`, delivery contents match `selected-concept-market-input-v1`, and no fake result appears.
11. Verify an old Run becomes effectively `STALE` after the current selection changes while its stored record remains preserved.
12. Complete keyboard, screen-reader, 390×844, 768×1024, 1280+, 200% zoom, reduced-motion, and long-Korean-content checks.

## 4. Logs on failure

Collect PostgreSQL, backend, and frontend logs from the last 30 minutes plus request IDs and project/concept/selection/Snapshot/Handoff/Run identifiers. Include Flyway history, failed constraint, input Snapshot hash, API safe error, browser console/network evidence, viewport, and reproduction sequence.

Do not collect tokens, authorization headers, prompts, Provider bodies, raw attachments, or unnecessary business/legal content.

## 5. R4 acceptance and continuation

R4 is accepted only when the full comparison UX, explicit selection, immutable Snapshot, Schema alignment, idempotency, history/staleness, truthful `NOT_CONNECTED` shell, database migration, production build, responsive behavior, and accessibility pass together. Stop after reporting verification. Do not automatically begin R5 or connect an external Provider.
