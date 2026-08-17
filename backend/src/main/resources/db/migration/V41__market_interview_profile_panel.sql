ALTER TABLE market_interview_runs
    ADD COLUMN requested_sample_size INTEGER;

ALTER TABLE market_interview_runs
    ADD CONSTRAINT ck_market_interview_sample_size
    CHECK (requested_sample_size IS NULL OR requested_sample_size IN (20, 40, 80));

COMMENT ON COLUMN market_interview_runs.requested_sample_size IS
    'Profile-bank synthetic panel size. NULL marks legacy v1 historical runs.';
