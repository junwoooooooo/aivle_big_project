package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CanonicalInputHasherTests {
    @Test
    void matchesTheP2IdeaInterpretationFixtureByteForByte() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var fixture = mapper.readTree(Files.readAllBytes(Path.of(
            "../docs/contracts/fixtures/internal-ai-v1/tasks/idea-interpretation.request.valid.json")));
        CanonicalInputHasher hasher = new CanonicalInputHasher(mapper);

        assertThat(hasher.hash(TaskType.IDEA_INTERPRETATION, fixture.get("taskSchemaVersion").asText(),
            fixture.get("locale").asText(), mapper.writeValueAsString(fixture.get("input"))))
            .isEqualTo(fixture.get("canonicalInputHash").asText());
    }
}
