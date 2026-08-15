CREATE TABLE professional_launch_readiness_reports (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    module_type VARCHAR(24) NOT NULL,
    input_json TEXT NOT NULL,
    analysis_json TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX idx_professional_launch_readiness_project_module
    ON professional_launch_readiness_reports (project_id, module_type, completed_at DESC)
    WHERE deleted_at IS NULL;
