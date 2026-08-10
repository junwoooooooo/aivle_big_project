# R1A User Verification — Frontend Active Surface Cutover

## Preconditions

- Repository root: `C:\Users\seewo\Desktop\big_proj_01\new_3`
- Expected branch: `rebuild/new-pipeline-v1`
- Use an account that can access at least one project and record its project ID as `<PROJECT_ID>`.

## npm installation

No new dependency was added, and `frontEnd/node_modules` was present during R1A. A new `npm install` is not required when the existing installation is intact.

If dependencies are missing or stale, run from `frontEnd`:

```powershell
npm.cmd ci
```

Success: installation completes without changing application dependency declarations.

## Targeted route/module test

Run from `frontEnd`:

```powershell
npm.cmd run test:run -- src/app/module-status/projectModuleModel.test.js
```

Success: one test file and two tests pass. The canonical eight routes and all nine required module statuses are verified.

## User-run lint

Run from `frontEnd`:

```powershell
npm.cmd run lint
```

Success: ESLint exits with code 0 and reports no errors.

## User-run production build

Run from `frontEnd`:

```powershell
npm.cmd run build
```

Success: Vite completes a production build and writes the expected `dist` output without unresolved imports or syntax errors.

## Browser verification — eight canonical routes

Start the existing frontend development environment, sign in, replace `<PROJECT_ID>`, and open each URL directly:

1. `/app/projects/<PROJECT_ID>/overview`
2. `/app/projects/<PROJECT_ID>/idea`
3. `/app/projects/<PROJECT_ID>/concepts`
4. `/app/projects/<PROJECT_ID>/concepts/compare`
5. `/app/projects/<PROJECT_ID>/market`
6. `/app/projects/<PROJECT_ID>/business-persona-test`
7. `/app/projects/<PROJECT_ID>/marketing`
8. `/app/projects/<PROJECT_ID>/settings`

Also open `/app/projects/<PROJECT_ID>` and confirm it replaces the URL with `/app/projects/<PROJECT_ID>/overview`.

Success on every route:

- The project shell renders without a blank page.
- The project title and current module title are correct.
- Status is shown with both text and color.
- The matching navigation item has `aria-current="page"`.
- Unimplemented modules show status, required inputs, and a usable navigation action.
- No execution/generation action is offered.

## Direct legacy route checks

Open each route group directly and confirm it redirects to a canonical new route without briefly or finally rendering a legacy page:

- `/app/projects/<PROJECT_ID>/legal`
- `/app/projects/<PROJECT_ID>/journey/concept`
- `/app/projects/<PROJECT_ID>/journey/concept-analysis`
- `/app/projects/<PROJECT_ID>/journey/concept-selection`
- `/app/projects/<PROJECT_ID>/journey/persona`
- `/app/projects/<PROJECT_ID>/journey/interview`
- `/app/projects/<PROJECT_ID>/journey/marketing`
- `/app/projects/<PROJECT_ID>/journey/final-report`
- `/app/projects/<PROJECT_ID>/plan` and representative `/plan/**` child URL
- `/app/projects/<PROJECT_ID>/review` and representative `/review/**` child URL
- `/app/projects/<PROJECT_ID>/validate` and representative `/validate/**` child URL
- `/app/projects/<PROJECT_ID>/validation` and representative `/validation/**` child URL
- `/app/projects/<PROJECT_ID>/report` and representative `/report/**` child URL
- Representative old `/projects/<PROJECT_ID>/**` URLs for documents, structured plan, legal review, feasibility, financial, personas, panel survey/discussion, market validation, report, marketing, and settings

Success: only overview, idea, concepts, concepts/compare, market, business-persona-test, marketing, or settings is rendered. No legacy Journey stepper, standalone legal stage, Persona/Interview/final-report navigation, or Plan/Review/Validate/Report area appears.

## Desktop checks

At a viewport width of 1280 px or wider, verify:

- Left navigation remains clearly fixed while project content scrolls.
- Navigation order exactly matches project overview, stages 1–6, and project settings.
- The project title is visually 24–28 px and current module title is 18–20 px.
- The task-center entry reaches the task-center status area.
- Keyboard Tab order reaches every navigation link, task center, and project settings.
- Focus is visible and the active route is announced through `aria-current`.
- Opening project settings from the header preserves the overlay behavior and returns to the prior module when closed.

## Mobile checks

At 390 × 844 px, verify:

- The desktop sidebar is not shown in compressed form.
- A current-stage selector is visible and changes routes.
- Previous/next controls move between adjacent modules.
- Project settings remains reachable.
- Content is single-column with no horizontal page overflow.
- Interactive controls have practical touch targets and keyboard focus remains visible.

Repeat the basic layout check at 768 × 1024 px.

## Database and Docker

- Database initialization required: **No**. R1A contains no backend, schema, or Migration change.
- Docker rebuild required: **No service**. R1A changes frontend source only.
- If the local frontend runs in a Docker image that does not bind-mount source, rebuild only that local frontend service according to the repository's existing Compose profile; no backend, AI, or database image rebuild is required.

## Logs to collect on failure

Collect:

- The failing URL and final redirected URL.
- Browser console errors with stack traces.
- Browser Network entries for the project detail request, including status code and response body.
- The terminal output from `npm.cmd run dev` or the frontend container logs if Docker is used.
- Output from the targeted test, full lint, and production build commands above.
- Viewport dimensions and a screenshot for layout or navigation defects.

Do not include access tokens, cookies, passwords, or other secrets in collected logs.

## Next-stage condition

R1B may begin only after all eight canonical routes, the base redirect, representative legacy redirects, desktop/mobile layouts, authentication, project context loading, project settings behavior, and admin routes pass the checks above. If any legacy page renders or active navigation depends on the backend `project.stage`, stop and fix R1A before proceeding.
