CREATE TABLE research_competitor_seeds (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_name VARCHAR(200),
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_research_competitor_seed_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_research_competitor_seed_user FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_research_competitor_seed_order CHECK (display_order > 0)
);
CREATE UNIQUE INDEX uk_research_competitor_seed_name ON research_competitor_seeds(project_id, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_research_competitor_seed_project ON research_competitor_seeds(project_id, display_order) WHERE deleted_at IS NULL;
