package com.aivle.backend.pipeline.finalreport.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import com.aivle.backend.project.entity.Project;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

    @Test
    void manifestV2PinsCurrentConceptAndExactSourceIdentity() {
        var binding = mapper.createObjectNode().put("marketSeedSnapshotId", "seed-1")
            .put("selectionId", 7).put("selectionRevision", 3).put("bmPlanRevision", 2);
        var manifest = composer.manifest(binding, List.of(new ReportSource("MARKET", "market-9", 4, null,
            "sha256:" + "a".repeat(64), Instant.parse("2026-08-17T00:00:00Z"), mapper.createObjectNode())));

        assertThat(manifest.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(manifest.path("currentConcept")).isEqualTo(binding);
        assertThat(manifest.path("sources").get(0).path("id").asText()).isEqualTo("market-9");
    }

    @Test
    void canonicalMarketInterviewRemainsTruthfullyQualifiedWithoutTwinSurface() {
        Project project = mock(Project.class);
        when(project.getTitle()).thenReturn("프로젝트");
        var report = composer.compose(project, 1, Instant.parse("2026-08-17T00:00:00Z"), List.of());

        assertThat(report.path("sections").get(3).path("title").asText()).isEqualTo("시장 인터뷰");
        assertThat(report.path("sections").toString()).doesNotContain("TWIN_SURVEY").doesNotContain("트윈 패널");
        assertThat(report.path("sections").get(3).path("sources").get(0).path("label").asText())
            .contains("아직 시장 인터뷰");
        assertThat(report.path("caveat").asText()).contains("AI 가상 참여자").contains("실제 소비자 조사 결과를 의미하지 않습니다");
    }
}
