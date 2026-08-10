-- A reopened financial input soft-deletes its prior snapshot.  Permit a replacement
-- snapshot for the same preparation while still allowing only one active snapshot.
ALTER TABLE financial_input_snapshots DROP CONSTRAINT uk_financial_snapshot_preparation;

CREATE UNIQUE INDEX uk_financial_snapshot_active_preparation
    ON financial_input_snapshots(preparation_id)
    WHERE deleted_at IS NULL;
