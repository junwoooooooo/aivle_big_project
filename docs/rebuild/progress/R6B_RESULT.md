# R6B Result — Marketing Content Creation, Canvas, Editor and Revision UI

## Outcome

R6B replaces the `/app/projects/{projectId}/marketing` placeholder with a new `features/marketing-content` frontend. It consumes only the R6A `/api/v3` marketing-content and finalized-planning contracts and does not import the legacy Marketing Workspace, Validation, Persona, Panel Interview, Market Response, BM, or financial features.

The page remains accessible without a FinalizedPlanningSnapshot and then disables only generation while linking to planning finalization. With a finalized snapshot it provides source summary, generation setup, async polling/restoration, preview canvas, local style controls, field-level copy editing, legal blocking versus warnings, immutable revision labels, full draft regeneration, copy, revision save, finalize, download, and a responsive content list.

## Contracts implemented

- Route: `/app/projects/:projectId/marketing`.
- Desktop order: source/setup, canvas/preview/revisions, style/editor/legal controls.
- Mobile order: source, setup, preview, edit, sticky save/download actions.
- R6A request adaptation: CTA is encoded into required phrases and additional instruction without adding an unknown request field.
- Async recovery: queued/running content is restored through bounded detail polling after create/regenerate and by list/detail reload after refresh.
- Revision display names: 첫 생성안, 친근한 톤 수정안, 짧은 SNS 문구안, 법률 고지 반영안, 사용자 편집안, 최종 저장본. Numeric versions are not primary titles.
- Legal UI: prohibited-expression or non-compliant results block save/finalize; missing disclosure and AI warnings remain visually distinct review warnings.
- Partial editor actions: local shortening and required-disclosure application create explicit `SHORTENED` or `LEGAL_NOTICE_APPLIED` edit revisions. R6A exposes only content-level Provider regeneration, so the `새 초안 생성` action regenerates the whole content rather than claiming field-level AI regeneration.

## Files changed

- Route: `frontEnd/src/app/routing/AppRouter.jsx`.
- New feature: all files under `frontEnd/src/features/marketing-content/`:
  - API clients for marketing content and finalized planning.
  - setup/editor/source/status models.
  - async content and generation hooks.
  - Canvas, Setup, Style, Copy Editor, Source Summary, Revision and Content List components.
  - page, renderer/download helper, responsive CSS, and feature export.
  - three targeted tests for setup, editor, and async hook.
- Stage/integration artifacts: this file, `docs/rebuild/verification/R6B_USER_VERIFICATION.md`, `docs/rebuild/progress/R6_RESULT.md`, and `docs/rebuild/verification/R6_USER_VERIFICATION.md`.

## Checks actually run

- `npm.cmd run test:run -- src/features/marketing-content/model/marketingContentModel.test.js src/features/marketing-content/components/MarketingCopyEditor.test.jsx src/features/marketing-content/hooks/useMarketingGeneration.test.jsx` — passed: 3 files, 3 tests.
- The first targeted test attempt used `npm` and was blocked before execution by the local PowerShell policy for `npm.ps1`; the `npm.cmd` retry passed.
- Targeted ESLint for `src/features/marketing-content` and `src/app/routing/AppRouter.jsx` identified four errors; the route import, polling callback, and derived-state initialization were corrected. The single remaining dependency warning was then removed. No full lint was run.
- Final targeted ESLint for the new feature and route — passed with no errors or warnings.
- Final forbidden legacy-feature import scan — passed with no matches inside `features/marketing-content`.
- Final `git diff --check` — passed with no whitespace errors.

## Checks intentionally omitted

- Frontend full lint, production build, baseline, full test suite, Docker rebuild, backend/AI suites, Provider smoke, and browser/manual testing were not run under Fast Mode.
- No branch switch, merge, directory copy, commit, or push was performed.

## Remaining risks

- The final route has not been exercised against a live V13 backend or real Provider.
- R6A response summaries do not expose a general `updatedAt`; non-finalized content therefore displays `서버 시간 미제공` for recent modification instead of inventing a timestamp.
- Preview style choices are intentionally local presentation state because the closed R6A result/revision schema has no style payload.
- Provider-backed field-only regeneration needs a future backend request contract. R6B provides honest local partial editing plus content-level Provider regeneration.
- Clipboard permission, Unicode text download, 390×844/768×1024 layouts, 200% zoom, and screen-reader flow require user verification.

## Exact continuation point

Run `docs/rebuild/verification/R6B_USER_VERIFICATION.md`, then the integrated `docs/rebuild/verification/R6_USER_VERIFICATION.md`. Accept R6 only after backend/AI/frontend/Docker/Provider and browser gates pass. Stop afterward and request R7 separately.
