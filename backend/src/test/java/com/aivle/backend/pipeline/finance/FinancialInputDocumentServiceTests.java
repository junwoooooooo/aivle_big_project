package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService;
import com.aivle.backend.pipeline.finance.application.FinancialPreparationFactory;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

class FinancialInputDocumentServiceTests {
    @Test
    void templateUsesSeparateInputTablesThatRemainImportable() throws Exception {
        MarketResearchVersionRepository marketVersions = mock(MarketResearchVersionRepository.class);
        when(marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(1L, MarketResearchRun.Kind.FULL))
            .thenReturn(Optional.empty());
        FinancialInputDocumentService service = new FinancialInputDocumentService(new ObjectMapper(), marketVersions);

        byte[] template = service.template(1L);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template))) {
            assertThat(document.getParagraphs().get(0).getRuns().get(0).getColor()).isEqualTo("1F4E79");
            assertThat(document.getTables()).hasSize(1 + FinancialPreparationFactory.ALL_KEYS.size());
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getColor()).isEqualTo("D9E2F3");
            for (String key : FinancialPreparationFactory.ALL_KEYS) {
                var table = document.getTables().stream()
                    .filter(value -> value.getRows().size() == 2 && key.equals(value.getRow(1).getCell(0).getText().trim()))
                    .findFirst().orElseThrow();
                assertThat(table.getRow(0).getCell(0).getText().trim()).isEqualTo("fieldKey");
                assertThat(table.getRow(0).getCell(1).getText().trim()).isEqualTo("입력값");
                assertThat(table.getRow(1).getCell(0).getColor()).isEqualTo("F5F7FA");
                assertThat(table.getRow(1).getCell(1).getColor()).isEqualTo("F8FBFF");
            }
            find(document, "annualFixedLaborCost").getRow(1).getCell(1).setText("120000000");
            find(document, "newCustomerCount").getRow(1).getCell(1).setText("1500");
            find(document, "revenueModel").getRow(1).getCell(1).setText("subscription");
            find(document, "threeYearTargets").getRow(1).getCell(1).setText("100,200,400");

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            document.write(out);
            var values = service.parse(new MockMultipartFile("file", "finance.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", out.toByteArray()));
            assertThat(values.path("annualFixedLaborCost").path("amount").asLong()).isEqualTo(120000000L);
            assertThat(values.path("newCustomerCount").asLong()).isEqualTo(1500L);
            assertThat(values.path("revenueModel").asText()).isEqualTo("SUBSCRIPTION");
            assertThat(values.path("threeYearTargets").path("years").get(2).path("value").asLong()).isEqualTo(400L);
        }
    }

    private org.apache.poi.xwpf.usermodel.XWPFTable find(XWPFDocument document, String key) {
        return document.getTables().stream()
            .filter(value -> value.getRows().size() == 2 && key.equals(value.getRow(1).getCell(0).getText().trim()))
            .findFirst().orElseThrow();
    }
}
