CREATE TABLE marketing_strategy_reports (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    task_run_id VARCHAR(64) NOT NULL REFERENCES task_runs(id),
    source_manifest_json TEXT NOT NULL,
    source_manifest_hash VARCHAR(71) NOT NULL,
    source_json TEXT NOT NULL,
    contract_version VARCHAR(40) NOT NULL,
    result_json TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_marketing_strategy_task_run UNIQUE (task_run_id)
);

CREATE INDEX idx_marketing_strategy_project_created
    ON marketing_strategy_reports(project_id, created_at DESC)
    WHERE deleted_at IS NULL;
