# AGENTS.md — New Pipeline Rebuild

## Active project

This repository is rebuilding the product around the new six-stage pipeline.

The active governing documents are under:

docs/rebuild/

Legacy documents under docs/redesign are not active product contracts.

## Document priority

1. docs/rebuild/CONCEPT_PORTFOLIO_V2_PRODUCTION_CUTOVER_AMENDMENT_v1.0.md (Concept Portfolio V2 Production Cutover scope only)
2. docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md
3. docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md
4. docs/rebuild/NEW_PIPELINE_UI_UX_SPEC_v1.0.md
5. docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md
6. docs/rebuild/EXTERNAL_MODULE_HANDOFF_CONTRACT_v1.0.md
7. docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md
8. docs/rebuild/NEW_PIPELINE_IMPLEMENTATION_PLAN_v1.0.md
9. docs/rebuild/REBUILD_EXECUTION_RULES_v1.0.md
10. docs/rebuild/LOCAL_FAST_EXECUTION_PROFILE.md
11. Current R-stage instruction

Concept Portfolio V2 cutover 범위에서 이 Amendment와 기존 rebuild 문서가 충돌하면 Amendment가 우선한다. 그 외 영역에서는 기존 우선순위를 그대로 적용한다.

If documents conflict, stop and report the conflict.

## Product cutover rules

- Do not add new product code to the legacy journey pipeline.
- New backend product code belongs under a new pipeline package.
- New frontend product code belongs in the new project shell and new feature modules.
- Legacy navigation, routes, and controllers must not be exposed to users.
- R1 removes active legacy exposure immediately.
- R7 removes remaining dead internal code.
- Do not make new code import legacy journey, persona, interview, market-response,
  feasibility, or marketing-workspace modules unless an approved adapter is defined.
- External analysis modules integrate through immutable snapshot contracts.
- Do not restore the conversational workspace.
- Preserve the Idea Brief contract so a future chat adapter can be added.

## Fast execution

Apply docs/rebuild/LOCAL_FAST_EXECUTION_PROFILE.md.

For R0 through R6:

- implementation first
- no full regression suites
- no full postgresTest
- no Docker/browser/provider smoke unless explicitly requested
- no frontend production build unless explicitly requested
- each command should normally finish within five minutes
- run git diff --check
- run at most a small directly-related targeted test where practical
- write exact user verification instructions instead of running heavy gates

## Stage output

Concept Portfolio V2 Production Cutover P0 through P11 follows the Amendment and `docs/rebuild/production-integration` artifact rules; unless separately requested, do not create duplicate progress/verification documents.

Every stage or substage must update:

docs/rebuild/progress/<stage>_RESULT.md

and create:

docs/rebuild/verification/<stage>_USER_VERIFICATION.md

The result must state:

- files changed
- contracts implemented
- checks actually run
- checks intentionally omitted
- remaining risks
- exact continuation point

## Git safety

- Do not switch or create branches.
- Do not reset, clean, revert, stash, commit, or push.
- Preserve unrelated user files.
- Do not alter hooks or bypass tests.
- Stop on overlapping dirty changes.

## Execution boundary

Perform only the requested stage or substage.
Do not automatically continue to the next stage.
