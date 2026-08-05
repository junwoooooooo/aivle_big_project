package com.aivle.backend.report.dto;

import java.util.List;

public record InterimReportResponse(
    String title,
    String metaInfo,
    String footerText,
    List<SectionDto> sections
) {
    public record SectionDto(
        String id,
        String type, // "KPI_GRID", "COMPARISON_TABLE" 등
        String sectionTitle,
        String gridRatio, // 예: "1.5fr 2.5fr 3fr 3.5fr"
        List<String> headers,
        List<KpiItemDto> data,
        List<TableRowDto> rows
    ) {}

    public record KpiItemDto(
        String label,
        String value,
        String highlightClass
    ) {}

    public record TableRowDto(
        String category,
        String input,
        String aiOutput,
        String gapInsight
    ) {}
}
