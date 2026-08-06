package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import org.junit.jupiter.api.Test;
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
}
