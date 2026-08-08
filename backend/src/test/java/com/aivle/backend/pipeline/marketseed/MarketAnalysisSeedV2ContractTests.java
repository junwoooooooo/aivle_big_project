package com.aivle.backend.pipeline.marketseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.integration.application.MarketResultIntakeService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketAnalysisSeedV2ContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void snapshotIdentityTimeAndDecimalValuesProduceStableHash() {
        var first = mapper.readTree("""
            {"snapshotId":"s1","schemaVersion":"1.0","createdAt":"2026-08-08T00:00:00Z",
             "share":2.50,"nested":{"b":2,"a":1}}
            """);
        var reordered = mapper.readTree("""
            {"nested":{"a":1,"b":2},"share":2.5,"createdAt":"2026-08-08T00:00:00Z",
             "schemaVersion":"1.0","snapshotId":"s1"}
            """);
        String hash = new SnapshotHasher(mapper).hash(first);
        assertThat(hash).isEqualTo(new SnapshotHasher(mapper).hash(reordered)).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void persistedSnapshotDomainHasNoPublicMutationMethod() {
        var publicMutators = Arrays.stream(MarketAnalysisSeedSnapshot.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getName().startsWith("set") || method.getName().startsWith("update")
                || method.getName().startsWith("replace") || method.getName().startsWith("finalize"))
            .toList();
        assertThat(publicMutators).isEmpty();
        var value = MarketAnalysisSeedSnapshot.create("snapshot-1", 1L, 2L, "concept-1", "2.0",
            "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64), "{}", 3L, Instant.EPOCH);
        assertThat(value.getId()).isEqualTo("snapshot-1");
    }

    @Test
    void marketResultIntakeHasNoConceptOrPlanningMutationDependency() {
        var dependencyNames = Arrays.stream(MarketResultIntakeService.class.getDeclaredFields())
            .map(field -> field.getType().getSimpleName()).toList();
        assertThat(dependencyNames).doesNotContain("ConceptRepository", "ConceptHypothesisDecisionRepository",
            "PlanningChangeProposalRepository", "PlanningChangeDecisionRepository", "PlanningSnapshotRepository");
    }
}
