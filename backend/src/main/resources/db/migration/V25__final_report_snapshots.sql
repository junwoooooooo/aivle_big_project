CREATE TABLE final_report_snapshots (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    report_version INTEGER NOT NULL,
    source_manifest_json TEXT NOT NULL,
    source_manifest_hash VARCHAR(71) NOT NULL,
    report_json TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_final_report_project_version UNIQUE (project_id, report_version)
);

CREATE INDEX idx_final_report_project_current
    ON final_report_snapshots(project_id, report_version DESC)
    WHERE deleted_at IS NULL;
