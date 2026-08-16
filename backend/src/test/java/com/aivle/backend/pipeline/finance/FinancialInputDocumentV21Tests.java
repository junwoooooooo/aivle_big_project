package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService.FinancialInputDocumentException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

class FinancialInputDocumentV21Tests {
    private final FinancialInputDocumentService service = new FinancialInputDocumentService(new ObjectMapper());

    @Test
    void generatedTemplateRoundTripAcceptsRealisticKoreanFormattedValues() throws Exception {
        byte[] template = service.template(9L);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(template))) {
            String text = document.getParagraphs().stream().map(value -> value.getText())
                .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("[필수]", "[선택]", "1,000 / 2,000 / 4,000");
        }
        var values = service.parse(file(completeTemplate(Map.of(
            "annualFixedLaborCost", "120,000,000원",
            "annualFixedRentAndManagementCost", "36 000 000",
            "threeYearTargets", "1,000 / 2,000 / 4,000",
            "newCustomerCount", "1,500",
            "revenueModel", "정기 구독 (SUBSCRIPTION)",
            "monthlyChurnRate", "3.5%",
            "shippingCost", "해당 없음"))));

        assertThat(values.path("annualFixedLaborCost").path("amount").decimalValue())
            .isEqualByComparingTo("120000000");
        assertThat(values.path("annualFixedRentAndManagementCost").path("amount").decimalValue())
            .isEqualByComparingTo("36000000");
        assertThat(values.path("newCustomerCount").asLong()).isEqualTo(1500);
        assertThat(values.path("revenueModel").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(values.path("monthlyChurnRate").decimalValue()).isEqualByComparingTo("3.5");
        assertThat(values.path("threeYearTargets").path("years").get(2).path("value").decimalValue())
            .isEqualByComparingTo("4000");
        assertThat(values.has("shippingCost")).isFalse();
    }

    @Test
    void legacyCommaTargetsAndOptionalBlankRemainCompatible() throws Exception {
        var values = service.parse(file(completeTemplate(Map.of(
            "threeYearTargets", "100,200,400", "unitPrice", "", "paymentFee", "N/A"))));
        assertThat(values.path("threeYearTargets").path("years")).hasSize(3);
        assertThat(values.has("unitPrice")).isFalse();
        assertThat(values.has("paymentFee")).isFalse();
    }

    @Test
    void invalidAndMissingValuesKeepFieldSpecificSafeIssues() throws Exception {
        assertThatThrownBy(() -> service.parse(file(completeTemplate(Map.of(
            "threeYearTargets", "1억원 / two / 3", "newCustomerCount", "")))))
            .isInstanceOfSatisfying(FinancialInputDocumentException.class, exception -> {
                assertThat(exception.issues()).extracting(FinancialInputDocumentService.ValidationIssue::field)
                    .contains("threeYearTargets", "newCustomerCount");
                var target = exception.issues().stream()
                    .filter(value -> "threeYearTargets".equals(value.field())).findFirst().orElseThrow();
                assertThat(target.message()).contains("1·2·3년차");
                assertThat(target.rawSafeSummary()).hasSizeLessThanOrEqualTo(33).doesNotContain("억원 / two / 3\n");
            });
    }

    @Test
    void rejectsOutOfRangePercentageDuplicateAndUnknownFieldKey() throws Exception {
        assertThatThrownBy(() -> service.parse(file(completeTemplate(Map.of("monthlyChurnRate", "101%")))))
            .isInstanceOfSatisfying(FinancialInputDocumentException.class, exception ->
                assertThat(exception.issues()).anySatisfy(issue -> {
                    assertThat(issue.field()).isEqualTo("monthlyChurnRate");
                    assertThat(issue.message()).contains("0에서 100");
                }));
        assertThatThrownBy(() -> service.parse(file(withExtraField(
            completeTemplate(Map.of()), "unknownAmount", "10000"))))
            .isInstanceOfSatisfying(FinancialInputDocumentException.class, exception ->
                assertThat(exception.issues()).anySatisfy(issue -> assertThat(issue.field()).isEqualTo("document")));
        assertThatThrownBy(() -> service.parse(file(withExtraField(
            completeTemplate(Map.of()), "annualFixedLaborCost", "10000"))))
            .isInstanceOfSatisfying(FinancialInputDocumentException.class, exception ->
                assertThat(exception.issues()).anySatisfy(issue -> {
                    assertThat(issue.field()).isEqualTo("annualFixedLaborCost");
                    assertThat(issue.message()).contains("두 번");
                }));
    }

    private byte[] completeTemplate(Map<String, String> overrides) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(service.template(9L)));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (var table : document.getTables()) {
                String key = table.getRow(0).getCell(0).getText().replace("fieldKey:", "").trim();
                String value = overrides.containsKey(key) ? overrides.get(key) : switch (key) {
                    case "revenueModel" -> "SUBSCRIPTION";
                    case "threeYearTargets" -> "1000 / 2000 / 4000";
                    case "newCustomerCount" -> "1500";
                    case "monthlyChurnRate" -> "3.5";
                    default -> "10000";
                };
                table.getRow(2).getCell(0).setText(value);
            }
            document.write(output); return output.toByteArray();
        }
    }

    private byte[] withExtraField(byte[] source, String key, String value) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(source));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var table = document.createTable(3, 1);
            table.getRow(0).getCell(0).setText("fieldKey: " + key);
            table.getRow(1).getCell(0).setText("입력값"); table.getRow(2).getCell(0).setText(value);
            document.write(output); return output.toByteArray();
        }
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "finance.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
    }
}
