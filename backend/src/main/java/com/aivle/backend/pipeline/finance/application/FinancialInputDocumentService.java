package com.aivle.backend.pipeline.finance.application;

import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** DOCX is the user-facing import contract; every table's first two cells are parsed as key/value pairs. */
@Service
public class FinancialInputDocumentService {
    private static final String INK = "1F302A";
    private static final String MUTED = "596761";
    private static final String MINT_DARK = "27654F";
    private static final String MINT = "BCEFDC";
    private static final String MINT_LIGHT = "EEF9F5";
    private static final String MINT_PALE = "F7FBF9";
    private static final String BORDER = "9FD7C4";
    private final ObjectMapper mapper;
    private final MarketResearchVersionRepository marketVersions;

    public FinancialInputDocumentService(ObjectMapper mapper, MarketResearchVersionRepository marketVersions) {
        this.mapper = mapper;
        this.marketVersions = marketVersions;
    }

    public byte[] template(Long projectId) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            title(doc, "재무 분석 입력 템플릿");
            subtitle(doc, "필수값을 빠짐없이 입력하면, 재무 분석에서 손익·현금흐름·핵심 지표를 계산할 수 있습니다.");
            callout(doc, "각 카드의 ‘입력값’ 칸에만 값을 작성해 주세요. 금액은 원(KRW) 단위의 숫자로 입력합니다.");
            var market = marketVersions
                .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, MarketResearchRun.Kind.FULL)
                .map(v -> mapper.readTree(v.getResultJson()).path("market")).orElse(mapper.createObjectNode());
            XWPFTable referenceTable = doc.createTable(4, 2);
            String[][] references = {{"시장 참고값 (계산에 사용하지 않음)", "최신 시장 분석 값"}, {"TAM", display(market.path("tam"))},
                {"SAM", display(market.path("sam"))}, {"시장 성장률", display(market.path("growth"))}};
            for (int row = 0; row < references.length; row++) for (int column = 0; column < 2; column++)
                referenceTable.getRow(row).getCell(column).setText(references[row][column]);
            styleReferenceTable(referenceTable);

            heading(doc, "작성 방법", 2);
            body(doc, "각 항목은 독립된 입력 카드입니다. fieldKey는 수정하지 말고, ‘입력값’ 칸만 채워 주세요.");
            body(doc, "필수 항목은 반드시 입력하고, 해당하지 않는 선택 항목은 비워 둘 수 있습니다.");
            body(doc, "매출 모델은 ONE_TIME(일회성), SUBSCRIPTION(구독형), HYBRID(혼합형) 중 하나를 입력합니다.");

            addSection(doc, "1. 연간 고정비", List.of(
                new InputField("annualFixedLaborCost", "연간 고정 인건비", "급여, 상여금, 4대 보험 등 매년 반복되는 인건비의 합계를 입력합니다.", "120000000"),
                new InputField("annualFixedRentAndManagementCost", "연간 임차료 및 관리비", "사무실·매장 임차료와 관리비의 연간 합계를 입력합니다.", "36000000"),
                new InputField("annualFixedInfrastructureCost", "연간 인프라 운영비", "서버, SaaS, 통신 등 고정적으로 발생하는 운영비의 연간 합계를 입력합니다.", "18000000")));
            addSection(doc, "2. 초기 투자비", List.of(
                new InputField("initialDevelopmentAndRnDCost", "초기 개발 및 R&D 비용", "서비스 출시 전 개발, 연구, 외주 제작 등에 드는 일회성 비용을 입력합니다.", "80000000"),
                new InputField("initialEquipmentAndInfrastructureCost", "초기 장비 및 인프라 비용", "장비 구매, 초기 구축, 설치 등에 드는 일회성 비용을 입력합니다.", "30000000"),
                new InputField("initialPatentAndLicensingCost", "초기 특허 및 라이선스 비용", "특허 출원, 기술·콘텐츠 라이선스 취득 등에 드는 초기 비용을 입력합니다.", "5000000")));
            addSection(doc, "3. 고객 확보 및 성장 목표", List.of(
                new InputField("totalMarketingCost", "연간 마케팅비", "광고, 캠페인, 콘텐츠 제작 등 고객 확보를 위한 연간 비용 총액을 입력합니다.", "24000000"),
                new InputField("totalSalesCost", "연간 영업비", "영업 인력, 제휴 수수료, 영업 활동에 드는 연간 비용 총액을 입력합니다.", "12000000"),
                new InputField("newCustomerCount", "연간 신규 고객 수", "첫해에 새로 확보할 것으로 예상하는 고객 수를 정수로 입력합니다.", "1500"),
                new InputField("threeYearTargets", "3개년 성장 목표", "1년차, 2년차, 3년차 목표를 쉼표로 구분해 입력합니다.", "100,200,400")));
            addSection(doc, "4. 매출 모델 및 선택 비용", List.of(
                new InputField("revenueModel", "매출 모델", "일회성 판매는 ONE_TIME, 정기 구독은 SUBSCRIPTION, 둘 다 사용하면 HYBRID를 입력합니다.", "SUBSCRIPTION"),
                new InputField("unitPrice", "건당 판매 가격", "일회성 판매 시 고객 1건당 평균 판매 가격을 입력합니다.", "50000"),
                new InputField("monthlySubscriptionPrice", "월 구독 가격", "구독형 서비스의 고객 1인당 월 평균 구독료를 입력합니다.", "9900"),
                new InputField("monthlyChurnRate", "월 이탈률", "구독 고객 중 한 달 동안 이탈할 것으로 예상하는 비율을 퍼센트로 입력합니다.", "3.5"),
                new InputField("unitVariableCost", "건당 변동비", "판매 또는 서비스 제공 1건이 늘어날 때 함께 증가하는 비용을 입력합니다.", "12000"),
                new InputField("paymentFee", "결제 수수료", "결제 처리 수수료를 비용 금액으로 입력합니다.", "1500"),
                new InputField("partnerPayout", "파트너 지급액", "판매 1건당 파트너에게 지급하는 금액이 있으면 입력합니다.", "5000"),
                new InputField("shippingCost", "배송비", "판매 1건당 평균 배송비가 있으면 입력합니다.", "3000"),
                new InputField("customerIncrementalInfraCost", "고객 증가 인프라비", "고객 또는 사용량 증가에 따라 추가로 드는 1인당 인프라 비용을 입력합니다.", "500")));
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("재무 입력 템플릿을 만들 수 없습니다.", e);
        }
    }

    private void addSection(XWPFDocument doc, String title, List<InputField> fields) {
        heading(doc, title, 1);
        for (InputField field : fields) {
            fieldLabel(doc, field.label());
            body(doc, field.description());
            example(doc, "입력 예시  " + field.example());
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("fieldKey");
            table.getRow(0).getCell(1).setText("입력값");
            table.getRow(1).getCell(0).setText(field.key());
            table.getRow(1).getCell(1).setText("");
            styleInputTable(table);
        }
    }

    private void title(XWPFDocument doc, String value) { paragraph(doc, value, true, 24, MINT_DARK, 0, 90); }
    private void subtitle(XWPFDocument doc, String value) { paragraph(doc, value, false, 10, MUTED, 0, 160); }
    private void heading(XWPFDocument doc, String value, int level) {
        XWPFParagraph paragraph = paragraph(doc, value, true, level == 1 ? 15 : 12, MINT_DARK, level == 1 ? 250 : 140, 90);
        paragraph.setKeepNext(true);
        shade(paragraph, level == 1 ? MINT_LIGHT : MINT_PALE);
    }
    private void fieldLabel(XWPFDocument doc, String value) {
        XWPFParagraph paragraph = paragraph(doc, value, true, 11, INK, 150, 35);
        paragraph.setKeepNext(true);
    }
    private void body(XWPFDocument doc, String value) { paragraph(doc, value, false, 10, INK, 0, 35); }
    private void example(XWPFDocument doc, String value) { paragraph(doc, value, false, 9, MUTED, 0, 55); }
    private void callout(XWPFDocument doc, String value) {
        XWPFParagraph paragraph = paragraph(doc, "작성 안내  |  " + value, true, 10, MINT_DARK, 0, 160);
        shade(paragraph, MINT_LIGHT);
    }
    private XWPFParagraph paragraph(XWPFDocument doc, String value, boolean bold, int size, String color, int before, int after) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingBefore(before); paragraph.setSpacingAfter(after); paragraph.setSpacingBetween(1.12);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Malgun Gothic"); run.setFontSize(size); run.setBold(bold); run.setColor(color); run.setText(value);
        return paragraph;
    }
    private void styleReferenceTable(XWPFTable table) {
        table.setWidth("100%"); table.setInsideHBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, BORDER);
        table.setInsideVBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, BORDER);
        table.setLeftBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setRightBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setTopBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setBottomBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        for (int row = 0; row < table.getRows().size(); row++) for (XWPFTableCell cell : table.getRow(row).getTableCells()) {
            cell.setVerticalAlignment(XWPFVertAlign.CENTER); cell.setColor(row == 0 ? MINT : "FFFFFF");
            styleCell(cell, row == 0, row == 0 ? MINT_DARK : INK, 9);
        }
    }
    private void styleInputTable(XWPFTable table) {
        table.setWidth("100%"); table.setInsideHBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setInsideVBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setLeftBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setRightBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setTopBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setBottomBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.getRow(0).setHeightRule(TableRowHeightRule.AUTO);
        for (int row = 0; row < table.getRows().size(); row++) for (XWPFTableCell cell : table.getRow(row).getTableCells()) {
            cell.setVerticalAlignment(XWPFVertAlign.CENTER); cell.setColor(row == 0 ? MINT : row == 1 && cell == table.getRow(1).getCell(1) ? MINT_PALE : "FFFFFF");
            styleCell(cell, row == 0, row == 0 ? MINT_DARK : INK, row == 0 ? 9 : 10);
        }
        table.getRow(1).getCell(1).getParagraphArray(0).setSpacingAfter(100);
    }
    private void styleCell(XWPFTableCell cell, boolean bold, String color, int size) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            paragraph.setSpacingBefore(35); paragraph.setSpacingAfter(35); paragraph.setSpacingBetween(1.0);
            for (XWPFRun run : paragraph.getRuns()) { run.setFontFamily("Malgun Gothic"); run.setFontSize(size); run.setBold(bold); run.setColor(color); }
        }
    }
    private void shade(XWPFParagraph paragraph, String fill) {
        var properties = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd();
        shading.setVal(STShd.CLEAR); shading.setFill(fill);
    }

    private String display(tools.jackson.databind.JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "시장 분석 결과 없음";
        if (value.path("value").isNumber()) {
            double number = value.path("value").asDouble();
            String unit = value.path("unit").asText("");
            String formatted = Math.rint(number) == number ? String.format("%,.0f", number) : String.format("%,.2f", number);
            if ("PERCENT_PER_YEAR".equals(unit)) formatted += "% / 년";
            else if (!unit.isBlank()) formatted += " " + unit;
            String grade = value.path("grade").asText("");
            return grade.isBlank() ? formatted : formatted + " (" + grade + ")";
        }
        if (value.isValueNode()) return value.asText();
        if (value.path("amount").isNumber()) return value.path("amount").asText() + " " + value.path("currency").asText("KRW");
        if (value.path("base").isNumber()) return value.path("base").asText();
        if (value.path("percent").isNumber()) return value.path("percent").asText() + "%";
        return value.toString();
    }

    public ObjectNode parse(MultipartFile file) {
        if (file == null || file.isEmpty() || !Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase().endsWith(".docx"))
            throw new IllegalArgumentException("DOCX 파일만 업로드할 수 있습니다.");
        ObjectNode values = mapper.createObjectNode();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : doc.getTables()) for (XWPFTableRow row : table.getRows()) {
                if (row.getTableCells().size() < 2) continue;
                String key = row.getCell(0).getText().trim();
                String raw = row.getCell(1).getText().trim();
                if (!FinancialPreparationFactory.ALL_KEYS.contains(key) || raw.isBlank()) continue;
                if ("revenueModel".equals(key)) values.put(key, raw.toUpperCase());
                else if ("newCustomerCount".equals(key)) values.put(key, Long.parseLong(raw.replace(",", "")));
                else if ("monthlyChurnRate".equals(key)) values.put(key, Double.parseDouble(raw.replace("%", "").replace(",", "")));
                else if ("threeYearTargets".equals(key)) {
                    String[] parts = raw.split(",");
                    if (parts.length != 3) throw new IllegalArgumentException("threeYearTargets는 숫자 3개를 쉼표로 구분해야 합니다.");
                    ObjectNode target = values.putObject(key);
                    target.put("metric", "customerCount");
                    target.put("unit", "명");
                    var years = target.putArray("years");
                    for (int index = 0; index < 3; index++)
                        years.addObject().put("year", index + 1).put("value", Double.parseDouble(parts[index].trim().replace(",", "")));
                } else {
                    ObjectNode money = values.putObject(key);
                    money.put("amount", Double.parseDouble(raw.replaceAll("[^0-9.]", "")));
                    money.put("currency", "KRW");
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalArgumentException("템플릿의 값 형식을 확인해 주세요.", e);
        }
        return values;
    }

    private record InputField(String key, String label, String description, String example) { }
}
