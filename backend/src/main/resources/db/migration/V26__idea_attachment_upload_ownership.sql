CREATE TABLE idea_attachment_uploads (
    stored_file_id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_idea_upload_stored_file FOREIGN KEY (stored_file_id) REFERENCES stored_files(id) ON DELETE NO ACTION,
    CONSTRAINT fk_idea_upload_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_idea_upload_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE NO ACTION
);

CREATE INDEX idx_idea_upload_owner_project
    ON idea_attachment_uploads(uploaded_by_user_id, project_id, created_at DESC)
    WHERE deleted_at IS NULL;
