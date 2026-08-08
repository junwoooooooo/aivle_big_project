ALTER TABLE tech_ops_input_preparations
    ADD COLUMN proposal_generation_status VARCHAR(40) NOT NULL DEFAULT 'IDLE';
ALTER TABLE tech_ops_input_preparations ADD COLUMN active_proposal_task_run_id VARCHAR(64);
ALTER TABLE tech_ops_input_preparations ADD COLUMN safe_proposal_error VARCHAR(100);

ALTER TABLE tech_ops_input_preparations
    ADD CONSTRAINT fk_tech_ops_proposal_task
    FOREIGN KEY (active_proposal_task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION;

ALTER TABLE tech_ops_input_preparations
    ADD CONSTRAINT ck_tech_ops_proposal_generation_status CHECK (proposal_generation_status IN (
        'IDLE','QUEUED','RUNNING','SUCCEEDED','FAILED','STALE_ACTION_RESULT'
    ));
