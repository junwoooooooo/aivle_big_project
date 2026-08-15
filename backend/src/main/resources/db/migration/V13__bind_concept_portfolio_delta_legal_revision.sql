ALTER TABLE concept_portfolio_delta_legal_reviews
    ADD COLUMN hypothesis_revision INTEGER NOT NULL DEFAULT 0;

ALTER TABLE concept_portfolio_delta_legal_reviews
    ADD CONSTRAINT ck_cp_delta_hypothesis_revision CHECK (hypothesis_revision >= 0);

CREATE INDEX idx_cp_delta_selection_revision
    ON concept_portfolio_delta_legal_reviews(selection_id, hypothesis_revision, created_at DESC)
    WHERE deleted_at IS NULL;
