-- G4 Regulatory Boundary contract hardening. Additive only.

ALTER TABLE regulatory_boundary_runs DROP CONSTRAINT ck_regulatory_boundary_run_state;
ALTER TABLE regulatory_boundary_runs ADD CONSTRAINT ck_regulatory_boundary_run_state CHECK (state IN (
    'QUEUED', 'CLASSIFYING', 'ROUTING', 'FETCHING_EVIDENCE', 'SCREENING',
    'NORMALIZING_RULES', 'CHECKING_CONFLICTS', 'READY', 'NEEDS_INPUT',
    'BLOCKED', 'FAILED', 'STALE'
));
ALTER TABLE regulatory_boundary_runs
    ADD CONSTRAINT uk_regulatory_boundary_run_input UNIQUE (project_id, brief_version_id, input_snapshot_hash);
ALTER TABLE regulatory_boundary_runs
    ADD CONSTRAINT uk_regulatory_boundary_run_task UNIQUE (task_run_id);

ALTER TABLE regulatory_boundary_versions DROP CONSTRAINT ck_regulatory_boundary_status;
ALTER TABLE regulatory_boundary_versions ADD CONSTRAINT ck_regulatory_boundary_status
    CHECK (status IN ('READY', 'NEEDS_INPUT', 'BLOCKED', 'FAILED', 'STALE'));
ALTER TABLE regulatory_boundary_versions ADD COLUMN brief_snapshot_hash VARCHAR(71);
ALTER TABLE regulatory_boundary_versions ADD COLUMN stale_at TIMESTAMP;
UPDATE regulatory_boundary_versions v
SET brief_snapshot_hash = r.input_snapshot_hash
FROM regulatory_boundary_runs r
WHERE r.id = v.run_id AND v.brief_snapshot_hash IS NULL;
ALTER TABLE regulatory_boundary_versions ALTER COLUMN brief_snapshot_hash SET NOT NULL;
ALTER TABLE regulatory_boundary_versions ADD CONSTRAINT ck_regulatory_boundary_brief_hash
    CHECK (brief_snapshot_hash LIKE 'sha256:%');

ALTER TABLE boundary_evidence ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'OFFICIAL_LAW';
ALTER TABLE boundary_evidence ADD COLUMN plain_summary TEXT;
ALTER TABLE boundary_evidence ADD COLUMN why_relevant TEXT;
ALTER TABLE boundary_evidence ADD COLUMN retrieved_at TIMESTAMP;
ALTER TABLE boundary_evidence ADD COLUMN content_hash VARCHAR(71);
UPDATE boundary_evidence
SET plain_summary = excerpt,
    why_relevant = 'Legacy G1 boundary evidence',
    retrieved_at = created_at,
    content_hash = 'sha256:' || repeat('0', 64)
WHERE content_hash IS NULL;
UPDATE boundary_evidence SET source_status = CASE source_status
    WHEN 'SOURCE_COMPLETE' THEN 'COMPLETE'
    WHEN 'SOURCE_PARTIAL' THEN 'PARTIAL'
    WHEN 'REGISTRY_GAP' THEN 'WARNING'
    ELSE 'WARNING'
END;
ALTER TABLE boundary_evidence ALTER COLUMN plain_summary SET NOT NULL;
ALTER TABLE boundary_evidence ALTER COLUMN why_relevant SET NOT NULL;
ALTER TABLE boundary_evidence ALTER COLUMN retrieved_at SET NOT NULL;
ALTER TABLE boundary_evidence ALTER COLUMN content_hash SET NOT NULL;
ALTER TABLE boundary_evidence ADD CONSTRAINT ck_boundary_evidence_source_status
    CHECK (source_status IN ('COMPLETE', 'PARTIAL', 'WARNING', 'UNAVAILABLE'));
ALTER TABLE boundary_evidence ADD CONSTRAINT ck_boundary_evidence_content_hash
    CHECK (content_hash LIKE 'sha256:%');
CREATE UNIQUE INDEX uk_boundary_evidence_content
    ON boundary_evidence(boundary_version_id, law_name, article, effective_date, content_hash);

ALTER TABLE boundary_rules ADD COLUMN structure_key VARCHAR(100);
ALTER TABLE boundary_rules ADD COLUMN title VARCHAR(300);
ALTER TABLE boundary_rules ADD COLUMN description TEXT;
ALTER TABLE boundary_rules ADD COLUMN normalized_requirement TEXT;
ALTER TABLE boundary_rules ADD COLUMN source_status VARCHAR(30);
ALTER TABLE boundary_rules ADD COLUMN applies_when_json TEXT;
ALTER TABLE boundary_rules ADD COLUMN user_facing_reason TEXT;
ALTER TABLE boundary_rules ADD COLUMN alternatives_json TEXT;
ALTER TABLE boundary_rules ADD COLUMN required_qualifications_json TEXT;
ALTER TABLE boundary_rules ADD COLUMN required_partner_role TEXT;
ALTER TABLE boundary_rules ADD COLUMN required_disclosure TEXT;
ALTER TABLE boundary_rules ADD COLUMN professional_review_recommended BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE boundary_rules SET
    structure_key = rule_key,
    title = rule_key,
    description = rationale,
    normalized_requirement = statement,
    source_status = 'WARNING',
    applies_when_json = '{}',
    user_facing_reason = rationale,
    alternatives_json = '[]',
    required_qualifications_json = '[]'
WHERE structure_key IS NULL;
ALTER TABLE boundary_rules ALTER COLUMN structure_key SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN title SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN description SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN normalized_requirement SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN source_status SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN applies_when_json SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN user_facing_reason SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN alternatives_json SET NOT NULL;
ALTER TABLE boundary_rules ALTER COLUMN required_qualifications_json SET NOT NULL;
ALTER TABLE boundary_rules ADD CONSTRAINT ck_boundary_rule_source_status
    CHECK (source_status IN ('COMPLETE', 'PARTIAL', 'WARNING', 'UNAVAILABLE'));
CREATE UNIQUE INDEX uk_boundary_rule_canonical
    ON boundary_rules(boundary_version_id, rule_type, structure_key, rule_key);

ALTER TABLE boundary_questions ADD COLUMN answer_type VARCHAR(20);
ALTER TABLE boundary_questions ADD COLUMN options_json TEXT;
ALTER TABLE boundary_questions ADD COLUMN required BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE boundary_questions ADD COLUMN related_rule_ids_json TEXT;
ALTER TABLE boundary_questions ADD COLUMN related_evidence_ids_json TEXT;
UPDATE boundary_questions SET answer_type = 'TEXT', options_json = '[]',
    related_rule_ids_json = '[]', related_evidence_ids_json = '[]'
WHERE answer_type IS NULL;
ALTER TABLE boundary_questions ALTER COLUMN answer_type SET NOT NULL;
ALTER TABLE boundary_questions ALTER COLUMN options_json SET NOT NULL;
ALTER TABLE boundary_questions ALTER COLUMN related_rule_ids_json SET NOT NULL;
ALTER TABLE boundary_questions ALTER COLUMN related_evidence_ids_json SET NOT NULL;
ALTER TABLE boundary_questions ADD CONSTRAINT ck_boundary_question_answer_type
    CHECK (answer_type IN ('TEXT', 'SINGLE_SELECT', 'MULTI_SELECT', 'BOOLEAN'));

ALTER TABLE job_events DROP CONSTRAINT ck_job_event_status;
ALTER TABLE job_events ADD CONSTRAINT ck_job_event_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'NEEDS_INPUT', 'BLOCKED'));
