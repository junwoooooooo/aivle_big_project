package com.aivle.backend.taskrun.service;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.taskrun.domain.TaskType;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @ActiveProfiles("test")
class CanonicalInputHasherSpringMapperDiagnosticsTests {
    @Autowired ObjectMapper applicationMapper;
    @Autowired CanonicalInputHasher dedicatedHasher;

    @Test
    void canonicalHashDoesNotDependOnTheSpringApplicationMapper() throws Exception {
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream(
                "/canonical/concept-portfolio-delta-canonical-fixture-v1.json")) {
            fixture = new ObjectMapper().readTree(stream);
        }
        String input = fixture.path("inputJson").asText();
        String expected = fixture.path("expectedHash").asText();
        String applicationMapperHash = new CanonicalInputHasher(applicationMapper).hash(
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "1.0", "ko-KR", input);

        assertThat(dedicatedHasher.hash(TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION,
            "1.0", "ko-KR", input)).isEqualTo(expected);
        assertThat(applicationMapperHash).isEqualTo(expected);
    }
}
