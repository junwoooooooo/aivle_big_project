# R2A User Verification — Idea Intake UI

## Preconditions

- Repository root: `C:\Users\seewo\Desktop\big_proj_01\new_3`
- Expected branch: `rebuild/new-pipeline-v1`
- Use an authenticated account that can access a project and record its ID as `<PROJECT_ID>`.
- No new npm dependency was added. If `frontEnd/node_modules` is missing, run `npm.cmd ci` from `frontEnd` first.

## Commands to run

Run the focused R2A tests from `frontEnd`:

```powershell
npm.cmd run test:run -- src/features/idea-intake/model/ideaIntakeModel.test.js src/features/idea-intake/components/QuestionCard.test.jsx
```

Success: Vitest reports 2 test files passed and 2 tests passed.

Run the full user-owned frontend gates from `frontEnd`:

```powershell
npm.cmd run lint
npm.cmd run build
```

Success: both commands exit with code 0. ESLint reports no errors, and Vite completes the production build without unresolved imports or syntax errors.

Start the frontend for browser verification:

```powershell
npm.cmd run dev
```

Success: Vite prints a local URL and the application opens without a compile overlay.

## Browser checks

Open `/app/projects/<PROJECT_ID>/idea` directly.

Initial form:

- The R1 project shell remains visible and the current module is Idea/아이디어 정리.
- No legacy Journey stepper, chat bubble, message composer, conversational workspace, Plan/Review/Validate/Report navigation, or legacy page appears.
- All eight requested inputs are present, including a multiple-file picker.
- Submitting with an empty idea overview shows an Error Summary whose link moves to the required field.
- Only idea overview is required; optional fields may remain empty.
- Typing an overview changes the form into a ready Draft without navigating away.
- The primary action reads `AI로 아이디어 정리하기`.

Follow-up questions:

- Submitting a valid overview displays exactly four Question Cards together.
- Cards are not visually represented as chat messages.
- Free text, single select, multi-select, and `아직 결정하지 않음` controls are all present.
- Submitting incomplete answers shows a linked Error Summary.
- Keyboard-only use can focus, select, toggle, type in, and submit every card with a clearly visible focus indicator.

Brief review and confirmation:

- Completing the questions opens the Idea Brief review.
- Group headings are `사업 아이디어`, `사업 조건`, and `규제 민감 정보`.
- Every R2A-specified field appears under the correct heading.
- Source badges use only `사용자 입력`, `파일에서 추출`, `AI 제안`, or `미정`; internal enum strings are never visible.
- Editing a Brief field immediately preserves the new value in the same Draft flow.
- The final action reads `이 내용으로 컨셉 만들기`.
- Clicking it shows the R2A confirmed state but sends no Idea Brief confirm request, starts no concept job, and does not navigate to a future-stage UI.
- Browser Network contains no POST request to `/api/v3/projects/<PROJECT_ID>/idea-brief/confirm` during this R2A flow.

## Responsive and accessibility checks

At 390 × 844 and 768 × 1024:

- Form, question, and review grids collapse to one column with no horizontal page overflow.
- The primary action remains visible as a bottom sticky action and does not cover the final form control.
- Touch targets are at least approximately 44 px high.

At 1280 px or wider:

- The form and question cards use the available project-shell content width without exceeding it.
- Two-column layouts remain readable and long Korean text wraps without clipping.

Also verify:

- 200% browser zoom does not hide content or actions.
- A screen reader announces validation errors and state changes through the Error Summary and `aria-live` regions.
- Field group headings provide a useful navigation outline.
- Status is not communicated by color alone.
- Reduced-motion preference does not create required motion or animated transitions.

## Database initialization

Database initialization required: **No**. R2A changes frontend source and documentation only. No migration, table, seed, or persisted Idea Brief data was added.

## Docker rebuild

Required for a normal local Vite run: **No service**.

If frontend source is baked into the Compose image rather than bind-mounted, rebuild only the actual `frontend` service from the repository root:

```powershell
docker compose build frontend
docker compose up -d frontend
```

Do not rebuild `backend`, `ai-server`, `postgres`, `minio`, or `minio-init` for R2A.

## Logs to collect on failure

Collect:

- The failing URL, `<PROJECT_ID>`, viewport size, and exact interaction sequence.
- Browser console errors and stack traces.
- Relevant Browser Network entries, including method, status, and response body. Remove tokens, cookies, and secrets.
- Terminal output from `npm.cmd run dev`, the focused tests, full lint, and build.
- If Docker is used:

```powershell
docker compose logs --tail=200 frontend
```

- A screenshot for clipping, sticky-action, focus, source-label, or grouping defects.
- Screen reader/browser name and version for announcement defects.

## Next-stage condition

R2B may begin only after the focused tests, lint, build, full browser flow, keyboard-only flow, responsive checks, and source-label checks pass; no legacy conversational or Journey UI is exposed; and confirmation produces no premature backend request. Any failure must be resolved within R2A before backend integration starts.
