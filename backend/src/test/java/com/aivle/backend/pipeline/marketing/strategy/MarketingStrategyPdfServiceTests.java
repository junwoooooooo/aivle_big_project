package com.aivle.backend.pipeline.marketing.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.document.KoreanPdfFontResolver;
import com.aivle.backend.pipeline.marketing.strategy.application.MarketingStrategyPdfService;
import com.aivle.backend.pipeline.marketing.strategy.domain.MarketingStrategyReport;
import java.time.Instant;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketingStrategyPdfServiceTests {
    @Test
    void embedsKoreanTextAndUsesFriendlyEvidenceLabels() throws Exception {
        String result = """
            {"executiveSummary":"재무 분석을 반영한 전략","targetCustomers":["타깃 고객"],
             "positioning":"운영 정보","coreMessages":[],"channelStrategies":[],"contentPillars":[],
             "campaignRoadmap":[],"budgetGuidelines":[],"risks":[],
             "evidenceRefs":["FINANCE:finance-1","FINANCE_REPORT:report-1"]}
            """;
        var report = MarketingStrategyReport.create("strategy-1", 7L, "task-1", "[]",
            "sha256:" + "a".repeat(64), "{}", result, 1L, Instant.parse("2026-08-18T00:00:00Z"));
        byte[] pdf = new MarketingStrategyPdfService(new ObjectMapper(), new KoreanPdfFontResolver()).render(report);
        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(pdf.length).isGreaterThan(2_000);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("마케팅 전략 보고서", "타깃 고객", "재무 분석");
            assertThat(text).doesNotContain("FINANCE:", "strategy-1", "sha256:");
        }
    }
}
