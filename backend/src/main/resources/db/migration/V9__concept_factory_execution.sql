ALTER TABLE concept_factory_runs ADD COLUMN task_run_id VARCHAR(64);
ALTER TABLE concept_factory_runs ADD CONSTRAINT fk_concept_factory_task FOREIGN KEY (task_run_id, project_id) REFERENCES task_runs(id, project_id) ON DELETE NO ACTION;

ALTER TABLE concept_attempts ADD COLUMN error_classification VARCHAR(40);
ALTER TABLE concept_attempts ADD COLUMN safe_error_code VARCHAR(80);
ALTER TABLE concept_attempts ADD COLUMN retryable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE concept_attempts ADD COLUMN result_json TEXT;
ALTER TABLE concept_attempts ADD CONSTRAINT ck_concept_attempt_error CHECK (error_classification IS NULL OR error_classification IN (
    'SCHEMA_INVALID','TRANSIENT_PROVIDER_FAILURE','PERMANENT_PROVIDER_FAILURE','ORIGIN_INVALID',
    'LEGAL_REDESIGN_REQUIRED','LEGAL_REJECTED','INSUFFICIENT_INFORMATION','INTERNAL_EXECUTION_ERROR'
));

ALTER TABLE legal_context_packs ADD COLUMN industry VARCHAR(500) NOT NULL DEFAULT '미확인';
ALTER TABLE legal_context_packs ADD COLUMN region VARCHAR(500) NOT NULL DEFAULT '미확인';
ALTER TABLE legal_context_packs ADD COLUMN platform_role VARCHAR(1000) NOT NULL DEFAULT '미확인';
ALTER TABLE legal_context_packs ADD COLUMN transaction_structure TEXT NOT NULL DEFAULT '미확인';
ALTER TABLE legal_context_packs ADD COLUMN payment VARCHAR(1000) NOT NULL DEFAULT '미확인';
ALTER TABLE legal_context_packs ADD COLUMN personal_data VARCHAR(1000) NOT NULL DEFAULT '미확인';
ALTER TABLE legal_context_packs ADD COLUMN physical_activities_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE legal_context_packs ADD COLUMN qualifications_and_permits_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE legal_context_packs ADD COLUMN labeling_and_advertising_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE concepts ADD COLUMN candidate_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE concepts ADD COLUMN origin_trace_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE concept_legal_assessments ADD COLUMN assessment_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE concept_legal_assessments ADD COLUMN legal_trace_json TEXT NOT NULL DEFAULT '{}';
