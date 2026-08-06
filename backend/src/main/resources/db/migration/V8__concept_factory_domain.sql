-- R3A five-slot Concept Factory and legal evidence persistence.
-- Composite foreign keys bind every child row to the same project as its immutable source snapshot.

ALTER TABLE idea_briefs ADD CONSTRAINT uk_idea_briefs_id_project UNIQUE (id, project_id);

CREATE TABLE legal_context_packs (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_snapshot_id VARCHAR(64) NOT NULL,
    source_snapshot_hash VARCHAR(71) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_context_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_legal_context_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_legal_context_snapshot FOREIGN KEY (source_snapshot_id, project_id) REFERENCES idea_briefs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_legal_context_hash CHECK (source_snapshot_hash LIKE 'sha256:%'),
    CONSTRAINT ck_legal_context_status CHECK (status IN ('PENDING', 'READY', 'FAILED', 'STALE'))
);

CREATE TABLE legal_evidence (
    id VARCHAR(64) PRIMARY KEY,
    context_pack_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_uri VARCHAR(1000) NOT NULL,
    content_hash VARCHAR(71) NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_evidence_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_legal_evidence_context FOREIGN KEY (context_pack_id, project_id) REFERENCES legal_context_packs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_legal_evidence_hash CHECK (content_hash LIKE 'sha256:%')
);

CREATE TABLE concept_factory_runs (
    id VARCHAR(64) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_idea_brief_snapshot_id VARCHAR(64) NOT NULL,
    source_snapshot_hash VARCHAR(71) NOT NULL,
    status VARCHAR(24) NOT NULL,
    replacement_rounds INTEGER NOT NULL DEFAULT 0,
    inspected_candidate_count INTEGER NOT NULL DEFAULT 0,
    provider_transient_retry_count INTEGER NOT NULL DEFAULT 0,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_concept_run_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_concept_run_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_run_snapshot FOREIGN KEY (source_idea_brief_snapshot_id, project_id) REFERENCES idea_briefs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_run_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE NO ACTION,
    CONSTRAINT ck_concept_run_hash CHECK (source_snapshot_hash LIKE 'sha256:%'),
    CONSTRAINT ck_concept_run_status CHECK (status IN ('QUEUED','GENERATING','VALIDATING','REPLACING','NEEDS_INPUT','COMPLETED','FAILED','STALE')),
    CONSTRAINT ck_concept_run_replacements CHECK (replacement_rounds BETWEEN 0 AND 2),
    CONSTRAINT ck_concept_run_inspected CHECK (inspected_candidate_count BETWEEN 0 AND 15),
    CONSTRAINT ck_concept_run_provider_retry CHECK (provider_transient_retry_count BETWEEN 0 AND 1)
);

CREATE INDEX idx_concept_run_project_current ON concept_factory_runs(project_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE concept_slots (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    slot_number INTEGER NOT NULL,
    variation_focus VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    legal_redesign_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_concept_slot_id_project UNIQUE (id, project_id),
    CONSTRAINT uk_concept_slot_number UNIQUE (run_id, slot_number),
    CONSTRAINT uk_concept_slot_focus UNIQUE (run_id, variation_focus),
    CONSTRAINT fk_concept_slot_run FOREIGN KEY (run_id, project_id) REFERENCES concept_factory_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_concept_slot_number CHECK (slot_number BETWEEN 1 AND 5),
    CONSTRAINT ck_concept_slot_focus CHECK (variation_focus IN ('CUSTOMER_EXPERIENCE','OPERATING_MODEL_AND_PARTNERS','REVENUE_AND_PRICING','CHANNEL_AND_SCALE','LOW_RISK_FAST_EXECUTION')),
    CONSTRAINT ck_concept_slot_status CHECK (status IN ('QUEUED','GENERATING','GENERATED','SCHEMA_INVALID','VALIDATING_ORIGIN','VALIDATING_LEGAL','REDESIGNING','REPLACING','ELIGIBLE','REJECTED','NEEDS_INPUT','FAILED','STALE')),
    CONSTRAINT ck_concept_slot_redesign CHECK (legal_redesign_count BETWEEN 0 AND 1)
);

CREATE TABLE concept_attempts (
    id VARCHAR(64) PRIMARY KEY,
    slot_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    phase VARCHAR(20) NOT NULL,
    task_run_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_concept_attempt_number UNIQUE (slot_id, attempt_number),
    CONSTRAINT fk_concept_attempt_slot FOREIGN KEY (slot_id, project_id) REFERENCES concept_slots(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_attempt_task FOREIGN KEY (task_run_id, project_id) REFERENCES task_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_concept_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_concept_attempt_phase CHECK (phase IN ('INITIAL','REPAIR','REDESIGN','REPLACEMENT'))
);

CREATE TABLE concepts (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    slot_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    source_idea_brief_snapshot_id VARCHAR(64) NOT NULL,
    source_snapshot_hash VARCHAR(71) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    canonical_hash VARCHAR(71) NOT NULL,
    major_field_hash VARCHAR(71) NOT NULL,
    legal_status VARCHAR(40) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_concept_id_project UNIQUE (id, project_id),
    CONSTRAINT uk_concept_run_slot UNIQUE (run_id, slot_id),
    CONSTRAINT uk_concept_run_canonical UNIQUE (run_id, canonical_hash),
    CONSTRAINT uk_concept_run_major_fields UNIQUE (run_id, major_field_hash),
    CONSTRAINT fk_concept_run FOREIGN KEY (run_id, project_id) REFERENCES concept_factory_runs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_slot FOREIGN KEY (slot_id, project_id) REFERENCES concept_slots(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_snapshot FOREIGN KEY (source_idea_brief_snapshot_id, project_id) REFERENCES idea_briefs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_concept_hashes CHECK (source_snapshot_hash LIKE 'sha256:%' AND canonical_hash LIKE 'sha256:%' AND major_field_hash LIKE 'sha256:%'),
    CONSTRAINT ck_concept_legal_status CHECK (legal_status IN ('IMPLEMENTABLE','IMPLEMENTABLE_WITH_CONTROLS','NEEDS_FACTS','REDESIGNABLE','REJECTED')),
    CONSTRAINT ck_concept_public_legal CHECK (published = FALSE OR legal_status IN ('IMPLEMENTABLE','IMPLEMENTABLE_WITH_CONTROLS'))
);

CREATE TABLE concept_legal_assessments (
    id VARCHAR(64) PRIMARY KEY,
    concept_id VARCHAR(64) NOT NULL,
    context_pack_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    safe_summary VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_concept_assessment_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_concept_assessment_concept FOREIGN KEY (concept_id, project_id) REFERENCES concepts(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_assessment_context FOREIGN KEY (context_pack_id, project_id) REFERENCES legal_context_packs(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT ck_concept_assessment_status CHECK (status IN ('IMPLEMENTABLE','IMPLEMENTABLE_WITH_CONTROLS','NEEDS_FACTS','REDESIGNABLE','REJECTED'))
);

CREATE TABLE concept_legal_evidence_links (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    assessment_id VARCHAR(64) NOT NULL,
    evidence_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_concept_legal_evidence UNIQUE (assessment_id, evidence_id),
    CONSTRAINT fk_concept_legal_link_assessment FOREIGN KEY (assessment_id, project_id) REFERENCES concept_legal_assessments(id, project_id) ON DELETE NO ACTION,
    CONSTRAINT fk_concept_legal_link_evidence FOREIGN KEY (evidence_id, project_id) REFERENCES legal_evidence(id, project_id) ON DELETE NO ACTION
);

CREATE TABLE concept_rejection_summaries (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    slot_id VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    safe_summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_concept_rejection_slot FOREIGN KEY (slot_id, project_id) REFERENCES concept_slots(id, project_id) ON DELETE NO ACTION
);
