package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.domain.ConceptAttemptError;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConceptFactoryMigrationContractTests {
    @Test
    void v6CheckContainsEveryConceptAttemptErrorAndV7AddsPerSlotBudget() throws Exception {
        String v6 = Files.readString(Path.of("src/main/resources/db/migration/V6__concept_factory_runtime_stabilization.sql"));
        for (ConceptAttemptError error : ConceptAttemptError.values()) {
            assertThat(v6).contains("'" + error.name() + "'");
        }
        String v7 = Files.readString(Path.of("src/main/resources/db/migration/V7__concept_factory_runtime_completion.sql"));
        assertThat(v7).contains("ADD COLUMN replacement_rounds", "BETWEEN 0 AND 2");
    }
}
