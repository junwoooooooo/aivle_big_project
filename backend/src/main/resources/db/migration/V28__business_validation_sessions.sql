CREATE TABLE business_validation_sessions (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    source_market_seed_snapshot_id VARCHAR(64) NOT NULL,
    source_portfolio_selection_id BIGINT NOT NULL,
    source_selection_revision INTEGER,
    market_task_run_id VARCHAR(64) NOT NULL REFERENCES task_runs(id),
    market_version_id BIGINT REFERENCES market_research_versions(id),
    bm_task_run_id VARCHAR(64) REFERENCES task_runs(id),
    bm_version_id BIGINT REFERENCES market_research_versions(id),
    state VARCHAR(24) NOT NULL,
    canonical_input_hash VARCHAR(71) NOT NULL,
    command_idempotency_key VARCHAR(128) NOT NULL,
    bm_command_idempotency_key VARCHAR(128),
    bm_attempt INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL
);

CREATE UNIQUE INDEX uq_business_validation_command
    ON business_validation_sessions(project_id, command_idempotency_key)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_business_validation_project_current
    ON business_validation_sessions(project_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_business_validation_active
    ON business_validation_sessions(state, updated_at)
    WHERE deleted_at IS NULL;
