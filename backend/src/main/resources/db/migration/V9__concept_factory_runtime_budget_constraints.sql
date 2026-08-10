ALTER TABLE concept_factory_runs DROP CONSTRAINT ck_concept_run_inspected;
ALTER TABLE concept_factory_runs
    ADD CONSTRAINT ck_concept_run_inspected CHECK (inspected_candidate_count >= 0);

ALTER TABLE concept_factory_runs DROP CONSTRAINT ck_concept_run_provider_retry;
ALTER TABLE concept_factory_runs
    ADD CONSTRAINT ck_concept_run_provider_retry CHECK (provider_transient_retry_count >= 0);
