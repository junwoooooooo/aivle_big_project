package com.aivle.backend.pipeline.finalreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.finalreport.application.FinalReportComposer;
import com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;
import com.aivle.backend.project.entity.Project;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinalReportLaunchReadinessV21Tests {
    @Test
    void launchSectionAcceptsNewTechnologyOperationsAndFinanceCanonicalSources() {
        ObjectMapper mapper = new ObjectMapper();
        FinalReportComposer composer = new FinalReportComposer(mapper);
        Project project = mock(Project.class);
        when(project.getTitle()).thenReturn("출시 준비 프로젝트");
        when(project.getDescription()).thenReturn("설명");
        when(project.getIndustryCategory()).thenReturn("SaaS");
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        var report = composer.compose(project, 1, now, List.of(
            source(mapper, "LAUNCH_TECHNOLOGY", "technology-1", now),
            source(mapper, "LAUNCH_OPERATIONS", "operations-1", now),
            source(mapper, "FINANCE", "finance-1", now),
            source(mapper, "FINANCE_REPORT", "finance-report-1", now)));

        var launch = report.path("sections").get(3).path("sources");
        assertThat(values(launch, "type")).contains(
            "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE", "FINANCE_REPORT");
        assertThat(values(launch, "sourceId")).contains(
            "technology-1", "operations-1", "finance-1", "finance-report-1");
    }

    private ReportSource source(ObjectMapper mapper, String type, String id, Instant now) {
        return new ReportSource(type, id, null, null, "sha256:" + "a".repeat(64), now,
            mapper.createObjectNode().put("summary", type));
    }

    private List<String> values(tools.jackson.databind.JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false).map(item -> item.path(field).asText()).toList();
    }
}
