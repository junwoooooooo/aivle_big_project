package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

class FinancialInputDocumentV21Tests {
    private final FinancialInputDocumentService service = new FinancialInputDocumentService(new ObjectMapper());

    @Test
    void validatesWholeDocumentBeforeReturningNormalizedValues() throws Exception {
        byte[] completed = completeTemplate(true);
        var values = service.parse(file(completed));
        assertThat(values.path("revenueModel").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(values.path("annualFixedLaborCost").path("currency").asText()).isEqualTo("KRW");
        assertThat(values.path("threeYearTargets").path("years")).hasSize(3);
    }

    @Test
    void rejectsIncompleteDocumentWithoutPartialResult() throws Exception {
        assertThatThrownBy(() -> service.parse(file(completeTemplate(false))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("필수 항목");
    }

    @Test
    void rejectsOutOfRangePercentageAndUnknownFieldKey() throws Exception {
        assertThatThrownBy(() -> service.parse(file(withValue(completeTemplate(true), "monthlyChurnRate", "101"))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0에서 100 사이");
        assertThatThrownBy(() -> service.parse(file(withValue(completeTemplate(true), "annualFixedLaborCost", "10000", "unknownAmount"))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("지원하지 않는");
    }

    private byte[] completeTemplate(boolean complete) throws Exception {
        byte[] template = service.template(9L);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (var table : document.getTables()) {
                String key = table.getRow(0).getCell(0).getText().replace("fieldKey:", "").trim();
                String value = switch (key) {
                    case "revenueModel" -> "SUBSCRIPTION";
                    case "threeYearTargets" -> "100,200,400";
                    case "newCustomerCount" -> complete ? "1500" : "";
                    case "monthlyChurnRate" -> "3.5";
                    default -> "10000";
                };
                table.getRow(2).getCell(0).setText(value);
            }
            document.write(output); return output.toByteArray();
        }
    }
    private MockMultipartFile file(byte[] content) { return new MockMultipartFile("file", "finance.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content); }

    private byte[] withValue(byte[] source, String key, String value, String... renamedKey) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(source));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (var table : document.getTables()) {
                if (!table.getRow(0).getCell(0).getText().trim().equals("fieldKey: " + key)) continue;
                if (renamedKey.length > 0) {
                    var cell = table.getRow(0).getCell(0);
                    while (!cell.getParagraphs().isEmpty()) cell.removeParagraph(0);
                    cell.setText("fieldKey: " + renamedKey[0]);
                }
                table.getRow(2).getCell(0).setText(value);
            }
            document.write(output); return output.toByteArray();
        }
    }
}
