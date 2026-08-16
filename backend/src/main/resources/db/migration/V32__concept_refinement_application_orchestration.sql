ALTER TABLE concept_refinement_rounds
    ADD COLUMN application_idempotency_key VARCHAR(128),
    ADD COLUMN application_hash VARCHAR(71),
    ADD COLUMN application_task_run_id VARCHAR(64) REFERENCES task_runs(id),
    ADD COLUMN delta_legal_task_run_id VARCHAR(64) REFERENCES task_runs(id),
    ADD COLUMN application_attempt INTEGER,
    ADD COLUMN applied_selection_revision INTEGER,
    ADD COLUMN applied_bm_plan_revision INTEGER,
    ADD COLUMN application_error_code VARCHAR(80),
    ADD COLUMN application_started_at TIMESTAMP,
    ADD COLUMN application_applied_at TIMESTAMP;

ALTER TABLE concept_refinement_rounds
    DROP CONSTRAINT ck_concept_refinement_state;

ALTER TABLE concept_refinement_rounds
    ALTER COLUMN state TYPE VARCHAR(40);

ALTER TABLE concept_refinement_rounds
    ADD CONSTRAINT ck_concept_refinement_state CHECK (
        state IN ('PROPOSING', 'AWAITING_DECISION', 'NO_CHANGES', 'FAILED', 'STALE',
                  'DECISION_RECORDED', 'KEEP_CURRENT', 'APPLYING_HYPOTHESES', 'APPLY_FAILED',
                  'LEGAL_REVIEW_PENDING', 'LEGAL_REVIEW_FAILED', 'LEGAL_BLOCKED',
                  'APPLIED_PENDING_FINALIZATION')),
    ADD CONSTRAINT ck_concept_refinement_application_hash CHECK (
        application_hash IS NULL OR application_hash LIKE 'sha256:%'),
    ADD CONSTRAINT ck_concept_refinement_application_attempt CHECK (
        application_attempt IS NULL OR application_attempt BETWEEN 1 AND 3);
