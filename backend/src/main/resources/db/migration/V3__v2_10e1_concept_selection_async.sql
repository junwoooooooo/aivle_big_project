ALTER TABLE concept_selections ADD COLUMN active_action_task_run_id VARCHAR(64);
ALTER TABLE concept_selections ADD COLUMN pending_action_type VARCHAR(40);
ALTER TABLE concept_selections ADD COLUMN pending_hypothesis_type VARCHAR(40);
ALTER TABLE concept_selections ADD COLUMN pending_decision_id VARCHAR(64);
ALTER TABLE concept_selections ADD COLUMN pending_proposal_version INTEGER;
ALTER TABLE concept_selections ADD COLUMN action_status VARCHAR(40) NOT NULL DEFAULT 'IDLE';
ALTER TABLE concept_selections ADD COLUMN safe_action_error VARCHAR(100);

ALTER TABLE concept_selections
    ADD CONSTRAINT fk_concept_selection_action_task
    FOREIGN KEY (active_action_task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION;

ALTER TABLE concept_selections
    ADD CONSTRAINT ck_concept_selection_action_status CHECK (action_status IN (
        'IDLE','QUEUED','RUNNING','SUCCEEDED','FAILED','LEGAL_INELIGIBLE','STALE_ACTION_RESULT'
    ));
