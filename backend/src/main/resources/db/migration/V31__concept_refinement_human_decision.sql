ALTER TABLE concept_refinement_rounds
    ADD COLUMN decision_json TEXT,
    ADD COLUMN decision_hash VARCHAR(71),
    ADD COLUMN decision_idempotency_key VARCHAR(128),
    ADD COLUMN decided_by_user_id BIGINT,
    ADD COLUMN decided_at TIMESTAMP;

ALTER TABLE concept_refinement_rounds
    DROP CONSTRAINT ck_concept_refinement_state;

ALTER TABLE concept_refinement_rounds
    ADD CONSTRAINT ck_concept_refinement_state CHECK (
        state IN ('PROPOSING', 'AWAITING_DECISION', 'NO_CHANGES', 'FAILED', 'STALE',
                  'DECISION_RECORDED', 'KEEP_CURRENT')),
    ADD CONSTRAINT ck_concept_refinement_decision_hash CHECK (
        decision_hash IS NULL OR decision_hash LIKE 'sha256:%');
