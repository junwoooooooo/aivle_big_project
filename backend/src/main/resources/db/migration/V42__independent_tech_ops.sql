-- TechOps의 고유 입력은 프로젝트에서 직접 작성할 수 있다. Concept/Market/BM은 선택적 문맥이다.
ALTER TABLE tech_ops_input_preparations
    ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL,
    ALTER COLUMN source_snapshot_hash DROP NOT NULL;

ALTER TABLE tech_ops_input_snapshots
    ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL;

ALTER TABLE tech_ops_advisory_reports
    ALTER COLUMN source_market_seed_snapshot_id DROP NOT NULL,
    ALTER COLUMN source_market_research_version_id DROP NOT NULL,
    ALTER COLUMN source_business_model_version_id DROP NOT NULL,
    ALTER COLUMN source_portfolio_selection_id DROP NOT NULL,
    ALTER COLUMN selected_concept_id DROP NOT NULL,
    ALTER COLUMN selected_concept_hash DROP NOT NULL;
