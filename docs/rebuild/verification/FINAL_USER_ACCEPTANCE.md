# Final User Acceptance

## Gate ledger from supplied verification

| Gate | Supplied result | R7B disposition |
|---|---|---|
| Branch and HEAD | `rebuild/new-pipeline-v1`, `5e3c13b...` | Pass |
| Backend targeted Project API | `ProjectApiIntegrationTests` passed, BUILD SUCCESSFUL | Pass |
| Backend `clean test postgresTest` | Compile and unit tests passed; `postgresTest` ran 19 tests with 12 stale pre-baseline/Legacy-test failures | Tests consolidated; full `postgresTest` rerun required |
| Targeted final-baseline PostgreSQL tests | Baseline migration and container smoke classes passed, BUILD SUCCESSFUL in 36s | Pass |
| AI pytest | 25 passed, one deprecation warning | Pass |
| Frontend lint | Pass | Pass |
| Frontend targeted auth/project flow | 2 passed | Pass |
| Frontend baseline | 129 passed, 6 explicitly allowed failures, 0 unexpected failures | Pass; allowed failures remain known debt |
| Frontend production build | Pass, 245 modules | Pass; chunk-size warning is non-blocking |
| Compose config | Pass | Pass |
| Docker image build | Backend, AI server, and frontend built | Pass |
| Docker startup and health | All five services healthy | Pass according to user evidence |
| Static Legacy search | No active-source matches | Pass |
| Browser/Network/Screenshot | Not supplied | Not run / blocking |
| Provider Smoke | Not supplied | Not run / blocking |
| Mobile/accessibility | Not supplied | Not run / blocking |

## Remaining automated rerun

The corrected Project and frontend tests are already Green. Rerun the complete PostgreSQL task to
cover the remaining previously passing classes together with the consolidated baseline tests:

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3\backend
.\gradlew.bat postgresTest --no-daemon
```

Success means the task exits 0 with no failing PostgreSQL test. If it fails, retain the full output
and `backend/build/test-results/postgresTest` XML files. Then reconfirm the lightweight gates:

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3
git diff --check
cd frontEnd
npm run lint
npm run test:baseline
cd ..
docker compose config
docker compose ps
```

Backend success requires clean-PostgreSQL tests to pass without restoring deleted Legacy migrations,
tables, or constraints. Frontend baseline success remains 0 unexpected failures.

## Static Legacy surface gate

```powershell
Get-ChildItem backend/src/main,ai/app,frontEnd/src -Recurse -File -Include *.java,*.py,*.js,*.jsx,*.yaml | Select-String -Pattern 'LegacyPipelineSurface|app\.legacy-pipeline|ProjectStage|IDEA_INTERPRETATION|IDEA_CONVERSATION_TURN|QUICK_ASSESSMENT|DETAILED_ANALYSIS|PERSONA_CARD_GENERATION|PERSONA_INTERVIEW|INTERVIEW_SYNTHESIS|MARKETING_COMPARISON|FINAL_REPORT_GENERATION'
Get-ChildItem frontEnd/src -Recurse -File -Include *.js,*.jsx | Select-String -Pattern 'features/(journey|conversational-idea|concept-workboard|feasibility|legal-review|personas|report|structured-plan|validation|financial|marketing-workspace)|projectWorkflowModel|/journey'
```

Both searches must return no active-source match.

## Final browser scenarios

- Desktop, tablet, and 390x844 mobile: signup/login and project create/list/detail.
- New project shell exposes overview and the six active routes: Idea Brief, Concept Factory,
  comparison, market/planning, business-persona external shell, and marketing.
- Former Journey URLs never render Legacy UI.
- Idea Brief input, questions, confirmation; five Concept slots and five legally eligible concepts;
  comparison/selection; immutable Market Handoff; planning changes and Finalized Snapshot.
- BM/financial and Persona remain external shells without a restored Legacy workspace.
- Marketing content generation, edit, save, regenerate, and finalize.
- TaskRun/JobEvent/SSE refresh recovery without duplicate or out-of-order state.
- Keyboard navigation, visible focus, aria-live/error announcements, 200% zoom, reduced motion, and
  core contrast checks.

Capture screenshots and Browser Network responses for start/completion calls, TaskRun/JobEvent SSE,
module handoff/result, planning finalize, and marketing save.

## Clean database and Docker

A clean PostgreSQL database is required because R7A consolidated migrations into a new baseline.
Targeted Testcontainers verification confirms V1 applies and validates on an empty schema. The full
`postgresTest` task must still be rerun, and the Compose database must show only
`V1__new_pipeline_baseline.sql` in Flyway history.

Docker images do not need another rebuild for the final two test-only assertion changes. Rebuild
backend, AI server, or frontend only if product/runtime source changes after this document.

Collect on failure:

```powershell
docker compose logs --no-color postgres backend ai-server frontend > r7-final-compose.log
docker compose exec postgres psql -U aivle -d aivle -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

Also retain the failing command, test class/case, backend test XML, browser console, Network response,
SSE frames, screenshot, and relevant service logs. Remove keys, tokens, passwords, raw prompts, and
provider payloads before sharing.

## Provider gate

Run the approved synthetic smoke for each active AI task type and retain safe error type/parameter
evidence. Provider Smoke has not been supplied and remains blocking.

## Known limitations and external modules

Market analysis, BM/financial, and Persona are external-module integrations. A Not Connected or
fixture shell is acceptable; claiming internal execution is not. Final acceptance remains open until
real handoff/result behavior is verified.

## Deployment and next-stage conditions

- Full backend `postgresTest` is Green after the baseline-test consolidation.
- Corrected targeted Project/frontend tests and frontend baseline remain Green.
- Clean Flyway history contains only the final baseline and expected schema.
- Browser, responsive, accessibility, SSE, and Provider evidence is supplied.
- Legacy active surface search remains zero.
- Rotate every credential exposed in shared verification output before deployment.
- Commit and push remain the user's responsibility.
