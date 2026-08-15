-- Finance는 current Market/BM lineage를 authority로 사용하고 TechOps는 독립 분기로 유지한다.
ALTER TABLE financial_input_preparations
    ALTER COLUMN source_tech_ops_snapshot_id DROP NOT NULL,
    ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL;

DROP INDEX uk_financial_preparation_active_sources;
CREATE UNIQUE INDEX uk_financial_preparation_active_sources
    ON financial_input_preparations(project_id, source_market_research_version_id,
        source_business_model_version_id)
    WHERE deleted_at IS NULL
      AND source_market_research_version_id IS NOT NULL
      AND source_business_model_version_id IS NOT NULL;

ALTER TABLE financial_input_snapshots
    ALTER COLUMN source_tech_ops_snapshot_id DROP NOT NULL,
    ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL;

DROP INDEX uk_financial_snapshot_active_sources;
CREATE UNIQUE INDEX uk_financial_snapshot_active_sources
    ON financial_input_snapshots(project_id, source_market_research_version_id,
        source_business_model_version_id)
    WHERE deleted_at IS NULL
      AND source_market_research_version_id IS NOT NULL
      AND source_business_model_version_id IS NOT NULL;
