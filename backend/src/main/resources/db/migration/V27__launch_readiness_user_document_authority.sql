CREATE TABLE launch_readiness_input_snapshots (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    module_type VARCHAR(24) NOT NULL,
    source_document_artifact_id VARCHAR(64) NOT NULL REFERENCES project_evidence_artifacts(id),
    source_document_hash VARCHAR(71) NOT NULL,
    source_document_name VARCHAR(255) NOT NULL,
    source_mode VARCHAR(40) NOT NULL,
    input_schema_version VARCHAR(20) NOT NULL,
    parsed_input_json TEXT NOT NULL,
    snapshot_hash VARCHAR(71) NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id BIGINT NOT NULL,
    finalized_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL
);

CREATE INDEX idx_launch_readiness_input_current
    ON launch_readiness_input_snapshots(project_id, module_type, finalized_at DESC)
    WHERE deleted_at IS NULL AND is_current = TRUE;

CREATE TABLE launch_readiness_reports (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    module_type VARCHAR(24) NOT NULL,
    input_snapshot_id VARCHAR(64) NOT NULL REFERENCES launch_readiness_input_snapshots(id),
    task_run_id VARCHAR(64) NOT NULL UNIQUE REFERENCES task_runs(id),
    result_schema_version VARCHAR(20) NOT NULL,
    analysis_json TEXT NOT NULL,
    quality_json TEXT NOT NULL,
    external_evidence_json TEXT NOT NULL,
    result_hash VARCHAR(71) NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL
);

CREATE INDEX idx_launch_readiness_report_current
    ON launch_readiness_reports(project_id, module_type, completed_at DESC)
    WHERE deleted_at IS NULL AND is_current = TRUE;

ALTER TABLE financial_input_preparations
    ADD COLUMN source_mode VARCHAR(40),
    ADD COLUMN source_document_artifact_id VARCHAR(64) REFERENCES project_evidence_artifacts(id),
    ADD COLUMN source_document_hash VARCHAR(71);

ALTER TABLE financial_input_snapshots
    ADD COLUMN source_mode VARCHAR(40),
    ADD COLUMN preparation_revision INTEGER,
    ADD COLUMN source_document_artifact_id VARCHAR(64) REFERENCES project_evidence_artifacts(id),
    ADD COLUMN source_document_hash VARCHAR(71);

CREATE INDEX idx_financial_user_document_preparation
    ON financial_input_preparations(project_id, created_at DESC)
    WHERE deleted_at IS NULL AND source_mode = 'USER_DOCUMENT_INPUT';

CREATE INDEX idx_financial_user_document_snapshot
    ON financial_input_snapshots(project_id, finalized_at DESC)
    WHERE deleted_at IS NULL AND source_mode = 'USER_DOCUMENT_INPUT';

CREATE TABLE launch_readiness_integrated_report_manifests (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    selected_modules_json TEXT NOT NULL,
    source_reports_json TEXT NOT NULL,
    generated_by_user_id BIGINT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL
);
