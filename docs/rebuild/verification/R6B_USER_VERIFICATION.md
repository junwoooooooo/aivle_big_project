# R6B User Verification — Marketing Content Frontend

Run from `C:\Users\seewo\Desktop\big_proj_01\new_3`.

## Commands

```powershell
Set-Location frontEnd
npm.cmd run test:run -- src/features/marketing-content/model/marketingContentModel.test.js src/features/marketing-content/components/MarketingCopyEditor.test.jsx src/features/marketing-content/hooks/useMarketingGeneration.test.jsx
npm.cmd run lint
npm.cmd run build
Set-Location ..
git diff --check
```

Success criteria: three targeted files/tests pass, full lint has no errors, production build succeeds, and diff check reports no whitespace errors.

R6B itself needs no DB reset or new DB migration. For an integrated environment, V13 from R6A must already be applied. Rebuild `frontend`; rebuild `backend` and `ai-server` too if R6A images have not already been rebuilt. PostgreSQL needs no image rebuild.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build postgres ai-server backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml ps
```

## Browser verification

Open `/app/projects/<PROJECT_ID>/marketing` and verify:

- without finalized planning, the page and existing content list remain accessible, generation is disabled, and `기획 확정으로 이동` works;
- desktop ≥1280px shows setup/source left, preview/revisions center, and style/editor/legal controls right;
- mobile 390×844 shows source → setup → preview → edit and a keyboard-reachable sticky save/download area;
- source summary shows the requested planning, target, value, positioning, features, channels, differentiators, claims, prohibited expressions, disclosures, and snapshot date;
- setup sends no Persona/BM/financial/Validation fields and supports type, channel, purpose, tone, length, CTA, include/exclude phrases, and instructions;
- refresh during QUEUED/RUNNING restores the content through list/detail and eventually reaches a terminal status;
- Headline, body, CTA, hashtag, image description, and disclosures are keyboard-editable;
- shortening and disclosure application save with the correct meaningful revision names; generated, user, and finalized origins stay distinct;
- prohibited expressions create a blocking panel and prevent save/finalize, while disclosure/AI warnings are non-blocking warnings;
- copy and UTF-8 text download preserve Korean, hashtags, image brief, and disclosures;
- whole-content `새 초안 생성`, edit save, and final save all refresh the list and revision panel;
- a changed finalized planning source marks older content as Stale;
- 768×1024, 200% zoom, visible focus, reduced motion, and screen-reader heading/label order remain usable;
- no A/B test, Persona validation, Panel Interview, Market Response, BM, financial prerequisite, or legacy Marketing Workspace UI appears.

## Failure logs

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs --since=20m frontend backend ai-server > R6B_services.log
```

Collect browser console output, failing request URL/method/status, sanitized response, request/correlation ID, content ID, TaskRun ID, and latest Job Event sequence. Do not capture authorization headers, prompts, Provider bodies, secrets, or full legal/user input.

## Next-stage condition

Proceed only when targeted/full frontend gates pass, the live `/marketing` lifecycle and refresh restoration pass, revision names and legal states are correct, and mobile/accessibility/download checks pass. Then run the integrated R6 verification; do not start R7 automatically.
