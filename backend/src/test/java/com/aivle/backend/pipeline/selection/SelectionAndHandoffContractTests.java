package com.aivle.backend.pipeline.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.integration.api.IntegrationApiModels.SelectedConceptMarketInputV1;
import com.aivle.backend.pipeline.integration.application.HandoffIdempotencyKey;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.selection.application.SelectionRequestFingerprint;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SelectionAndHandoffContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void snapshotHashIsStableAcrossObjectPropertyOrder() {
        SnapshotHasher hasher = new SnapshotHasher(mapper);
        JsonNode left = mapper.readTree("{\"concept\":{\"title\":\"A\",\"features\":[\"one\",\"two\"]},\"selectionId\":7}");
        JsonNode right = mapper.readTree("{\"selectionId\":7,\"concept\":{\"features\":[\"one\",\"two\"],\"title\":\"A\"}}");
        assertThat(hasher.hash(left)).isEqualTo(hasher.hash(right)).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void identicalCurrentSelectionAndHandoffInputsProduceIdenticalIdempotencyKeys() {
        String first = SelectionRequestFingerprint.create("concept-1", "sha256:" + "a".repeat(64), " 빠른 실행 ");
        String repeated = SelectionRequestFingerprint.create("concept-1", "sha256:" + "a".repeat(64), "빠른 실행");
        assertThat(repeated).isEqualTo(first);
        assertThat(HandoffIdempotencyKey.create(ModuleType.MARKET_ANALYSIS, first, "START_MARKET_ANALYSIS"))
            .isEqualTo(HandoffIdempotencyKey.create(ModuleType.MARKET_ANALYSIS, repeated, "START_MARKET_ANALYSIS"));
    }

    @Test
    void marketInputDtoMatchesProvidedJsonSchemaFixture() throws Exception {
        JsonNode schema = mapper.readTree(Files.readString(Path.of("..", "docs", "rebuild", "contracts", "selected-concept-market-input-v1.schema.json")));
        var input = new SelectedConceptMarketInputV1("selected-concept-market-input-v1", 10L, 20L, "snapshot-1",
            mapper.createObjectNode().put("title", "컨셉"), mapper.createObjectNode().put("status", "IMPLEMENTABLE"),
            "sha256:" + "b".repeat(64), Instant.parse("2026-08-06T00:00:00Z"));
        JsonNode fixture = mapper.valueToTree(input);
        Set<String> actual = new HashSet<>();
        fixture.propertyNames().forEach(actual::add);
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(node -> required.add(node.asText()));
        Set<String> properties = new HashSet<>();
        schema.path("properties").propertyNames().forEach(properties::add);

        assertThat(actual).isEqualTo(required).isEqualTo(properties);
        assertThat(fixture.path("contract").asText()).isEqualTo(schema.path("properties").path("contract").path("const").asText());
        assertThat(fixture.path("snapshotHash").asText()).startsWith("sha256:");
        assertThat(fixture.path("concept").isObject()).isTrue();
        assertThat(fixture.path("legalAssessment").isObject()).isTrue();
    }
}
