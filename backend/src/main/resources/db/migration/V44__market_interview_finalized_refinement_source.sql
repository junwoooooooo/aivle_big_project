ALTER TABLE market_interview_runs
    ADD COLUMN source_concept_refinement_final_id BIGINT;

ALTER TABLE market_interview_runs
    ADD CONSTRAINT fk_market_interview_refinement_final
    FOREIGN KEY (source_concept_refinement_final_id)
    REFERENCES concept_refinement_finals(id) ON DELETE NO ACTION;

-- Historical rows predate the finalized-refinement gate. They remain readable history only.
UPDATE market_interview_runs run
SET source_concept_refinement_final_id = final.id
FROM concept_refinement_finals final
WHERE run.source_concept_refinement_final_id IS NULL
  AND final.project_id = run.project_id
  AND final.final_market_seed_snapshot_id = run.source_market_seed_snapshot_id;

CREATE INDEX idx_market_interview_refinement_final
    ON market_interview_runs(source_concept_refinement_final_id);
