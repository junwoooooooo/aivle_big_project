-- V28: immutable Final Report lineage and command identity. Legacy rows remain
-- nullable and are conservatively projected as historical/stale.
ALTER TABLE final_report_snapshots
    ADD COLUMN source_market_seed_snapshot_id VARCHAR(64),
    ADD COLUMN source_selection_id BIGINT,
    ADD COLUMN source_selection_revision INTEGER,
    ADD COLUMN source_bm_plan_revision INTEGER,
    ADD COLUMN source_binding_hash VARCHAR(71),
    ADD COLUMN command_idempotency_key VARCHAR(128),
    ADD COLUMN command_identity_hash VARCHAR(71),
    ADD COLUMN manifest_schema_version INTEGER,
    ADD CONSTRAINT fk_final_report_current_seed
        FOREIGN KEY (source_market_seed_snapshot_id) REFERENCES market_analysis_seed_snapshots(id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_final_report_current_selection
        FOREIGN KEY (source_selection_id) REFERENCES concept_portfolio_selections(id) ON DELETE NO ACTION;

CREATE UNIQUE INDEX uk_final_report_project_command_key
    ON final_report_snapshots(project_id, command_idempotency_key)
    WHERE deleted_at IS NULL AND command_idempotency_key IS NOT NULL;

CREATE INDEX idx_final_report_exact_source
    ON final_report_snapshots(
        project_id, source_market_seed_snapshot_id, source_selection_id,
        source_selection_revision, source_bm_plan_revision
    )
    WHERE deleted_at IS NULL;
