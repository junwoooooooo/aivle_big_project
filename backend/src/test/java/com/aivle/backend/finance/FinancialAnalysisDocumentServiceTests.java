package com.aivle.backend.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.service.FinancialAnalysisDocumentService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class FinancialAnalysisDocumentServiceTests {
    @Test
    void exportsAllFinancialAnalysisSectionsToDocx() throws Exception {
        FinancialModuleResponse result = new FinancialModuleResponse(null,
            List.of(new FinancialModuleResponse.ChartPoint(1, BigDecimal.valueOf(1000000), BigDecimal.valueOf(-200000), BigDecimal.valueOf(-1200000))),
            List.of(new FinancialModuleResponse.AnnualProjection(1, BigDecimal.valueOf(12000000), BigDecimal.valueOf(3000000), BigDecimal.valueOf(9000000), BigDecimal.valueOf(6000000), BigDecimal.valueOf(3000000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(3000000), BigDecimal.valueOf(25))),
            List.of(new FinancialModuleResponse.StressScenario("BASE", "Base", 8, BigDecimal.valueOf(3000000), BigDecimal.valueOf(5000000), List.of())),
            new FinancialModuleResponse.MonteCarloSummary(2000, BigDecimal.valueOf(-100), BigDecimal.valueOf(0), BigDecimal.valueOf(100), BigDecimal.valueOf(40), BigDecimal.valueOf(30), 1L),
            new FinancialModuleResponse.ModuleReport("기준 시나리오 결과", List.of("핵심 발견"), List.of("주의 사항"), List.of("권장 조치"), "계획 시뮬레이션입니다."), null);

        byte[] output = new FinancialAnalysisDocumentService().create(result);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output))) {
            String text = document.getParagraphs().stream().map(value -> value.getText()).reduce("", String::concat);
            assertThat(text).contains("재무 분석 결과 보고서", "3개년 추정 손익계산서", "월별 매출 및 현금흐름", "시나리오 스트레스 테스트", "몬테카를로 시뮬레이션");
            assertThat(document.getTables()).hasSize(5);
            assertThat(document.getAllPictures()).hasSize(3);
        }
    }
}
