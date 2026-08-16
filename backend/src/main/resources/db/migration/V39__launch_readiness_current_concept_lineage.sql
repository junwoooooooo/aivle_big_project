-- V27: bind readiness inputs to the exact current concept. Legacy rows stay
-- nullable and are therefore projected as historical/stale rather than backfilled.
ALTER TABLE launch_readiness_input_snapshots
    ADD COLUMN source_market_seed_snapshot_id VARCHAR(64),
    ADD COLUMN source_selection_id BIGINT,
    ADD COLUMN source_selection_revision INTEGER,
    ADD COLUMN source_bm_plan_revision INTEGER,
    ADD COLUMN source_binding_hash VARCHAR(71),
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN stale BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stale_reason VARCHAR(40),
    ADD CONSTRAINT fk_launch_readiness_current_seed
        FOREIGN KEY (source_market_seed_snapshot_id) REFERENCES market_analysis_seed_snapshots(id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_launch_readiness_current_selection
        FOREIGN KEY (source_selection_id) REFERENCES concept_portfolio_selections(id) ON DELETE NO ACTION,
    ADD CONSTRAINT ck_launch_readiness_attempt CHECK (attempt BETWEEN 1 AND 3);

CREATE INDEX idx_launch_readiness_exact_source
    ON launch_readiness_input_snapshots(
        project_id, source_market_seed_snapshot_id, source_selection_id,
        source_selection_revision, source_bm_plan_revision
    )
    WHERE deleted_at IS NULL AND is_current = TRUE;

ALTER TABLE financial_input_preparations
    ADD COLUMN source_current_market_seed_snapshot_id VARCHAR(64),
    ADD COLUMN source_selection_id BIGINT,
    ADD COLUMN source_selection_revision INTEGER,
    ADD COLUMN source_bm_plan_revision INTEGER,
    ADD COLUMN current_concept_binding_hash VARCHAR(71),
    ADD CONSTRAINT fk_financial_preparation_current_seed
        FOREIGN KEY (source_current_market_seed_snapshot_id) REFERENCES market_analysis_seed_snapshots(id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_financial_preparation_current_selection
        FOREIGN KEY (source_selection_id) REFERENCES concept_portfolio_selections(id) ON DELETE NO ACTION;

ALTER TABLE financial_input_snapshots
    ADD COLUMN source_current_market_seed_snapshot_id VARCHAR(64),
    ADD COLUMN source_selection_id BIGINT,
    ADD COLUMN source_selection_revision INTEGER,
    ADD COLUMN source_bm_plan_revision INTEGER,
    ADD COLUMN current_concept_binding_hash VARCHAR(71),
    ADD CONSTRAINT fk_financial_snapshot_current_seed
        FOREIGN KEY (source_current_market_seed_snapshot_id) REFERENCES market_analysis_seed_snapshots(id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_financial_snapshot_current_selection
        FOREIGN KEY (source_selection_id) REFERENCES concept_portfolio_selections(id) ON DELETE NO ACTION;
