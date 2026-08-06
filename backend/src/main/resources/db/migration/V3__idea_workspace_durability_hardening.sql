-- G3-H: additive Message Envelope, provenance and durable worker hardening.

ALTER TABLE idea_messages ADD COLUMN schema_version VARCHAR(20);
ALTER TABLE idea_messages ADD COLUMN message_type VARCHAR(40);
ALTER TABLE idea_messages ADD COLUMN payload_json TEXT;
ALTER TABLE idea_messages ADD COLUMN task_run_id VARCHAR(64);

UPDATE idea_messages
SET message_type = 'TEXT'
WHERE message_type IS NULL;

UPDATE idea_messages
SET schema_version = '1.0',
    payload_json = content
WHERE role = 'ASSISTANT' AND schema_version IS NULL;

ALTER TABLE idea_messages ALTER COLUMN message_type SET NOT NULL;
ALTER TABLE idea_messages ADD CONSTRAINT fk_idea_message_task_run
    FOREIGN KEY (task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION;
ALTER TABLE idea_messages ADD CONSTRAINT uk_idea_message_task_run UNIQUE (task_run_id);
ALTER TABLE idea_messages ADD CONSTRAINT ck_idea_message_type
    CHECK (message_type IN ('TEXT', 'QUESTION_SET', 'BRIEF_REVIEW', 'ATTACHMENT_SUMMARY', 'JOB_STATUS', 'ERROR'));
ALTER TABLE idea_messages ADD CONSTRAINT ck_idea_message_envelope
    CHECK ((role = 'USER' AND schema_version IS NULL AND payload_json IS NULL AND message_type = 'TEXT')
        OR (role = 'ASSISTANT' AND schema_version IS NOT NULL AND payload_json IS NOT NULL));
CREATE INDEX idx_idea_message_type ON idea_messages(conversation_id, message_type, sequence_number);

ALTER TABLE opportunity_field_values ADD COLUMN source_message_id BIGINT;
ALTER TABLE opportunity_field_values ADD COLUMN source_attachment_id BIGINT;
ALTER TABLE opportunity_field_values ADD COLUMN confidence DECIMAL(5,4);
ALTER TABLE opportunity_field_values ADD COLUMN user_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE opportunity_field_values ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE opportunity_field_values ADD CONSTRAINT fk_opportunity_field_source_message
    FOREIGN KEY (source_message_id) REFERENCES idea_messages(id) ON DELETE NO ACTION;
ALTER TABLE opportunity_field_values ADD CONSTRAINT fk_opportunity_field_source_attachment
    FOREIGN KEY (source_attachment_id) REFERENCES idea_attachments(id) ON DELETE NO ACTION;
ALTER TABLE opportunity_field_values ADD CONSTRAINT ck_opportunity_field_confidence
    CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1));
ALTER TABLE opportunity_field_values ADD CONSTRAINT ck_opportunity_field_confirmation
    CHECK ((user_confirmed = TRUE AND confirmed_at IS NOT NULL)
        OR (user_confirmed = FALSE AND confirmed_at IS NULL));
CREATE INDEX idx_opportunity_field_source_message ON opportunity_field_values(source_message_id);
CREATE INDEX idx_opportunity_field_source_attachment ON opportunity_field_values(source_attachment_id);

ALTER TABLE idea_attachments ADD COLUMN task_run_id VARCHAR(64);
ALTER TABLE idea_attachments ADD CONSTRAINT fk_idea_attachment_task_run
    FOREIGN KEY (task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION;
ALTER TABLE idea_attachments ADD CONSTRAINT uk_idea_attachment_task_run UNIQUE (task_run_id);

ALTER TABLE opportunity_brief_versions ADD COLUMN task_run_id VARCHAR(64);
ALTER TABLE opportunity_brief_versions ADD CONSTRAINT fk_opportunity_brief_task_run
    FOREIGN KEY (task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION;
ALTER TABLE opportunity_brief_versions ADD CONSTRAINT uk_opportunity_brief_task_run UNIQUE (task_run_id);

ALTER TABLE task_runs ADD COLUMN next_attempt_at TIMESTAMP;
UPDATE task_runs SET next_attempt_at = created_at WHERE next_attempt_at IS NULL;
ALTER TABLE task_runs ALTER COLUMN next_attempt_at SET NOT NULL;
CREATE INDEX idx_task_runs_durable_claim ON task_runs(task_type, state, next_attempt_at, created_at, id);
