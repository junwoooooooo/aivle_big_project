-- V26: bind every new Marketing execution to the exact current concept authority.
-- Legacy rows intentionally remain without exact revisions and are projected as historical/stale.
ALTER TABLE marketing_source_snapshots
    DROP CONSTRAINT uk_marketing_source_market_seed,
    ADD COLUMN source_selection_revision INTEGER,
    ADD COLUMN source_bm_plan_revision INTEGER;

CREATE UNIQUE INDEX uk_marketing_source_exact_lineage
    ON marketing_source_snapshots(
        source_market_seed_snapshot_id,
        source_selection_revision,
        source_bm_plan_revision
    )
    WHERE deleted_at IS NULL
      AND source_selection_revision IS NOT NULL
      AND source_bm_plan_revision IS NOT NULL;

ALTER TABLE pipeline_marketing_contents
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN previous_content_id VARCHAR(64),
    DROP CONSTRAINT ck_pipeline_marketing_content_status,
    ADD CONSTRAINT ck_pipeline_marketing_content_status
        CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','FINALIZED','STALE')),
    ADD CONSTRAINT ck_pipeline_marketing_content_attempt
        CHECK (attempt BETWEEN 1 AND 3),
    ADD CONSTRAINT fk_pipeline_marketing_content_previous
        FOREIGN KEY (previous_content_id) REFERENCES pipeline_marketing_contents(id) ON DELETE NO ACTION;

CREATE INDEX idx_pipeline_marketing_previous
    ON pipeline_marketing_contents(previous_content_id)
    WHERE previous_content_id IS NOT NULL;
