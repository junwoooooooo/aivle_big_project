# R4A User Verification — Five-Concept Comparison and Selection Preparation

## 1. Commands to run

From the repository root:

```powershell
cd frontEnd
npm.cmd test -- --run src/features/concept-selection/model/conceptComparisonModel.test.js src/features/concept-selection/components/ConceptComparisonView.test.jsx
npm.cmd exec eslint -- src/features/concept-selection src/app/routing/AppRouter.jsx
npm.cmd run build
```

Success criteria:

- Both targeted test files pass.
- ESLint reports no errors in the new feature or route.
- The production build completes without unresolved imports, JSX errors, or CSS processing errors.

Codex ran the targeted tests and ESLint. Codex did not run the production build.

## 2. Browser verification

Open `/app/projects/{projectId}/concepts/compare` for a project whose current Concept Factory run has five published eligible concepts.

Confirm:

1. Exactly five cards appear together; no draft concept appears before Concept Factory completion.
2. Every card shows name, one-line description, differentiator, target customer, operating model, revenue structure, legal state, required conditions, risks, legal detail, compare control, and preferred-candidate control.
3. Tag text is supported by visible server data. No unexplained tag, composite score, automatic winner, or rank appears.
4. A maximum of five and minimum of two concepts can form a saveable comparison set.
5. A preferred candidate can only be marked after that concept is in the comparison set.
6. Removing the preferred candidate from the comparison set also clears the preferred state.
7. The desktop comparison table contains all eleven specified rows and only the selected concept columns.
8. At 390×844 and 768×1024, the wide table is replaced by comparison groups containing no more than two concept cards.
9. The legal-detail modal shows status, safe summary, basis date, expert-review recommendation, implementation hypothesis, reviewed activities, controls, partner/qualification requirements, disclosures, prohibited variants, unknown facts, and official Evidence links.
10. Escape, backdrop click, and the close button close the modal; Tab/Shift+Tab remain inside while open; focus returns to the invoking control.
11. Saving shows that the draft is browser-session-only. Reloading the tab restores it; a new browser session does not imply a server selection.
12. No request creates a Selection Snapshot or market Handoff. Inspect the Network panel and confirm R4A performs only Concept Factory/current concept reads.
13. For a project with no run, a running run, `NEEDS_INPUT`, `FAILED`, or `STALE`, the route remains accessible and shows the required state plus `Concept Factory로 이동`.
14. Verify keyboard-only operation, screen-reader reading order, focus visibility, 200% zoom, reduced motion, and long Korean line wrapping.

## 3. Database initialization

No R4A schema or seed change exists. Database initialization is not required if the R3 schema and a completed five-concept run are already available.

If no completed R3 project exists, create one through the existing Idea Brief and Concept Factory flow; do not insert selection rows manually because R4A has no selection persistence.

## 4. Docker rebuild

For the repository's production-container path, rebuild only the frontend after the R3 services and data are already available:

```powershell
docker compose build frontend
docker compose up -d frontend
```

Backend, AI server, and PostgreSQL do not require an R4A-specific rebuild or reset. Rebuild them only if the environment does not already contain the accepted R3 implementation.

## 5. Logs and evidence to collect on failure

```powershell
docker compose ps
docker compose logs frontend --since=30m
docker compose logs backend --since=30m
```

Also collect:

- Browser console errors and the failing route.
- The status/code and safe response body for `GET /api/v3/projects/{projectId}/concept-factory-runs/current` and `GET /api/v3/projects/{projectId}/concepts`.
- Current run ID/status, project ID, public concept count, and whether each concept has candidate/legal-review fields.
- Viewport size, zoom level, interaction sequence, and an accessibility-tree or screenshot for layout/focus failures.
- The local draft value under `concept-selection-draft:{projectId}` with business text redacted if needed.

Do not collect authorization headers, tokens, raw Provider bodies, prompts, or unrelated user data.

## 6. Next-stage condition

R4B may begin only when targeted tests, lint, production build, completed/not-ready route states, five-card rendering, eleven-row comparison, two-concept mobile replacement, legal modal accessibility, 2–5 selection rules, local-only persistence, and absence of selection/Handoff mutations all pass.

R4B must start with explicit confirmation and authoritative server-side Selection Snapshot persistence, then pass the immutable snapshot body/hash plus legal assessment through the market-analysis Handoff contract. Do not automatically continue from this verification.
