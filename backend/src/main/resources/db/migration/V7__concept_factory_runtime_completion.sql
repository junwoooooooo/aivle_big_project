ALTER TABLE concept_slots
    ADD COLUMN replacement_rounds INTEGER NOT NULL DEFAULT 0;

ALTER TABLE concept_slots
    ADD CONSTRAINT ck_concept_slot_replacement_rounds
    CHECK (replacement_rounds BETWEEN 0 AND 2);
