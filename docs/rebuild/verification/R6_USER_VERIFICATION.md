# R6 Integrated User Verification

Run from `C:\Users\seewo\Desktop\big_proj_01\new_3`.

## 1. Targeted and frontend gates

```powershell
Set-Location backend
.\gradlew.bat test --tests com.aivle.backend.pipeline.marketing.MarketingContentContractsTests
Set-Location ..\ai
.\.venv\Scripts\python.exe -m pytest -q tests/test_marketing_content_contract.py
Set-Location ..\frontEnd
npm.cmd run test:run -- src/features/marketing-content/model/marketingContentModel.test.js src/features/marketing-content/components/MarketingCopyEditor.test.jsx src/features/marketing-content/hooks/useMarketingGeneration.test.jsx
npm.cmd run lint
npm.cmd run build
Set-Location ..
git diff --check
```

Success: backend reports `BUILD SUCCESSFUL`, AI reports `2 passed`, frontend targeted reports 3 files/3 tests, lint/build succeed, and diff check is clean.

## 2. Docker, DB, and Provider

DB reset is not normally required. Confirm Flyway applies V13 once and creates the three `pipeline_marketing_*` tables. If V13 previously failed halfway, collect history/logs before repairing or resetting.

Rebuild `ai-server`, `backend`, and `frontend`; do not rebuild the PostgreSQL image.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build postgres ai-server backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml ps
docker compose -f compose.yaml -f compose.e2e.yaml logs --since=10m postgres ai-server backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml exec ai-server python -m app.tools.marketing_content_provider_smoke
```

Success: services are healthy, V13/Hibernate are clean, and Provider smoke returns exactly `marketing-content-result-v1` with no prohibited claim and required disclosure applied.

## 3. Integrated browser lifecycle

At `/app/projects/<PROJECT_ID>/marketing`, verify in order:

1. With no finalized snapshot, access works, generation alone is disabled, and planning navigation works.
2. With a finalized snapshot, Source Summary is complete and does not require BM, financial, Persona, Validation, or external Market DB fields.
3. Create every content type at least once across the run; confirm status messages and `job.marketing.*` events follow queued → started → source prepared → copy generating → legal checking → completed/failed.
4. Refresh during generation; the list/detail state and terminal result restore without duplicate generation.
5. Edit Headline, body, CTA, hashtags, image brief, and disclosure. Save and confirm a distinct USER revision without overwriting GENERATED.
6. Use short-copy and legal-disclosure partial edit actions. Confirm meaningful labels, not v1/v2, appear.
7. Copy and download; confirm Korean text, CTA, hashtags, image description, and disclosures survive.
8. Generate a new whole draft and confirm prior revisions remain. Finalize and confirm FINALIZED/SYSTEM and final-save state.
9. Finalize a newer planning snapshot and confirm old content displays STALE and cannot be finalized against the wrong source.
10. Insert a prohibited expression and confirm save/finalize are blocked. Confirm ordinary legal warnings remain warnings and required disclosures can be applied.
11. Verify 390×844 mobile, 768×1024 tablet, ≥1280px desktop, 200% zoom, keyboard-only editing, visible focus, reduced motion, and basic screen-reader order.
12. Confirm legacy Marketing Workspace, A/B, Persona satisfaction, Panel Interview, Market Response, campaign experiment, and launch-strategy UI are absent.

## 4. Failure evidence

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs --since=30m postgres ai-server backend frontend > R6_services.log
docker compose -f compose.yaml -f compose.e2e.yaml exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 10;"'
```

Collect sanitized browser console/network output, request/correlation IDs, content and TaskRun IDs, Job Event sequence, migration history, and relevant service logs. Never collect prompts, Provider request/response bodies, authorization, secrets, or full legal/user inputs.

## 5. R6 acceptance and next stage

R6 is accepted only when AI/backend/frontend gates, Provider smoke, Docker health, generation, refresh restoration, partial editing, save/finalize, meaningful revision names, stale source, download, mobile/accessibility, and legal disclosure/blocking all pass. Stop after acceptance and request R7 separately.
