# R2A Result — Idea Intake Form, Follow-up Questions, and Brief Review UI

## Outcome

R2A frontend implementation is complete on branch `rebuild/new-pipeline-v1` from starting HEAD `defa37cee8b90494313d24e6e25dc49bbba15b5a`.

The canonical `/app/projects/:projectId/idea` route now renders a new Idea Intake feature instead of the R1 placeholder. It supports initial structured input, a four-card follow-up question group, editable Idea Brief review, localized field-source labels, and a confirm adapter boundary without calling the R2B/R2C backend or AI runtime.

No legacy Journey or Conversational Workspace component is imported. Backend, AI, database, dependency declarations, and future concept-generation behavior were not changed.

## Files changed

Updated:

- `frontEnd/src/app/routing/AppRouter.jsx`

Created:

- `frontEnd/src/features/idea-intake/index.js`
- `frontEnd/src/features/idea-intake/pages/IdeaIntakePage.jsx`
- `frontEnd/src/features/idea-intake/components/ErrorSummary.jsx`
- `frontEnd/src/features/idea-intake/components/IdeaIntakeForm.jsx`
- `frontEnd/src/features/idea-intake/components/QuestionCard.jsx`
- `frontEnd/src/features/idea-intake/components/QuestionGroup.jsx`
- `frontEnd/src/features/idea-intake/components/IdeaBriefReview.jsx`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.js`
- `frontEnd/src/features/idea-intake/api/ideaBriefApi.js`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.js`
- `frontEnd/src/features/idea-intake/model/ideaQuestions.js`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.test.js`
- `frontEnd/src/features/idea-intake/components/QuestionCard.test.jsx`
- `frontEnd/src/features/idea-intake/styles/idea-intake.css`
- `docs/rebuild/progress/R2A_RESULT.md`
- `docs/rebuild/verification/R2A_USER_VERIFICATION.md`

## Contracts implemented

- The canonical project idea route renders `IdeaIntakePage` inside the R1 project shell.
- The initial form contains idea overview, problem, expected users, service region, desired outcome, fixed constraints, avoided methods, and reference-file selection. Only idea overview is required.
- Primary form action is `AI로 아이디어 정리하기`.
- Follow-up questions render as a group of four independent Question Cards, never chat bubbles.
- Question Cards support `FREE_TEXT`, `SINGLE_SELECT`, `MULTI_SELECT`, and `UNDECIDED` through native keyboard-operable form controls.
- Idea Brief review contains the specified business idea, business conditions, and regulatory-sensitive groups and all required fields.
- Internal source values are mapped to `사용자 입력`, `파일에서 추출`, `AI 제안`, and `미정` before rendering.
- Intake fields, selected files, question answers, and editable Brief fields share one domain Draft reducer.
- Screen state is kept separately from domain Draft state and defines Loading, Empty, Ready, Running, Needs Input, Review, Failed, and Confirmed.
- The final action is `이 내용으로 컨셉 만들기`. R2A prepares an immutable confirm request at the adapter boundary but performs no confirm HTTP call and starts no concept job.
- The `/api/v3/projects/{projectId}/idea-brief` GET/derive/fields/answers/confirm adapter surface is defined for R2B connection without coupling UI components to transport code.
- Error summaries, `aria-live` status, field-group headings, native fieldsets and choices, visible focus styles, 44 px controls, reduced-motion handling, and a mobile sticky primary action are included.
- No new import points to `features/conversational-idea`, Journey, Persona, Interview, market-response, feasibility, or marketing-workspace code.

## Checks actually run

- Root `AGENTS.md`, branch, HEAD, clean worktree, required R2A documents, Fast Profile, and relevant repository manifest paths were checked before implementation.
- Initial Targeted Test invocation through `npm` did not start because PowerShell blocked `npm.ps1` under the local execution policy.
- The single allowed retry used `npm.cmd`:
  - `npm.cmd run test:run -- src/features/idea-intake/model/ideaIntakeModel.test.js src/features/idea-intake/components/QuestionCard.test.jsx`
  - Result: 2 test files passed, 2 tests passed.
- Targeted changed-source ESLint:
  - `npm.cmd exec eslint -- src/features/idea-intake src/app/routing/AppRouter.jsx`
  - Result: passed with no findings.
- `git diff --check`: passed after the final documentation update.

## Checks intentionally omitted

- Full frontend lint, baseline, test suite, and production build.
- Backend and AI tests.
- Full `postgresTest` and Testcontainers.
- Docker Compose rebuild.
- Real provider smoke and actual Idea Brief API calls.
- Browser, responsive, and manual accessibility testing.
- Commit and push.

## Remaining risks

- R2A intentionally uses local transitions; live initial loading, derive progress, server failure, retry, persistence, refresh recovery, and confirmed snapshot behavior await R2B/R2C.
- Reference files are held only in local Draft state. Upload, extraction, file-source attribution, size/type policy, and failure handling are not connected.
- The Running state is intentionally brief in the local-only flow; real Job Events will control its duration later.
- Loading and Failed rendering paths exist but are not naturally produced until a live adapter is connected.
- Browser layout, 200% zoom, screen reader announcements, keyboard-only completion, and mobile sticky behavior remain user verification gates.

## Exact continuation point

Stop after R2A. Run `docs/rebuild/verification/R2A_USER_VERIFICATION.md` and resolve any R2A failure. Only after those gates pass may a separately authorized R2B execution begin by connecting the existing adapter and Draft contract to the Idea Brief backend. Do not implement R2B or R2C automatically.
