package com.aivle.backend.file.object;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObjectKeyGeneratorTests {
    private final ObjectKeyGenerator keys =
        new ObjectKeyGenerator();

    @Test
    void documentSourceUsesIdsAndNeverUsesUserFilename() {
        String key = keys.documentSource(
            11L,
            22L,
            33L,
            "docx"
        );

        assertThat(key)
            .startsWith(
                "projects/11/documents/22/versions/33/source/"
            )
            .endsWith(".docx")
            .doesNotContain("business-plan");
    }

    @Test
    void parserArtifactUsesVersionAndChecksum() {
        String checksum = "a".repeat(64);

        assertThat(keys.parserArtifact(
            11L,
            22L,
            33L,
            "spring-docx-blocks-v2",
            checksum
        )).isEqualTo(
            "projects/11/documents/22/versions/33/parser/"
                + "spring-docx-blocks-v2/" + checksum + ".json"
        );
    }
}
