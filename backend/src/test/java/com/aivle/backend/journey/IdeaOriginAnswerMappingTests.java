package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class IdeaOriginAnswerMappingTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IdeaOriginService service = new IdeaOriginService(
        null, null, null, null, null, null, mapper);

    @Test
    void targetClarificationPreservesExistingStructuredTarget() {
        ObjectNode snapshot = (ObjectNode) mapper.readTree("""
            {"target":{"customerTypes":["소비자","소상공인"],"segment":null,
            "situation":null,"needs":["합리적인 소비","환경 보호"]}}
            """);

        service.applyAnswer(snapshot, "target", "지역 복지재단 및 푸드뱅크");

        assertThat(snapshot.path("target").path("customerTypes").toString())
            .isEqualTo("[\"소비자\",\"소상공인\",\"지역 복지재단 및 푸드뱅크\"]");
        assertThat(snapshot.path("target").path("needs").toString())
            .isEqualTo("[\"합리적인 소비\",\"환경 보호\"]");
        assertThat(snapshot.path("target").path("segment").isNull()).isTrue();
    }
}
