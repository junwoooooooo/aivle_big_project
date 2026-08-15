CREATE TABLE concept_portfolio_runs (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_idea_brief_snapshot_id VARCHAR(64) NOT NULL,
    source_snapshot_hash VARCHAR(71) NOT NULL,
    requested_max_concepts INTEGER NOT NULL,
    produced_concept_count INTEGER NOT NULL DEFAULT 0,
    product_status VARCHAR(40) NOT NULL,
    engine_run_id VARCHAR(64),
    engine_status VARCHAR(40),
    runtime_stage VARCHAR(40),
    downstream_readiness VARCHAR(80),
    initial_task_run_id VARCHAR(64),
    active_task_run_id VARCHAR(64),
    engine_default_candidate_id VARCHAR(200),
    result_contract VARCHAR(100),
    result_schema_version VARCHAR(20),
    result_snapshot_json TEXT,
    failure_code VARCHAR(80),
    open_input_count INTEGER NOT NULL DEFAULT 0,
    request_hash VARCHAR(71) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cp_run_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_run_idea_brief FOREIGN KEY (source_idea_brief_snapshot_id) REFERENCES idea_briefs(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_run_initial_task FOREIGN KEY (initial_task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_run_active_task FOREIGN KEY (active_task_run_id) REFERENCES task_runs(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_run_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT uk_cp_run_id_project UNIQUE (id, project_id),
    CONSTRAINT uk_cp_run_idempotency UNIQUE (project_id, idempotency_key),
    CONSTRAINT ck_cp_run_requested_count CHECK (requested_max_concepts BETWEEN 1 AND 5),
    CONSTRAINT ck_cp_run_produced_count CHECK (produced_concept_count BETWEEN 0 AND 5),
    CONSTRAINT ck_cp_run_open_input_count CHECK (open_input_count >= 0),
    CONSTRAINT ck_cp_run_source_hash CHECK (source_snapshot_hash LIKE 'sha256:%'),
    CONSTRAINT ck_cp_run_request_hash CHECK (request_hash LIKE 'sha256:%'),
    CONSTRAINT ck_cp_run_status CHECK (product_status IN (
        'QUEUED', 'RUNNING', 'RESULTS_AVAILABLE', 'RESULTS_WITH_OPEN_INPUT',
        'NEEDS_INPUT', 'FAILED', 'STALE'))
);
CREATE UNIQUE INDEX uk_cp_run_current ON concept_portfolio_runs(project_id)
    WHERE is_current = TRUE AND deleted_at IS NULL;
CREATE INDEX idx_cp_run_project_updated ON concept_portfolio_runs(project_id, updated_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_cp_run_active_task ON concept_portfolio_runs(active_task_run_id)
    WHERE active_task_run_id IS NOT NULL;

CREATE TABLE concept_portfolio_concepts (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    candidate_id VARCHAR(200) NOT NULL,
    lineage_id VARCHAR(200) NOT NULL,
    plan_id VARCHAR(200) NOT NULL,
    parent_candidate_id VARCHAR(200),
    display_order INTEGER NOT NULL,
    concept_name VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    legal_status VARCHAR(40) NOT NULL,
    candidate_snapshot_json TEXT NOT NULL,
    legal_review_json TEXT NOT NULL,
    canonical_hash VARCHAR(71) NOT NULL,
    selectable BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cp_concept_run FOREIGN KEY (run_id, project_id)
        REFERENCES concept_portfolio_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_concept_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT uk_cp_concept_candidate UNIQUE (run_id, candidate_id),
    CONSTRAINT uk_cp_concept_order UNIQUE (run_id, display_order),
    CONSTRAINT ck_cp_concept_order CHECK (display_order BETWEEN 1 AND 5),
    CONSTRAINT ck_cp_concept_hash CHECK (canonical_hash LIKE 'sha256:%')
);
CREATE INDEX idx_cp_concept_run_order ON concept_portfolio_concepts(run_id, display_order)
    WHERE deleted_at IS NULL;

CREATE TABLE concept_portfolio_continuations (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    context_version VARCHAR(20) NOT NULL,
    context_hash VARCHAR(71) NOT NULL,
    context_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cp_continuation_run FOREIGN KEY (run_id, project_id)
        REFERENCES concept_portfolio_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_continuation_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT uk_cp_continuation_run UNIQUE (run_id),
    CONSTRAINT ck_cp_continuation_hash CHECK (context_hash LIKE 'sha256:%')
);

CREATE TABLE concept_input_requests (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    continuation_id VARCHAR(64),
    candidate_id VARCHAR(200),
    lineage_id VARCHAR(200),
    scope VARCHAR(40),
    status VARCHAR(20) NOT NULL,
    source_question TEXT,
    presentation_question_ko TEXT,
    reason TEXT,
    possible_user_action TEXT,
    safe_summary TEXT,
    unknown_facts_json TEXT NOT NULL,
    affected_fields_json TEXT NOT NULL,
    artifact_json TEXT,
    request_hash VARCHAR(71) NOT NULL,
    answered_at TIMESTAMP,
    resolved_at TIMESTAMP,
    continuation_task_run_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cp_input_run FOREIGN KEY (run_id, project_id)
        REFERENCES concept_portfolio_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_input_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_input_continuation FOREIGN KEY (continuation_id)
        REFERENCES concept_portfolio_continuations(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_input_task FOREIGN KEY (continuation_task_run_id)
        REFERENCES task_runs(id) ON DELETE NO ACTION,
    CONSTRAINT uk_cp_input_hash UNIQUE (run_id, request_hash),
    CONSTRAINT ck_cp_input_status CHECK (status IN ('OPEN', 'ANSWERED', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_cp_input_hash CHECK (request_hash LIKE 'sha256:%')
);
CREATE INDEX idx_cp_input_run_status ON concept_input_requests(run_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE concept_input_responses (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    input_request_id VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    responded_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cp_response_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_response_request FOREIGN KEY (input_request_id)
        REFERENCES concept_input_requests(id) ON DELETE NO ACTION,
    CONSTRAINT fk_cp_response_user FOREIGN KEY (responded_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT uk_cp_response_idempotency UNIQUE (input_request_id, idempotency_key)
);
