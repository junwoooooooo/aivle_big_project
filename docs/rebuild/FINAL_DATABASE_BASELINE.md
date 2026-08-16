# Final Database Baseline

## Decision

R7A creates a clean-install-only Flyway baseline at
`backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`.
There is no retained production data and no supported in-place upgrade from the legacy chain.

## Construction evidence

1. Backend Legacy cleanup completed and `compileJava` was Green.
2. All maintained `@Entity` classes were enumerated in `FINAL_ENTITY_TABLE_INVENTORY.md`.
3. Foundation tables were written from current entity mappings.
4. New-pipeline DDL from the completed R1–R6 migrations was consolidated.
5. Obsolete generic/legacy scaffold tables and all legacy domain tables were excluded.
6. Old V1–V13 files were removed only after the new V1 existed and was statically compared.

## Included domains

- auth/user and refresh token
- project and stored file
- audit and service/admin settings
- TaskRun, TaskAttempt, TaskResult, JobEvent
- Idea Brief, fields, questions, answers, attachment join
- legal context/evidence
- Concept Factory run, slot, attempt, concept, assessment/link/rejection summary
- concept selection and immutable selection snapshot
- module handoff, run, market result and change proposal
- planning decision, snapshot, finalized snapshot
- marketing content, revision and asset

## Excluded domains

Document/structured plan, old legal analysis, feasibility, financial analysis, Journey,
persona/interview/validation, reports, simulation, AI task artifact, and legacy marketing workspace.

## Operational rule

Delete/recreate the local database or Docker PostgreSQL volume before first use. Do not run this
baseline against a database containing the former migration history. PostgreSQL/Flyway validation
was intentionally delegated to user verification; R7A performed static DDL checks only.
