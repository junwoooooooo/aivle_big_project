ALTER TABLE twin_survey_runs
    ADD COLUMN source_bm_plan_revision INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1;

ALTER TABLE twin_survey_runs DROP CONSTRAINT ck_twin_survey_run_state;
ALTER TABLE twin_survey_runs ADD CONSTRAINT ck_twin_survey_run_state
    CHECK (state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'STALE'));
ALTER TABLE twin_survey_runs ADD CONSTRAINT ck_twin_survey_run_bm_revision
    CHECK (source_bm_plan_revision >= 0);
ALTER TABLE twin_survey_runs ADD CONSTRAINT ck_twin_survey_run_attempt
    CHECK (attempt BETWEEN 1 AND 3);
