package com.aivle.backend.pipeline.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.integration.application.HandoffIdempotencyKey;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.selection.application.SelectionRequestFingerprint;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void marketSeedSnapshotSchemaIsTheOnlyMarketInputContract() throws Exception {
        JsonNode schema = mapper.readTree(Files.readString(Path.of("..", "docs", "rebuild", "contracts", "market-analysis-seed-snapshot-v1.schema.json")));
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(node -> required.add(node.asText()));
        assertThat(schema.path("properties").path("contract").path("const").asText())
            .isEqualTo("market-analysis-seed-snapshot-v1");
        assertThat(required).contains("snapshotId", "hash", "schemaVersion", "createdAt",
            "originalSeed", "aiInterpretation", "selectedConcept", "finalHypotheses", "legalResult");
        assertThat(required).doesNotContain("planningChangeProposals");
    }
}
