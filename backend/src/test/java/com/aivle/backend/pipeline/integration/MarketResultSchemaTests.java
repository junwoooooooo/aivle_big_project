package com.aivle.backend.pipeline.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketResultSchemaTests {
    @Test void schemaRequiresEvidenceBackedCompetitorsAndExcludesPlanningChanges() throws Exception {
        Path schemaPath = Path.of("..", "docs", "rebuild", "contracts", "market-analysis-result-v1.schema.json");
        var schema = new ObjectMapper().readTree(Files.readString(schemaPath));
        var competitorRequired = schema.path("properties").path("competitors").path("items").path("required");
        assertThat(competitorRequired.toString()).contains("sourceReferences", "verificationStatus", "officialUrl");
        assertThat(schema.path("properties").has("planningChangeProposals")).isFalse();
        assertThat(schema.path("required").toString()).doesNotContain("planningChangeProposals");
        assertThat(schema.path("properties").path("resultHash").path("pattern").asText()).startsWith("^sha256:");
    }
}
