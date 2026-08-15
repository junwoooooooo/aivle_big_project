-- Marketing Source는 CPV2 authority와 legacy authority를 상호 배타적으로 보존한다.
ALTER TABLE marketing_source_snapshots
    DROP CONSTRAINT fk_marketing_source_selection,
    DROP CONSTRAINT fk_marketing_source_concept,
    ALTER COLUMN selection_id DROP NOT NULL,
    ALTER COLUMN concept_id DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(40) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN portfolio_selection_id BIGINT,
    ADD COLUMN portfolio_concept_id VARCHAR(64);

ALTER TABLE marketing_source_snapshots
    ADD CONSTRAINT fk_marketing_source_selection
        FOREIGN KEY (selection_id, project_id) REFERENCES concept_selections(id, project_id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_marketing_source_concept
        FOREIGN KEY (concept_id, project_id) REFERENCES concepts(id, project_id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_marketing_source_portfolio_selection
        FOREIGN KEY (portfolio_selection_id, project_id)
        REFERENCES concept_portfolio_selections(id, project_id) ON DELETE NO ACTION,
    ADD CONSTRAINT fk_marketing_source_portfolio_concept
        FOREIGN KEY (portfolio_concept_id, project_id)
        REFERENCES concept_portfolio_concepts(id, project_id) ON DELETE NO ACTION,
    ADD CONSTRAINT ck_marketing_source_type
        CHECK (source_type IN ('LEGACY', 'CONCEPT_PORTFOLIO_V2')),
    ADD CONSTRAINT ck_marketing_source_authority
        CHECK ((source_type = 'LEGACY'
                AND selection_id IS NOT NULL AND concept_id IS NOT NULL
                AND portfolio_selection_id IS NULL AND portfolio_concept_id IS NULL)
            OR (source_type = 'CONCEPT_PORTFOLIO_V2'
                AND selection_id IS NULL AND concept_id IS NULL
                AND portfolio_selection_id IS NOT NULL AND portfolio_concept_id IS NOT NULL));
