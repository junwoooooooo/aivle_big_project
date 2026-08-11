CREATE TABLE financial_analysis_reports (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    input_snapshot_id VARCHAR(64) NOT NULL REFERENCES financial_input_snapshots(id),
    input_snapshot_hash VARCHAR(71) NOT NULL,
    source_market_research_run_id BIGINT REFERENCES market_research_runs(id),
    report_json TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL
);
CREATE INDEX idx_financial_analysis_report_current ON financial_analysis_reports(project_id, input_snapshot_id, completed_at DESC) WHERE deleted_at IS NULL;
