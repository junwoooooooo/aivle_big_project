package com.aivle.backend.pipeline.finalreport.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinalReportComposerTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final FinalReportComposer composer = new FinalReportComposer(mapper);

    @Test
    void sourceManifestHashIsDeterministicAndChangesWithSourceRevision() {
        Instant at = Instant.parse("2026-08-13T00:00:00Z");
        ReportSource marketV1 = new ReportSource("MARKET", "10", 1, null,
            "sha256:" + "1".repeat(64), at, mapper.createObjectNode().put("decision", "GO"));
        ReportSource marketV2 = new ReportSource("MARKET", "11", 2, null,
            "sha256:" + "2".repeat(64), at, mapper.createObjectNode().put("decision", "HOLD"));

        String first = composer.hash(composer.manifest(List.of(marketV1)));
        String same = composer.hash(composer.manifest(List.of(marketV1)));
        String changed = composer.hash(composer.manifest(List.of(marketV2)));

        assertThat(first).isEqualTo(same).matches("sha256:[0-9a-f]{64}");
        assertThat(changed).isNotEqualTo(first);
    }
}
