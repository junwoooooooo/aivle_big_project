# Final User Acceptance

## Gate ledger from supplied verification

| Gate | Supplied result | R7B disposition |
|---|---|---|
| Initial `git status --short` | Pass, clean | Recorded |
| Initial `git diff --check` | Pass | Recorded |
| Backend `clean test postgresTest` | Failed at test compilation | Evidence-based fixes applied; rerun required |
| AI pytest | 25 passed, one deprecation warning | Pass |
| Frontend lint | Pass | Pass |
| Frontend baseline | Failed on stale allowlist | Allowlist corrected; rerun required |
| Frontend production build | Failed unresolved import | Import corrected; rerun required |
| Compose config | Pass | Pass; service is `ai-server` |
| Clean volume reset | Initially daemon failure, later succeeded | Pass according to later output |
| Docker service health | Five services healthy | Pass for reported run |
| Static Legacy search | Active text search had no matches | Pass; binary `__pycache__` match is not active source |
| Browser/Network/Screenshot | Not supplied | Not run / blocking |
| Provider Smoke | Not supplied | Not run / blocking |
| Mobile/accessibility | Not supplied | Not run / blocking |

## Commands to rerun

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3
git diff --check
cd backend
.\gradlew.bat clean test postgresTest --no-daemon
cd ..\frontEnd
npm run lint
npm run test:baseline
npm run build
cd ..
docker compose config
docker compose build backend ai-server frontend
docker compose up -d
docker compose ps
```

Backend success requires `compileTestJava`, unit tests and clean-PostgreSQL tests to complete without
restoring any deleted Legacy type. Baseline success requires no new or stale allowlist entry. Build
success requires Vite to resolve every active import.

If `rg` is not installed, use source-file-only PowerShell searches so ignored `__pycache__` binaries
do not create false positives:

```powershell
Get-ChildItem backend/src/main,ai/app,frontEnd/src -Recurse -File -Include *.java,*.py,*.js,*.jsx,*.yaml | Select-String -Pattern 'LegacyPipelineSurface|app\.legacy-pipeline|ProjectStage|IDEA_INTERPRETATION|IDEA_CONVERSATION_TURN|QUICK_ASSESSMENT|DETAILED_ANALYSIS|PERSONA_CARD_GENERATION|PERSONA_INTERVIEW|INTERVIEW_SYNTHESIS|MARKETING_COMPARISON|FINAL_REPORT_GENERATION'
Get-ChildItem frontEnd/src -Recurse -File -Include *.js,*.jsx | Select-String -Pattern 'features/(journey|conversational-idea|concept-workboard|feasibility|legal-review|personas|report|structured-plan|validation|financial|marketing-workspace)|projectWorkflowModel|/journey'
```

Both searches must return no active-source match.

## Final browser scenarios

- Desktop, tablet and 390×844 mobile: signup/login, project create/list/detail.
- Exactly the new shell routes are visible: overview, Idea Brief, Concept Factory, comparison,
  market handoff, business/persona external shell, marketing and settings.
- Idea Brief input, questions and confirmation work.
- Concept Factory produces five slots and five legally eligible concepts reach comparison.
- Selection creates an immutable snapshot and Market Handoff uses it.
- Planning change decisions produce a Finalized Snapshot.
- BM/financial and Persona remain external shells without a restored Legacy workspace.
- Marketing content generates, edits, saves, regenerates and finalizes.
- TaskRun/JobEvent/SSE resumes after refresh without duplicate/out-of-order UI state.
- Former Journey URLs never render a Legacy page.
- Keyboard navigation, focus visibility, aria-live/error announcements, 200% zoom, reduced motion and
  color contrast meet acceptance criteria.

Capture screenshots and Browser Network responses for the start/completion calls, TaskRun/JobEvent
stream, module handoff/result, planning finalize and marketing save.

## Clean database and Docker

A clean PostgreSQL database is required. Confirm Flyway applies only
`V1__new_pipeline_baseline.sql`, then collect:

```powershell
docker compose logs --no-color postgres backend ai-server frontend > r7-final-compose.log
docker compose exec postgres psql -U aivle -d aivle -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

## Provider gate

Run the approved synthetic smoke for each of the five active AI task types and retain safe error
type/parameter evidence. Never attach API keys, tokens, passwords, raw prompts or provider payloads.

## Known limitations and external modules

Market analysis, BM/financial and Persona are external-module integrations. Their Not Connected or
fixture shell is acceptable; pretending they executed internally is not. The final product acceptance
remains open until real handoff/result contract behavior is verified.

## Deployment blockers

- Corrected backend/frontend gates must be rerun Green.
- Browser, responsive, accessibility and Provider evidence must be supplied.
- The shared verification log exposed credential material. Rotate every exposed API key, internal
  token, JWT secret and password before deployment, then verify Compose uses newly injected secrets.
- Commit/push remains the user’s responsibility.
