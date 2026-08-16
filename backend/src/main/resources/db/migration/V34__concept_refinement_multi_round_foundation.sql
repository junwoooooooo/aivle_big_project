ALTER TABLE concept_refinement_rounds
    ADD COLUMN parent_round_id BIGINT REFERENCES concept_refinement_rounds(id),
    ADD COLUMN baseline_selection_revision INTEGER,
    ADD COLUMN baseline_bm_plan_revision INTEGER,
    ADD COLUMN baseline_overlay_json TEXT,
    ADD COLUMN seed_rebuild_required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE concept_refinement_rounds
SET baseline_selection_revision = source_selection_revision,
    baseline_bm_plan_revision = source_bm_plan_revision,
    baseline_overlay_json = '{}'
WHERE round_number = 1;

ALTER TABLE concept_refinement_rounds
    ALTER COLUMN baseline_selection_revision SET NOT NULL,
    ALTER COLUMN baseline_bm_plan_revision SET NOT NULL,
    ALTER COLUMN baseline_overlay_json SET NOT NULL;

ALTER TABLE concept_refinement_rounds DROP CONSTRAINT ck_concept_refinement_state;
ALTER TABLE concept_refinement_rounds
    ADD CONSTRAINT ck_concept_refinement_state CHECK(state IN(
        'PROPOSING','AWAITING_DECISION','NO_CHANGES','FAILED','STALE',
        'DECISION_RECORDED','KEEP_CURRENT','APPLYING_HYPOTHESES','APPLY_FAILED',
        'LEGAL_REVIEW_PENDING','LEGAL_REVIEW_FAILED','LEGAL_BLOCKED',
        'APPLIED_PENDING_FINALIZATION','FINALIZING','FINALIZATION_FAILED','FINALIZED',
        'DECLINED','CONTINUED'));

CREATE UNIQUE INDEX uq_concept_refinement_parent_round
    ON concept_refinement_rounds(parent_round_id)
    WHERE parent_round_id IS NOT NULL AND deleted_at IS NULL;
