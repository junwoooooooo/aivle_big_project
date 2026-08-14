CREATE TABLE bm_plan_preparations (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    plan_json TEXT NOT NULL,
    constraint_json TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    updated_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_bm_plan_preparation_project UNIQUE (project_id),
    CONSTRAINT fk_bm_plan_preparation_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT ck_bm_plan_preparation_revision CHECK (revision > 0)
);
CREATE INDEX idx_bm_plan_preparation_project ON bm_plan_preparations(project_id, updated_at DESC);
