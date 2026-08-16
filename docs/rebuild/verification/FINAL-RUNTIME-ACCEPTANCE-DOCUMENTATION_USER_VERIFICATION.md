# FINAL-RUNTIME-ACCEPTANCE-DOCUMENTATION User Verification

This verification checks the documentation unit only. It does not execute or waive the runtime
acceptance gates documented by the unit.

Run from `C:\Users\seewo\Desktop\big_proj_01\new_3`:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short

Test-Path docs/rebuild/FINAL_RUNTIME_ACCEPTANCE_PLAN.md
Test-Path docs/rebuild/FINAL_RUNTIME_ACCEPTANCE_CHECKLIST.md

$Plan = Get-Content docs/rebuild/FINAL_RUNTIME_ACCEPTANCE_PLAN.md -Raw -Encoding UTF8
$Checklist = Get-Content docs/rebuild/FINAL_RUNTIME_ACCEPTANCE_CHECKLIST.md -Raw -Encoding UTF8

@(
  'Gate 1 — Static contract',
  'Gate 2 — Backend compilation, contracts, PostgreSQL',
  'Gate 3 — AI schemas, task alignment, real provider smoke',
  'Gate 4 — Frontend lint, tests, production build',
  'Gate 5 — Destructive clean database and Docker startup',
  'Gate 6 — Browser end-to-end success path',
  'Gate 7 — Async, replay, restart, and terminal invariants',
  'Gate 8 — Responsive UI and accessibility',
  'Gate 9 — Security and data minimization',
  'Failure evidence and redaction procedure'
) | ForEach-Object { if (-not $Plan.Contains($_)) { throw "Missing plan section: $_" } }

@('G1 Static','G2 Backend','G3 AI and providers','G4 Frontend','G5 Clean DB and Docker','G6 Browser E2E','G7 Async and failure behavior','G8 UI and accessibility matrix','G9 Security and minimization','Final decision') |
  ForEach-Object { if (-not $Checklist.Contains($_)) { throw "Missing checklist section: $_" } }

git diff --check
```

Success criteria:

- both required final documents exist;
- all nine gates, failure evidence procedure, checklist sections, and final decision are present;
- `git diff --check` exits successfully;
- only documentation files changed during this unit;
- no checklist item is pre-checked and no runtime acceptance is claimed.

After this documentation verification passes, stop. Runtime execution starts separately at Gate 1
of the final plan and must stop at its first failed criterion.
