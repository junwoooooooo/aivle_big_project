-- Finance keeps all three upstream authorities: TechOps, Market and Business Model.
ALTER TABLE financial_input_preparations
    ADD COLUMN source_market_research_version_id BIGINT,
    ADD COLUMN source_business_model_version_id BIGINT;

ALTER TABLE financial_input_preparations
    ADD CONSTRAINT fk_financial_preparation_market_version
        FOREIGN KEY (source_market_research_version_id) REFERENCES market_research_versions(id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_financial_preparation_bm_version
        FOREIGN KEY (source_business_model_version_id) REFERENCES market_research_versions(id) ON DELETE NO ACTION;

ALTER TABLE financial_input_preparations DROP CONSTRAINT uk_financial_preparation_source;
CREATE UNIQUE INDEX uk_financial_preparation_active_sources
    ON financial_input_preparations(project_id, source_tech_ops_snapshot_id,
        source_market_research_version_id, source_business_model_version_id)
    WHERE deleted_at IS NULL
      AND source_market_research_version_id IS NOT NULL
      AND source_business_model_version_id IS NOT NULL;

ALTER TABLE financial_input_snapshots
    ADD COLUMN source_market_research_version_id BIGINT,
    ADD COLUMN source_business_model_version_id BIGINT;

ALTER TABLE financial_input_snapshots
    ADD CONSTRAINT fk_financial_snapshot_market_version
        FOREIGN KEY (source_market_research_version_id) REFERENCES market_research_versions(id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_financial_snapshot_bm_version
        FOREIGN KEY (source_business_model_version_id) REFERENCES market_research_versions(id) ON DELETE NO ACTION;

ALTER TABLE financial_input_snapshots DROP CONSTRAINT uk_financial_snapshot_source;
CREATE UNIQUE INDEX uk_financial_snapshot_active_sources
    ON financial_input_snapshots(project_id, source_tech_ops_snapshot_id,
        source_market_research_version_id, source_business_model_version_id)
    WHERE deleted_at IS NULL
      AND source_market_research_version_id IS NOT NULL
      AND source_business_model_version_id IS NOT NULL;
