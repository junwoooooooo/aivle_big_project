package com.aivle.backend.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import com.lowagie.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialAnalysisPdfV21Tests {
    @Test
    void rendersAuthoritativeCalculationScenarioAndNarrativeAsPdf() throws Exception {
        FinancialModuleResponse result = new FinancialModuleResponse(null,
            List.of(new FinancialModuleResponse.ChartPoint(1, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(-200_000), BigDecimal.valueOf(-1_200_000))),
            List.of(new FinancialModuleResponse.AnnualProjection(1, BigDecimal.valueOf(12_000_000), BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(9_000_000), BigDecimal.valueOf(6_000_000), BigDecimal.valueOf(3_000_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25))),
            List.of(new FinancialModuleResponse.StressScenario("BASE", "기준", 2, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(5_000_000), List.of())),
            new FinancialModuleResponse.MonteCarloSummary(2_000, BigDecimal.valueOf(-100), BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(40), BigDecimal.valueOf(30), 1L),
            new FinancialModuleResponse.ModuleReport("기준 시나리오 결과", List.of("핵심 발견"), List.of("주의 사항"), List.of("권장 조치"), "계획 시뮬레이션입니다.", "SYSTEM_CALCULATION", "SUCCEEDED", null), null);

        byte[] output = new FinancialAnalysisPdfService().create(result);

        Files.createDirectories(Path.of("build", "qa"));
        Files.write(Path.of("build", "qa", "v21-finance-readiness.pdf"), output);
        assertThat(output).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        PdfReader reader = new PdfReader(output);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        reader.close();
    }
}
