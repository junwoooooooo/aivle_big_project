# PRODUCT-CUTOVER-CLEANUP Result

## Outcome

The user-facing project shell now reflects the current six-stage product contract. Active Idea,
Concept, Selection, and Marketing routes render their real screens; external Market and
BM/financial/Persona modules retain explicit `NOT_CONNECTED` states. Development-stage copy,
the unused module Placeholder implementation, obsolete Journey build flags, and the unused
three-concept AI exploration implementation were removed.

The active Job Event message registry now contains only the 29 keys actually published by the
Idea Brief, Concept, and Marketing workers. Unknown or archived events are hidden instead of being
presented as generic progress. Concept limits remain authoritative at 5 eligible slots, 15 inspected
candidates, and 2 replacement rounds.

## Files changed

- `.env.example`
- `README.md`
- `ai/app/models/concept_core.py` (removed unused three-concept model)
- `ai/app/services/concept_core.py` (removed unused three-concept service)
- `docs/api/openapi.yaml`
- `docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `docs/rebuild/REPOSITORY_REORGANIZATION_AND_CUTOVER_PLAN_v1.0.md`
- `frontEnd/Dockerfile`
- `frontEnd/src/app/project-shell/ProjectModulePages.jsx`
- `frontEnd/src/app/project-shell/project-shell.css`
- `frontEnd/src/app/routing/AppRouter.cutover.test.js`
- `frontEnd/src/features/idea-intake/api/ideaBriefApi.js`
- `frontEnd/src/features/idea-intake/components/IdeaIntakeForm.jsx`
- `frontEnd/src/features/idea-intake/pages/IdeaIntakePage.jsx`
- `frontEnd/src/features/market-integration/pages/MarketIntegrationPage.jsx`
- `frontEnd/src/shared/async-events/JobTimeline.test.jsx`
- `frontEnd/src/shared/async-events/jobEventMessages.js`
- `frontEnd/src/shared/async-events/jobEventMessages.test.js`
- `scripts/demo-start.ps1`
- `scripts/verify-pipeline-cutover.mjs`
- `docs/rebuild/progress/PRODUCT-CUTOVER-CLEANUP_RESULT.md`
- `docs/rebuild/verification/PRODUCT-CUTOVER-CLEANUP_USER_VERIFICATION.md`

## Contracts implemented

- Concept generation is documented and statically checked as 5 eligible / 15 inspected / 2
  replacement rounds. Provider concurrency remains a separate 1–3 operational setting.
- The active message registry matches current worker publishers and excludes old attachment,
  Opportunity Brief, Boundary, conversation, and legal-report-build events.
- Idea confirmation and Market `NOT_CONNECTED` copy use product language without R-stage or fixture
  terminology.
- The project overview links every module regardless of readiness; it does not reinstate a global
  project stage or route gate.
- Idea, Concept, Selection, Market, external BM/financial/Persona, and Marketing routes point to the
  current screens. Former project URLs remain redirects only and do not render Journey components.
- README, environment comments, governing rebuild documents, and OpenAPI describe the current
  pipeline, public routes, module/job queries, five provider task contracts, two orchestration task
  types, clean V1 baseline, and provider smoke entry points.

## Checks actually run

- Preflight: branch `rebuild/new-pipeline-v1`, HEAD
  `e814f60458592696561c6dc23826700c181a3f65`, initially clean worktree.
- `node scripts/verify-pipeline-cutover.mjs` — passed.
- `npm.cmd run test:run -- src/app/routing/AppRouter.cutover.test.js src/shared/async-events/jobEventMessages.test.js src/shared/async-events/JobTimeline.test.jsx` — passed, 3 files / 13 tests.
- `npm.cmd run test:run -- src/test/App.test.jsx` — passed, 1 file / 16 tests.
- Targeted ESLint for changed frontend route, shell, Idea, Market, and Event files — passed.
- Static worker/Event comparison and forbidden active-surface searches — passed with no stale
  user-facing matches.
- `git diff --check` — passed.

An initial OpenAPI parse attempt did not execute because PyYAML is not installed; a Ruby fallback
also could not execute because Ruby is unavailable. No dependency was installed solely for this
check. The YAML was reviewed and its route/metadata text was covered by static inspection.

## Checks intentionally omitted

- Backend `compileJava`: no Java source changed.
- Full backend tests, `postgresTest`, Testcontainers, full frontend baseline/build, Docker build,
  browser E2E, and provider smoke: excluded by Fast Execution.
- AI test suite: the deleted `concept_core` files had no repository import or dispatcher reference;
  active provider task implementations were not changed.
- OpenAPI schema-validator gate: no YAML/OpenAPI parser is installed in the current runtime.

## Remaining risks

- OpenAPI still uses compact response descriptions rather than complete response schemas and needs
  an installed OpenAPI validator in integrated acceptance.
- Former project URLs intentionally remain redirect aliases for bookmarks. They do not render a
  legacy surface, but removing the aliases later would be a separate compatibility decision.
- Historical RESULT and USER_VERIFICATION records keep their original R-stage terminology by
  design; they are implementation records, not current runtime acceptance evidence.
- Docker/runtime rendering and responsive browser behavior remain user verification gates.

## Exact continuation point

Stop this execution unit. Run the commands and browser checks in
`docs/rebuild/verification/PRODUCT-CUTOVER-CLEANUP_USER_VERIFICATION.md`. If they pass, proceed to the
single clean-database reset and integrated new-pipeline acceptance gate already required after the
Legal Evidence hardening work; do not restore any legacy Journey surface.
