ALTER TABLE module_runs DROP CONSTRAINT ck_module_run_status;
ALTER TABLE module_runs ADD CONSTRAINT ck_module_run_status
    CHECK (status IN ('NOT_CONNECTED','READY','QUEUED','RUNNING','NEEDS_INPUT','COMPLETED','FAILED','STALE'));

CREATE TABLE module_results (
    module_run_id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    input_snapshot_id VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    result_reference VARCHAR(1000) NOT NULL,
    summary_json TEXT NOT NULL,
    competitors_json TEXT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_hash VARCHAR(71) NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_module_result_run FOREIGN KEY (module_run_id, project_id) REFERENCES module_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_module_result_snapshot FOREIGN KEY (input_snapshot_id, project_id) REFERENCES selected_concept_snapshots(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_module_result_status CHECK (status IN ('COMPLETED','FAILED','NEEDS_INPUT')),
    CONSTRAINT ck_module_result_hash CHECK (result_hash LIKE 'sha256:%')
);

CREATE INDEX idx_module_result_project ON module_results(project_id, completed_at DESC);

CREATE TABLE planning_change_proposals (
    id VARCHAR(100) PRIMARY KEY,
    module_run_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    meaningful_title VARCHAR(300) NOT NULL,
    affected_fields_json TEXT NOT NULL,
    before_json TEXT NOT NULL,
    after_json TEXT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    evidence_references_json TEXT NOT NULL,
    impact_areas_json TEXT NOT NULL,
    decision_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    modified_after_json TEXT,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_planning_proposal_result FOREIGN KEY (module_run_id) REFERENCES module_results(module_run_id) ON DELETE NO ACTION,
    CONSTRAINT fk_planning_proposal_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT ck_planning_proposal_decision CHECK (decision_status IN ('PENDING','ADOPT','PARTIALLY_ADOPT','REJECT')),
    CONSTRAINT ck_planning_proposal_partial CHECK (
        (decision_status = 'PARTIALLY_ADOPT' AND modified_after_json IS NOT NULL)
        OR (decision_status <> 'PARTIALLY_ADOPT' AND modified_after_json IS NULL)
    )
);

CREATE INDEX idx_planning_proposal_run ON planning_change_proposals(module_run_id, created_at);
CREATE INDEX idx_planning_proposal_project ON planning_change_proposals(project_id, created_at);
