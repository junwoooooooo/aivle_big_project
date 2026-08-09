package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConceptFactoryMigrationContractTests {
    @Test
    void currentMigrationCheckContainsEveryConceptAttemptErrorAndPerSlotAndDiscardGuards() throws Exception {
        String v6 = Files.readString(Path.of("src/main/resources/db/migration/V6__concept_factory_runtime_stabilization.sql"));
        assertThat(v6).contains("ck_concept_attempt_error");
        String v7 = Files.readString(Path.of("src/main/resources/db/migration/V7__concept_factory_runtime_completion.sql"));
        assertThat(v7).contains("ADD COLUMN replacement_rounds", "BETWEEN 0 AND 2");
        String v8 = Files.readString(Path.of("src/main/resources/db/migration/V8__concept_factory_cross_service_contract_hardening.sql"));
        for (ConceptAttemptError error : ConceptAttemptError.values()) {
            assertThat(v8).contains("'" + error.name() + "'");
        }
        assertThat(v8).contains("ADD COLUMN attempt_id", "ux_concept_rejection_summary_attempt");
    }
}
