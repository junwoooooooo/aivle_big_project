-- Finance can start after the BM canvas is completed; TechOps remains an independent branch.
ALTER TABLE financial_input_preparations DROP CONSTRAINT fk_financial_preparation_tech_ops;
ALTER TABLE financial_input_preparations DROP CONSTRAINT fk_financial_preparation_market_seed;
ALTER TABLE financial_input_preparations ALTER COLUMN source_tech_ops_snapshot_id DROP NOT NULL;
ALTER TABLE financial_input_preparations ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL;
ALTER TABLE financial_input_preparations ADD COLUMN source_market_research_run_id BIGINT;
ALTER TABLE financial_input_preparations ADD CONSTRAINT fk_financial_preparation_bm
    FOREIGN KEY (source_market_research_run_id) REFERENCES market_research_runs(id) ON DELETE NO ACTION;
CREATE UNIQUE INDEX uk_financial_preparation_bm_source
    ON financial_input_preparations(project_id, source_market_research_run_id)
    WHERE source_market_research_run_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE financial_input_snapshots DROP CONSTRAINT fk_financial_snapshot_tech_ops;
ALTER TABLE financial_input_snapshots DROP CONSTRAINT fk_financial_snapshot_market_seed;
ALTER TABLE financial_input_snapshots ALTER COLUMN source_tech_ops_snapshot_id DROP NOT NULL;
ALTER TABLE financial_input_snapshots ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL;
ALTER TABLE financial_input_snapshots ADD COLUMN source_market_research_run_id BIGINT;
ALTER TABLE financial_input_snapshots ADD CONSTRAINT fk_financial_snapshot_bm
    FOREIGN KEY (source_market_research_run_id) REFERENCES market_research_runs(id) ON DELETE NO ACTION;
CREATE UNIQUE INDEX uk_financial_snapshot_bm_source
    ON financial_input_snapshots(project_id, source_market_research_run_id)
    WHERE source_market_research_run_id IS NOT NULL AND deleted_at IS NULL;
