# R1A Result — New Project Shell, Routes, Navigation and Immediate Legacy UI Cutover

## Outcome

R1A frontend active-surface cutover is implemented on branch `rebuild/new-pipeline-v1` at starting HEAD `a363be69ceaa4569274aeb9ef6ddb6819a002f4f`.

The active application now enters a new six-stage project shell. All eight canonical project routes are reachable, the project base route redirects to `/overview`, and direct legacy project URLs redirect only to new modules. The active router no longer imports or renders legacy Journey pages.

Backend, AI, database Migration, dependencies, and legacy component implementation files were not changed. The pre-existing `.gitignore` modification was preserved unchanged.

## Files changed

Created:

- `frontEnd/src/app/routing/AppRouter.jsx`
- `frontEnd/src/app/routing/projectRoutes.js`
- `frontEnd/src/app/module-status/projectModuleModel.js`
- `frontEnd/src/app/module-status/projectModuleModel.test.js`
- `frontEnd/src/app/project-shell/ProjectLayout.jsx`
- `frontEnd/src/app/project-shell/ProjectModulePages.jsx`
- `frontEnd/src/app/project-shell/project-shell.css`
- `docs/rebuild/progress/R1A_RESULT.md`
- `docs/rebuild/verification/R1A_USER_VERIFICATION.md`

Updated:

- `frontEnd/src/app/App.jsx`
- `frontEnd/src/app/layouts/AppShell.jsx`
- `frontEnd/src/app/layouts/AdminShell.jsx`
- `frontEnd/src/features/projects/ProjectGetStartedPage.jsx`
- `frontEnd/src/features/projects/ProjectPages.jsx`
- `frontEnd/src/features/projects/ProjectPages.test.jsx`
- `frontEnd/src/features/projects/ProjectSettingsPages.jsx`
- `frontEnd/src/features/projects/ProjectSettingsSheet.jsx`
- `frontEnd/src/features/projects/WorkspaceHomePage.jsx`
- `frontEnd/src/features/projects/components/ProjectRow.jsx`
- `frontEnd/src/features/projects/model/projectViewModel.js`

## Contracts implemented

- Canonical routes:
  - `/app/projects/:projectId/overview`
  - `/app/projects/:projectId/idea`
  - `/app/projects/:projectId/concepts`
  - `/app/projects/:projectId/concepts/compare`
  - `/app/projects/:projectId/market`
  - `/app/projects/:projectId/business-persona-test`
  - `/app/projects/:projectId/marketing`
  - `/app/projects/:projectId/settings`
- `/app/projects/:projectId` redirects to the canonical overview route.
- Desktop shell provides a fixed left module navigation, project title, current module title, text-and-color status, task-center entry, settings entry, and main content.
- Mobile shell provides a native current-module selector, previous/next links, settings access, and a single-column layout.
- Navigation uses route identity and independent module status, not `project.stage`.
- Temporary module model supports `NOT_READY`, `READY`, `QUEUED`, `RUNNING`, `NEEDS_INPUT`, `COMPLETED`, `FAILED`, `STALE`, and `NOT_CONNECTED`.
- Every unimplemented module has a non-empty placeholder with state, required inputs, and a safe navigation action. No execution behavior was added.
- Active Journey imports, the Journey stepper, the Plan/Review/Validate/Report help context, and legacy global project progress derived from `project.stage` were disconnected.
- Legacy `/legal`, `/journey/**`, `/plan/**`, `/review/**`, `/validate/**`, `/validation/**`, `/report/**`, and old `/projects/:projectId/**` URLs redirect to safe new-pipeline destinations without rendering legacy components.
- Existing authentication, `ProjectContext`, project settings overlay/direct route, and admin routes remain registered.
- Legacy source files were retained for R7 physical deletion.

## Checks actually run

- Root `AGENTS.md`, branch, HEAD, and `git status` preflight.
- Required R1A governing documents and relevant Manifest paths.
- Active import, legacy wording, legacy route, and `project.stage` navigation reference searches.
- Targeted test:
  - Initial `npm run test:run -- src/app/module-status/projectModuleModel.test.js` invocation did not start because PowerShell blocked `npm.ps1`.
  - Re-run with `npm.cmd run test:run -- src/app/module-status/projectModuleModel.test.js`: 1 file passed, 2 tests passed.
- Changed frontend source syntax/lint inspection with targeted ESLint: passed with no findings.
- `git diff --check`: recorded after the final document update.

## Checks intentionally omitted

- Full frontend lint.
- Frontend production build.
- Frontend baseline and full frontend test suite.
- Backend and AI test suites.
- Full `postgresTest`, Testcontainers, and database migration checks.
- Docker Compose rebuild and provider smoke.
- Browser/manual accessibility testing.
- Commit and push.

## Remaining risks

- Module statuses are intentionally temporary defaults until later stages connect durable module state and job events.
- Placeholder actions do not execute domain work by R1A design.
- Browser rendering, responsive behavior, settings overlay interaction, and redirect behavior require the manual checks in `R1A_USER_VERIFICATION.md`.
- Dead legacy files still contain old terminology and imports by design; they are disconnected from the active router and remain scheduled for R7 deletion.
- Backend legacy controllers remain unchanged because this R1A execution was explicitly frontend-only.

## Exact continuation point

Stop after R1A. After the user completes the verification document and accepts all success criteria, the next authorized execution may begin at the separately requested R1B preflight. Do not start R1B automatically.
