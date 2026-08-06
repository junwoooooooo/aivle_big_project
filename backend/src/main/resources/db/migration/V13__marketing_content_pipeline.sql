CREATE TABLE pipeline_marketing_contents (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    planning_snapshot_id VARCHAR(64) NOT NULL,
    source_snapshot_hash VARCHAR(71) NOT NULL,
    source_snapshot_json TEXT NOT NULL,
    request_json TEXT NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    channel VARCHAR(120) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    task_run_id VARCHAR(64),
    current_revision_number INTEGER NOT NULL DEFAULT 0,
    finalized_revision_number INTEGER,
    created_by_user_id BIGINT NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pipeline_marketing_content_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_pipeline_marketing_content_planning FOREIGN KEY (planning_snapshot_id) REFERENCES finalized_planning_snapshots(id) ON DELETE NO ACTION,
    CONSTRAINT fk_pipeline_marketing_content_task FOREIGN KEY (task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION,
    CONSTRAINT fk_pipeline_marketing_content_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT ck_pipeline_marketing_content_type CHECK (content_type IN ('SOCIAL_POST','AD_COPY','LANDING_PAGE','BLOG_INTRO','EMAIL','BANNER','POSTER','IMAGE_BRIEF')),
    CONSTRAINT ck_pipeline_marketing_content_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','FINALIZED'))
);

CREATE INDEX idx_pipeline_marketing_contents_project ON pipeline_marketing_contents(project_id, created_at DESC);

CREATE TABLE pipeline_marketing_content_revisions (
    id VARCHAR(64) PRIMARY KEY, content_id VARCHAR(64) NOT NULL, revision_number INTEGER NOT NULL,
    revision_type VARCHAR(30) NOT NULL, origin VARCHAR(10) NOT NULL, result_json TEXT NOT NULL,
    created_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pipeline_marketing_revision_content FOREIGN KEY (content_id) REFERENCES pipeline_marketing_contents(id) ON DELETE NO ACTION,
    CONSTRAINT fk_pipeline_marketing_revision_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT uk_pipeline_marketing_revision_number UNIQUE (content_id, revision_number),
    CONSTRAINT ck_pipeline_marketing_revision_type CHECK (revision_type IN ('GENERATED','TONE_EDITED','SHORTENED','LEGAL_NOTICE_APPLIED','USER_EDITED','FINALIZED')),
    CONSTRAINT ck_pipeline_marketing_revision_origin CHECK (origin IN ('AI','USER','SYSTEM'))
);

CREATE TABLE pipeline_marketing_assets (
    id VARCHAR(64) PRIMARY KEY, content_id VARCHAR(64) NOT NULL, revision_id VARCHAR(64) NOT NULL,
    artifact_ref VARCHAR(300) NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pipeline_marketing_asset_content FOREIGN KEY (content_id) REFERENCES pipeline_marketing_contents(id) ON DELETE NO ACTION,
    CONSTRAINT fk_pipeline_marketing_asset_revision FOREIGN KEY (revision_id) REFERENCES pipeline_marketing_content_revisions(id) ON DELETE NO ACTION
);
