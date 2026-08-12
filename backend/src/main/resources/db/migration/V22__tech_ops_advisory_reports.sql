CREATE TABLE tech_ops_advisory_reports (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    task_run_id VARCHAR(64) NOT NULL,
    tech_ops_input_snapshot_id VARCHAR(64) NOT NULL,
    source_market_seed_snapshot_id VARCHAR(64) NOT NULL,
    source_market_research_version_id BIGINT NOT NULL,
    source_business_model_version_id BIGINT NOT NULL,
    source_portfolio_selection_id BIGINT NOT NULL,
    selected_concept_id VARCHAR(64) NOT NULL,
    selected_concept_hash VARCHAR(71) NOT NULL,
    contract_version VARCHAR(20) NOT NULL,
    result_json TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_tech_ops_advisory_task UNIQUE (task_run_id),
    CONSTRAINT fk_tech_ops_advisory_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_task FOREIGN KEY (task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_snapshot FOREIGN KEY (tech_ops_input_snapshot_id) REFERENCES tech_ops_input_snapshots(id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_seed FOREIGN KEY (source_market_seed_snapshot_id) REFERENCES market_analysis_seed_snapshots(id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_market FOREIGN KEY (source_market_research_version_id) REFERENCES market_research_versions(id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_bm FOREIGN KEY (source_business_model_version_id) REFERENCES market_research_versions(id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_selection FOREIGN KEY (source_portfolio_selection_id, project_id) REFERENCES concept_portfolio_selections(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_concept FOREIGN KEY (selected_concept_id, project_id) REFERENCES concept_portfolio_concepts(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_tech_ops_advisory_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT ck_tech_ops_advisory_hash CHECK (selected_concept_hash LIKE 'sha256:%')
);

CREATE INDEX idx_tech_ops_advisory_project_created
    ON tech_ops_advisory_reports(project_id, created_at DESC)
    WHERE deleted_at IS NULL;
