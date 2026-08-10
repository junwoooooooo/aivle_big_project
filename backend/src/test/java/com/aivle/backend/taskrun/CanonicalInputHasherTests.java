package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CanonicalInputHasherTests {
    @Test
    void hashesEquivalentNewPipelineInputDeterministically() {
        CanonicalInputHasher hasher = new CanonicalInputHasher(new ObjectMapper());

        String first = hasher.hash(TaskType.IDEA_BRIEF_DERIVATION, "1.0", "ko-KR",
            "{\"projectId\":1,\"sourceText\":\"idea\"}");
        String reordered = hasher.hash(TaskType.IDEA_BRIEF_DERIVATION, "1.0", "ko-KR",
            "{\"sourceText\":\"idea\",\"projectId\":1}");

        assertThat(first).isEqualTo(reordered).startsWith("sha256:").hasSize(71);
    }

    @Test
    void canonicalizesAllFiniteDecimalFormsUsingTheSharedFixture() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CanonicalInputHasher hasher = new CanonicalInputHasher(mapper);
        try (InputStream stream = getClass().getResourceAsStream(
                "/canonical/numeric-canonical-fixture-v1.json")) {
            JsonNode fixture = mapper.readTree(stream);
            String hash = hasher.hash(TaskType.valueOf(fixture.path("taskType").asText()),
                fixture.path("taskSchemaVersion").asText(), fixture.path("locale").asText(),
                fixture.path("inputJson").asText());
            assertThat(hash).isEqualTo(fixture.path("expectedHash").asText());
        }
    }

    @Test
    void equivalentDecimalSpellingsHaveTheSameHash() {
        CanonicalInputHasher hasher = new CanonicalInputHasher(new ObjectMapper());
        String canonical = hasher.hash(TaskType.CONCEPT_REDESIGN, "1.0", "ko-KR",
            "{\"value\":1,\"zero\":0,\"som\":100000000}");
        String alternate = hasher.hash(TaskType.CONCEPT_REDESIGN, "1.0", "ko-KR",
            "{\"som\":1e8,\"zero\":-0.0,\"value\":1.00}");
        assertThat(alternate).isEqualTo(canonical);
    }
}
