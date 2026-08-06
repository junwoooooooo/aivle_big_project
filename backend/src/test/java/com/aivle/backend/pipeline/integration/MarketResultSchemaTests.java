package com.aivle.backend.pipeline.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketResultSchemaTests {
    @Test void schemaRequiresEvidenceBackedCompetitorsAndDecisionReadyProposals() throws Exception {
        Path schemaPath = Path.of("..", "docs", "rebuild", "contracts", "market-analysis-result-v1.schema.json");
        var schema = new ObjectMapper().readTree(Files.readString(schemaPath));
        var competitorRequired = schema.path("properties").path("competitors").path("items").path("required");
        assertThat(competitorRequired.toString()).contains("sourceReferences", "verificationStatus", "officialUrl");
        var proposalRequired = schema.path("properties").path("planningChangeProposals").path("items").path("required");
        assertThat(proposalRequired.toString()).contains("meaningfulTitle", "before", "after", "decisionStatus");
        assertThat(schema.path("properties").path("resultHash").path("pattern").asText()).startsWith("^sha256:");
    }
}
