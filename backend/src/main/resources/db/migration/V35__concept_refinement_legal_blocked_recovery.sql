ALTER TABLE concept_refinement_rounds
    ADD COLUMN application_before_json TEXT,
    ADD COLUMN application_before_hash VARCHAR(71),
    ADD COLUMN recovery_idempotency_key VARCHAR(128),
    ADD COLUMN recovery_hash VARCHAR(71),
    ADD COLUMN recovered_at TIMESTAMP;

ALTER TABLE concept_refinement_rounds
    ADD CONSTRAINT ck_concept_refinement_application_before_hash
        CHECK (application_before_hash IS NULL OR application_before_hash LIKE 'sha256:%'),
    ADD CONSTRAINT ck_concept_refinement_recovery_hash
        CHECK (recovery_hash IS NULL OR recovery_hash LIKE 'sha256:%');

ALTER TABLE concept_refinement_rounds DROP CONSTRAINT ck_concept_refinement_state;
ALTER TABLE concept_refinement_rounds
    ADD CONSTRAINT ck_concept_refinement_state CHECK(state IN(
        'PROPOSING','AWAITING_DECISION','NO_CHANGES','FAILED','STALE',
        'DECISION_RECORDED','KEEP_CURRENT','APPLYING_HYPOTHESES','APPLY_FAILED',
        'LEGAL_REVIEW_PENDING','LEGAL_REVIEW_FAILED','LEGAL_BLOCKED',
        'APPLIED_PENDING_FINALIZATION','FINALIZING','FINALIZATION_FAILED','FINALIZED',
        'DECLINED','CONTINUED','RECOVERED'));
