ALTER TABLE concept_hypothesis_decisions
    DROP CONSTRAINT ck_concept_hypothesis_type;

ALTER TABLE concept_hypothesis_decisions
    ADD CONSTRAINT ck_concept_hypothesis_type CHECK (hypothesis_type IN (
        'TARGET_REGION','REVENUE_MODEL','PRICE','CHANNELS','DIFFERENTIATORS',
        'PRE_MARKET_SOM_SHARE','PRE_MARKET_SOM'
    ));
