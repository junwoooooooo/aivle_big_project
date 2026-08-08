CREATE TABLE project_evidence_artifacts (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    storage_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(71) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_project_evidence_artifact_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_project_evidence_artifact_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_project_evidence_artifact_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT ck_project_evidence_artifact_size CHECK (size_bytes > 0),
    CONSTRAINT ck_project_evidence_artifact_sha CHECK (sha256 LIKE 'sha256:%')
);

CREATE INDEX idx_project_evidence_artifact_project
    ON project_evidence_artifacts(project_id, created_at DESC);

ALTER TABLE tech_ops_evidence_references ADD COLUMN artifact_id VARCHAR(64);
ALTER TABLE tech_ops_evidence_references ALTER COLUMN artifact_ref DROP NOT NULL;
ALTER TABLE tech_ops_evidence_references ADD CONSTRAINT fk_tech_ops_evidence_artifact
    FOREIGN KEY (artifact_id, project_id) REFERENCES project_evidence_artifacts(id, project_id) ON DELETE NO ACTION;
CREATE INDEX idx_tech_ops_evidence_artifact ON tech_ops_evidence_references(artifact_id);
