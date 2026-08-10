# PRODUCT-CUTOVER-CLEANUP User Verification

Run from `C:\Users\seewo\Desktop\big_proj_01\new_3` on branch
`rebuild/new-pipeline-v1`. Do not commit or push as part of this verification.

## 1. Fast static and targeted gates

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
node scripts/verify-pipeline-cutover.mjs

Push-Location frontEnd
npm.cmd run test:run -- src/app/routing/AppRouter.cutover.test.js src/shared/async-events/jobEventMessages.test.js src/shared/async-events/JobTimeline.test.jsx
npm.cmd run test:run -- src/test/App.test.jsx
npx.cmd eslint src/app/routing/AppRouter.jsx src/app/routing/AppRouter.cutover.test.js src/app/project-shell/ProjectModulePages.jsx src/features/idea-intake/api/ideaBriefApi.js src/features/idea-intake/components/IdeaIntakeForm.jsx src/features/idea-intake/pages/IdeaIntakePage.jsx src/features/market-integration/pages/MarketIntegrationPage.jsx src/shared/async-events/jobEventMessages.js src/shared/async-events/jobEventMessages.test.js src/shared/async-events/JobTimeline.test.jsx
Pop-Location

git diff --check
```

Expected: configuration reports `5 eligible / 15 inspected / 2 replacement rounds`, 29 frontend
tests pass, ESLint exits with no findings, and `git diff --check` exits successfully.

## 2. Cutover searches

```powershell
rg -n "VITE_CONVERSATIONAL_VALIDATION_WORKSPACE|LegacyPipelineSurface|ProjectStage" backend/src/main ai/app frontEnd/src frontEnd/Dockerfile
rg -n "R2A|후속 단계에서 제공됩니다|개발용 fixture|신규 파이프라인 테스트용" frontEnd/src README.md
rg -n "3개.*Concept|Concept.*3개|적격 3개|후보.*9개|desiredCount.*3|maxInspectedCandidates.*9" README.md .env.example compose.yaml frontEnd/src ai/app docs/rebuild --glob "!*_RESULT.md" --glob "!*_USER_VERIFICATION.md"
rg -n "ProjectModulePlaceholder" frontEnd/src
```

Expected: all four searches return no matches. Historical progress and verification documents are
excluded because they preserve implementation history rather than define the active UI.

## 3. Compose and browser verification

These heavier commands were not run by the implementation agent:

```powershell
docker compose config
docker compose build backend frontend
docker compose up -d backend frontend
docker compose ps
```

Sign in, open one owned project, then directly open each route:

```text
/app/projects/<PROJECT_ID>/idea
/app/projects/<PROJECT_ID>/concepts
/app/projects/<PROJECT_ID>/concepts/compare
/app/projects/<PROJECT_ID>/market
/app/projects/<PROJECT_ID>/business-persona-test
/app/projects/<PROJECT_ID>/marketing
```

Verify:

1. Idea, Concept, Selection, and Marketing show their actual state screens, never a “later stage”
   Placeholder.
2. Market says `외부 시장분석 연결이 아직 준비되지 않았습니다.` when no external result exists.
3. BM·financial and Persona areas show external `NOT_CONNECTED` state without claiming results.
4. A `NOT_READY` or `NEEDS_INPUT` badge does not prevent direct page navigation; only execution
   actions enforce prerequisites.
5. Idea confirmation says `아이디어를 정리했습니다.` and offers the current concept workflow.
6. Concept completion copy refers to five verified concepts, and no active screen promises three.
7. The Job Timeline displays only Event messages emitted by current workers. Unknown or archived
   Event keys do not appear as fabricated progress.
8. Opening a former URL such as `/app/projects/<PROJECT_ID>/legal` redirects to the current Concept
   page and never renders a Journey screen.

## 4. OpenAPI and provider-smoke user gates

Validate `docs/api/openapi.yaml` with the team's installed OpenAPI 3.1 validator. Then, with real
provider credentials configured, run only the required smoke commands:

```powershell
docker compose exec -T ai-server python -m app.tools.idea_brief_provider_smoke
docker compose exec -T ai-server python -m app.tools.concept_factory_provider_smoke
docker compose exec -T ai-server python -m app.tools.marketing_content_provider_smoke
```

The smoke output must not expose secrets, prompts, raw provider bodies, raw user input, or full legal
text. Smoke success does not replace Query API and browser acceptance.

## 5. Database note

This unit changes no migration and does not itself run a reset. After this unit passes, perform the
one clean DB reset required by the Legal Evidence hardening verification before final integrated
runtime acceptance. Do not apply the V1 baseline over a database containing prior migration history.
