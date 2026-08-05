package com.aivle.backend.report.service;

import com.aivle.backend.report.dto.InterimReportResponse;
import com.aivle.backend.report.dto.InterimReportResponse.KpiItemDto;
import com.aivle.backend.report.dto.InterimReportResponse.SectionDto;
import com.aivle.backend.report.dto.InterimReportResponse.TableRowDto;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterimReportService {

    public InterimReportResponse generateInterimReport(Long projectId) {
        log.info("Generating interim report for project {}", projectId);

        // The current AI server exposes no interim-report endpoints. Return the
        // existing pending-state view until the report integration is implemented.
        List<KpiItemDto> kpis = List.of(
            new KpiItemDto("Market readiness", "Pending", "neutral"),
            new KpiItemDto("Business-model fit", "Pending", "neutral"),
            new KpiItemDto("Financial outlook", "Pending", "neutral")
        );
        List<TableRowDto> rows = List.of(
            new TableRowDto("Market", "Not evaluated", "Pending", "Run market analysis first"),
            new TableRowDto("Business model", "Not evaluated", "Pending", "Run business-model analysis first"),
            new TableRowDto("Financial", "Not evaluated", "Pending", "Run financial analysis first")
        );

        SectionDto kpiSection = new SectionDto(
            "sec_summary", "KPI_GRID", "Summary", null, null, kpis, null
        );
        SectionDto comparisonSection = new SectionDto(
            "sec_comparison", "COMPARISON_TABLE", "Plan versus analysis",
            "1.5fr 2.5fr 3fr 3.5fr",
            List.of("Category", "Plan", "Analysis", "Next step"),
            null, rows
        );

        return new InterimReportResponse(
            "Interim business report",
            "Project ID: PRJ_" + projectId,
            "AI Business Validation Platform",
            List.of(kpiSection, comparisonSection)
        );
    }
}
