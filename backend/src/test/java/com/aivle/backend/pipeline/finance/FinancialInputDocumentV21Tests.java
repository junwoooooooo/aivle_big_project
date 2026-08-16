package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService.FinancialInputDocumentException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
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
            assertThat(document.getTables().get(0).getRows()).hasSize(5);
            assertThat(document.getTables().get(0).getRow(3).getCell(0).getText())
                .isEqualTo("산정 근거 · 선택");
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
    void userProvidedLegacyDocumentParsesPrimaryValuesAndPreservesNotesExactly() throws Exception {
        byte[] fixture = userFixture();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixture)))
            .isEqualToIgnoringCase("A66124E0302673F113682370FD2D558F9A8568A376A5B36CA1D885C81D7A02B8");

        var values = service.parse(file(fixture));

        assertMoney(values, "annualFixedLaborCost", "160000000");
        assertMoney(values, "annualFixedRentAndManagementCost", "24000000");
        assertMoney(values, "annualFixedInfrastructureCost", "15000000");
        assertMoney(values, "initialDevelopmentAndRnDCost", "90000000");
        assertMoney(values, "initialEquipmentAndInfrastructureCost", "20000000");
        assertMoney(values, "initialPatentAndLicensingCost", "6000000");
        assertMoney(values, "totalMarketingCost", "30000000");
        assertMoney(values, "totalSalesCost", "18000000");
        assertThat(values.path("newCustomerCount").asLong()).isEqualTo(5000);
        assertThat(values.path("revenueModel").asText()).isEqualTo("HYBRID");
        assertMoney(values, "unitPrice", "500");
        assertMoney(values, "monthlySubscriptionPrice", "2000000");
        assertThat(values.path("monthlyChurnRate").decimalValue()).isEqualByComparingTo("4.5");
        assertMoney(values, "unitVariableCost", "350");
        assertMoney(values, "paymentFee", "50");
        assertMoney(values, "partnerPayout", "0");
        assertMoney(values, "shippingCost", "0");
        assertMoney(values, "customerIncrementalInfraCost", "100");
        assertThat(values.path("threeYearTargets").path("years").get(0).path("value").decimalValue())
            .isEqualByComparingTo("100");
        assertThat(values.path("threeYearTargets").path("years").get(1).path("value").decimalValue())
            .isEqualByComparingTo("300");
        assertThat(values.path("threeYearTargets").path("years").get(2).path("value").decimalValue())
            .isEqualByComparingTo("900");
        assertThat(values.path(FinancialInputDocumentService.INPUT_NOTES).size()).isEqualTo(19);
        assertThat(values.path(FinancialInputDocumentService.INPUT_NOTES).path("annualFixedLaborCost").asText())
            .contains("2명", "3~4인");
        assertThat(values.path("annualFixedLaborCost").path("amount").asLong())
            .isNotEqualTo(160000000234L);
    }

    @Test
    void newTemplateStoresSeparateRationaleWithoutChangingCalculationValue() throws Exception {
        byte[] document = completeTemplate(Map.of("annualFixedLaborCost", "160000000"),
            Map.of("annualFixedLaborCost", "개발자 2명, 초기 3~4인 기준"));
        var values = service.parse(file(document));
        assertMoney(values, "annualFixedLaborCost", "160000000");
        assertThat(values.path(FinancialInputDocumentService.INPUT_NOTES)
            .path("annualFixedLaborCost").asText()).isEqualTo("개발자 2명, 초기 3~4인 기준");
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
        return completeTemplate(overrides, Map.of());
    }

    private byte[] completeTemplate(Map<String, String> overrides, Map<String, String> notes) throws Exception {
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
                table.getRow(4).getCell(0).setText(notes.getOrDefault(key, ""));
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

    private byte[] userFixture() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/fixtures/finance/user-finance-readiness-input.docx.b64")) {
            assertThat(input).isNotNull();
            String base64 = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            return Base64.getMimeDecoder().decode(base64);
        }
    }

    private void assertMoney(tools.jackson.databind.JsonNode values, String key, String expected) {
        assertThat(values.path(key).path("amount").decimalValue()).isEqualByComparingTo(expected);
        assertThat(values.path(key).path("currency").asText()).isEqualTo("KRW");
    }
}
