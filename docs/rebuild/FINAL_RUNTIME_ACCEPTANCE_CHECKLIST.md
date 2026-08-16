# Final Runtime Acceptance Checklist

Use with `FINAL_RUNTIME_ACCEPTANCE_PLAN.md`. Check an item only from evidence produced for the same
candidate.

## Candidate record

- [ ] Branch is `rebuild/new-pipeline-v1`
- [ ] HEAD: `______________________________`
- [ ] Acceptance ID: `______________________________`
- [ ] Tester/date: `______________________________`
- [ ] Evidence directory: `______________________________`
- [ ] Provider/model identifiers recorded without credentials

## G1 Static

- [ ] G1.1 `git diff --check` passed
- [ ] G1.2 active legacy imports are zero
- [ ] G1.3 active direct routes map to current module screens
- [ ] G1.4 implemented modules do not render Placeholder screens
- [ ] G1.5 pages remain directly accessible regardless of readiness
- [ ] G1.6 Concept limits are 5 eligible / 15 inspected / 2 replacements
- [ ] G1.7 `docker compose config --quiet` passed
- [ ] G1.8 OpenAPI YAML lint passed
- [ ] G1.9 active JSON files parsed

Failure/stop record: `____________________________________________________________`

## G2 Backend

- [ ] G2.1 `compileJava` passed
- [ ] G2.2 scheduling, SSE/polling, active-job tests passed
- [ ] G2.3 Idea catalog/readiness/answer/snapshot tests passed
- [ ] G2.4 Concept 5-slot/limit/state/worker/legal tests passed
- [ ] G2.5 Selection/Handoff/Market/Planning/Module tests passed
- [ ] G2.6 Marketing contract/worker tests passed
- [ ] G2.7 TaskRun terminal invariant tests passed
- [ ] G2.8 PostgreSQL V1 baseline Testcontainers test passed

Failure/stop record: `____________________________________________________________`

## G3 AI and providers

- [ ] G3.1 Python compileall passed
- [ ] G3.2 internal five-task alignment passed
- [ ] G3.3 Idea, Concept/legal, Evidence, and Marketing schema tests passed
- [ ] G3.4 real Idea Brief provider smoke passed
- [ ] G3.5 real Concept + official Evidence provider smoke passed
- [ ] G3.6 real Marketing provider smoke passed
- [ ] G3.7 smoke output contained no prohibited data

Failure/stop record: `____________________________________________________________`

## G4 Frontend

- [ ] G4.1 full lint passed
- [ ] G4.2 named routing/async/Job Center tests passed
- [ ] G4.3 Idea/Concept/Selection/external shell/Marketing tests passed
- [ ] G4.4 production build passed
- [ ] G4.5 tests show Event dedupe and terminal Query refresh

Failure/stop record: `____________________________________________________________`

## G5 Clean DB and Docker

- [ ] G5.1 destructive volume removal was explicitly approved
- [ ] G5.2 `docker compose down -v` completed
- [ ] G5.3 backend, ai-server, and frontend images built
- [ ] G5.4 postgres, MinIO, ai-server, backend, frontend are healthy
- [ ] G5.5 minio-init completed successfully
- [ ] G5.6 Flyway history has exactly one successful row
- [ ] G5.7 applied script is only `V1__new_pipeline_baseline.sql`
- [ ] G5.8 startup has no migration/schema/task-type mismatch

Failure/stop record: `____________________________________________________________`

## G6 Browser E2E

### Account and project

- [ ] G6.1 signup succeeded
- [ ] G6.2 logout/login succeeded
- [ ] G6.3 project creation succeeded

### Idea Brief

- [ ] G6.4 synthetic Idea submitted
- [ ] G6.5 real AI derivation and Job Events observed
- [ ] G6.6 refresh during DERIVING restored the same job
- [ ] G6.7 follow-up answers updated canonical fields
- [ ] G6.8 summary/provenance/missing/contradictions/readiness displayed
- [ ] G6.9 clarification round remained bounded
- [ ] G6.10 confirmed immutable Idea Brief snapshot recorded

### Concept and legal evidence

- [ ] G6.11 exactly five Slot cards displayed
- [ ] G6.12 each Slot showed actual worker progress
- [ ] G6.13 refresh restored the same Concept run
- [ ] G6.14 details remained hidden until five eligible concepts were ready
- [ ] G6.15 five eligible concepts were revealed together
- [ ] G6.16 failed/rejected drafts were not published
- [ ] G6.17 each material legal finding had official Evidence
- [ ] G6.18 law/article/effective date/official link and limitation displayed
- [ ] G6.19 comparison and immutable selection snapshot succeeded

### Market and planning

- [ ] G6.20 Market Handoff used the selected snapshot
- [ ] G6.21 E2E-only local stub was received and clearly labelled non-real analysis
- [ ] G6.22 every change proposal was decided
- [ ] G6.23 FinalizedPlanningSnapshot ID/hash recorded

### External shell and Marketing

- [ ] G6.24 BM/financial + Persona shell was directly accessible
- [ ] G6.25 external modules remained NOT_CONNECTED without results
- [ ] G6.26 Marketing required FinalizedPlanningSnapshot but not BM/Persona results
- [ ] G6.27 real Marketing Events displayed
- [ ] G6.28 refresh restored content and active job
- [ ] G6.29 user edit created a revision
- [ ] G6.30 regeneration created a separate revision
- [ ] G6.31 prohibited claim blocking and required disclosures worked
- [ ] G6.32 finalization persisted
- [ ] G6.33 UTF-8 `.txt` download contents verified
- [ ] G6.34 no unsupported image artifact was claimed

Failure/stop record: `____________________________________________________________`

## G7 Async and failure behavior

- [ ] G7.1 Idea refresh recovery passed
- [ ] G7.2 Concept refresh recovery passed
- [ ] G7.3 Marketing refresh recovery passed
- [ ] G7.4 SSE disconnect triggered bounded polling fallback
- [ ] G7.5 polling used backoff and hidden-tab slowdown
- [ ] G7.6 Last-Event-ID replay returned only later sequences
- [ ] G7.7 rendered/DB Event sequences had no duplicates
- [ ] G7.8 terminal Event triggered authoritative Query refresh
- [ ] G7.9 queued TaskRun restart recovery used normal claim path
- [ ] G7.10 retryable dependency failure terminated within bounds
- [ ] G7.11 permanent provider failure was terminal and non-retryable
- [ ] G7.12 natural NEEDS_INPUT was terminal/non-claimable
- [ ] G7.13 stale RUNNING audit returned zero rows
- [ ] G7.14 TaskRun, attempt, domain, and Event terminal states agreed

Failure/stop record: `____________________________________________________________`

## G8 UI and accessibility matrix

| Check | 1280+ | 768 | 390×844 | 200% zoom |
|---|---:|---:|---:|---:|
| Direct module navigation | [ ] | [ ] | [ ] | [ ] |
| Idea input/questions/review | [ ] | [ ] | [ ] | [ ] |
| Concept Workboard/Evidence | [ ] | [ ] | [ ] | [ ] |
| Compare/select/Market proposals | [ ] | [ ] | [ ] | [ ] |
| Job Center | [ ] | [ ] | [ ] | [ ] |
| Marketing edit/actions/download | [ ] | [ ] | [ ] | [ ] |

- [ ] G8.1 keyboard-only flow completed
- [ ] G8.2 visible focus and logical order verified
- [ ] G8.3 dialog/sheet focus return and no trap verified
- [ ] G8.4 aria-live announcements were timely and not duplicated
- [ ] G8.5 reduced motion removed nonessential animation without hiding state
- [ ] G8.6 no clipped, overlapped, or unreachable required action

Failure/stop record: `____________________________________________________________`

## G9 Security and minimization

- [ ] G9.1 non-owner Project/Module access denied
- [ ] G9.2 non-owner Job/Event polling denied
- [ ] G9.3 non-owner SSE denied
- [ ] G9.4 non-owner Idea/Concept/Selection/Planning/Marketing access denied
- [ ] G9.5 no raw provider body exposed
- [ ] G9.6 no prompt exposed
- [ ] G9.7 no key/token/password exposed
- [ ] G9.8 Job Events contain no complete raw user input/attachment
- [ ] G9.9 Job Events and reports contain no full legal text
- [ ] G9.10 browser received no stack trace/internal exception detail
- [ ] G9.11 fixture endpoint absent in production-like profile
- [ ] G9.12 shared evidence files were reviewed and redacted

Failure/stop record: `____________________________________________________________`

## Final decision

- [ ] Every mandatory item above is checked
- [ ] Every failure was resolved and the affected gate rerun
- [ ] Final branch/HEAD still match the candidate record
- [ ] Final duplicate and stale-RUNNING audits are clean
- [ ] Evidence contains no prohibited data

Decision: `PASS / NOT ACCEPTED`

Approver: `______________________________`  Date/time: `______________________________`

Notes: `__________________________________________________________________________`
