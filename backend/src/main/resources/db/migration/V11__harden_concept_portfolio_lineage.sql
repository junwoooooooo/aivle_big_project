ALTER TABLE concept_portfolio_concepts
    ADD CONSTRAINT uk_cp_concept_lineage UNIQUE (run_id, lineage_id);
