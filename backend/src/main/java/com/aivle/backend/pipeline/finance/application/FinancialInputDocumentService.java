package com.aivle.backend.pipeline.finance.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FinancialInputDocumentService {
    private static final String BORDER = "C8D4E3";
    private static final String NUMBER = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";
    private static final Pattern THREE_TARGETS = Pattern.compile(
        "^\\s*(" + NUMBER + ")\\s*[,/]\\s*(" + NUMBER + ")\\s*[,/]\\s*(" + NUMBER + ")\\s*$");
    private static final Pattern TRAILING_NOTE = Pattern.compile("(?s)^(.+?)\\s*\\((.+)\\)\\s*$");
    public static final String INPUT_NOTES = "inputNotes";
    private static final Set<String> EMPTY_OPTIONAL = Set.of("해당 없음", "없음", "미정", "n/a", "na", "-");
    private final ObjectMapper mapper;

    public FinancialInputDocumentService(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] template(Long projectId) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            paragraph(doc, "재무 분석 입력 템플릿", true, 22, "1F4E79", 0, 80);
            paragraph(doc, "업로드한 값이 손익·현금흐름 분석의 기준이 됩니다. 금액은 원(KRW) 단위입니다.", false, 10, "52657D", 0, 120);
            paragraph(doc, "작성 안내 | fieldKey는 수정하지 말고 각 입력 칸만 작성해 주세요.", true, 10, "1F4E79", 0, 35);
            paragraph(doc, "[필수] 항목은 모두 작성해야 합니다. [선택] 항목은 비워 두거나 ‘해당 없음’으로 작성할 수 있습니다.", false, 10, "1F2937", 0, 35);
            paragraph(doc, "3개년 목표는 ‘1,000 / 2,000 / 4,000’처럼 슬래시로 구분해 주세요. 기존 ‘100,200,400’ 형식도 읽을 수 있습니다.", false, 10, "1F2937", 0, 35);
            paragraph(doc, "매출 모델은 일회성 판매(ONE_TIME), 정기 구독(SUBSCRIPTION), 혼합형(HYBRID) 중 하나를 작성합니다.", false, 10, "1F2937", 0, 150);
            String section = null;
            for (InputField field : fields()) {
                if (!field.section().equals(section)) {
                    section = field.section();
                    paragraph(doc, section, true, 15, "1F4E79", 180, 70);
                }
                paragraph(doc, (field.required() ? "[필수] " : "[선택] ") + field.label(), true, 11, "1F2937", 100, 30);
                paragraph(doc, field.description() + " · 입력 예시: " + field.example(), false, 9, "52657D", 0, 50);
                XWPFTable table = doc.createTable(5, 1);
                table.getRow(0).getCell(0).setText("fieldKey: " + field.key());
                table.getRow(1).getCell(0).setText("입력값 · " + (field.required() ? "필수" : "선택"));
                table.getRow(2).getCell(0).setText("\n\n\n");
                table.getRow(3).getCell(0).setText("산정 근거 · 선택");
                table.getRow(4).getCell(0).setText("\n\n\n");
                style(table);
            }
            doc.write(out); return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("재무 입력 템플릿을 만들 수 없습니다.", exception);
        }
    }

    public ObjectNode parse(MultipartFile file) {
        if (file == null || file.isEmpty() || !Optional.ofNullable(file.getOriginalFilename())
                .orElse("").toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new FinancialInputDocumentException(List.of(
                new ValidationIssue("file", "재무 입력 문서", "DOCX 파일을 선택해 주세요.", "")));
        }
        Map<String, InputField> fields = fieldMap();
        ObjectNode values = mapper.createObjectNode();
        ObjectNode notes = mapper.createObjectNode();
        Set<String> seen = new HashSet<>();
        Set<String> invalid = new HashSet<>();
        List<ValidationIssue> issues = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : doc.getTables()) {
                if (table.getRows().size() < 3 || table.getRow(0).getTableCells().size() != 1) continue;
                String marker = table.getRow(0).getCell(0).getText().trim();
                if (!marker.startsWith("fieldKey:")) continue;
                String key = marker.replaceFirst("^fieldKey:\\s*", "").trim();
                InputField field = fields.get(key);
                if (field == null) {
                    issues.add(new ValidationIssue("document", "문서 입력 항목",
                        "지원하지 않는 입력 항목이 있습니다: " + safeKey(key), safe(key)));
                    continue;
                }
                if (!seen.add(key)) {
                    invalid.add(key);
                    issues.add(new ValidationIssue(key, field.label(),
                        "같은 항목이 문서에 두 번 있습니다.", safe(key)));
                    continue;
                }
                String raw = table.getRow(2).getCell(0).getText().trim();
                String separateNote = table.getRows().size() >= 5
                    ? table.getRow(4).getCell(0).getText().trim() : "";
                ParsedCell parsed = splitCell(field, raw, separateNote);
                if (parsed.note().length() > 2_000) {
                    invalid.add(key);
                    issues.add(new ValidationIssue(key, field.label(),
                        "산정 근거는 2,000자 이내로 작성해 주세요.", safe(parsed.note())));
                    continue;
                }
                if (parsed.value().isBlank() || emptyOptional(parsed.value())) {
                    if (field.required()) {
                        invalid.add(key);
                        issues.add(new ValidationIssue(key, field.label(),
                            "필수 값을 입력해 주세요.", safe(parsed.value())));
                    }
                    if (!parsed.note().isBlank()) notes.put(key, parsed.note());
                    continue;
                }
                try {
                    put(values, field, parsed.value());
                    if (!parsed.note().isBlank()) notes.put(key, parsed.note());
                }
                catch (FieldFormatException exception) {
                    invalid.add(key);
                    issues.add(new ValidationIssue(key, field.label(), exception.getMessage(), safe(parsed.value())));
                }
            }
        } catch (IOException exception) {
            throw new FinancialInputDocumentException(List.of(
                new ValidationIssue("file", "재무 입력 문서", "DOCX 문서 형식을 읽을 수 없습니다.", "")));
        }
        for (InputField field : fields.values()) {
            if (field.required() && !values.has(field.key()) && !invalid.contains(field.key())) {
                issues.add(new ValidationIssue(field.key(), field.label(), "필수 값을 입력해 주세요.", ""));
            }
        }
        if (!issues.isEmpty()) throw new FinancialInputDocumentException(issues);
        if (!notes.isEmpty()) values.set(INPUT_NOTES, notes);
        return values;
    }

    private ParsedCell splitCell(InputField field, String raw, String separateNote) {
        String value = Optional.ofNullable(raw).orElse("").strip();
        String note = Optional.ofNullable(separateNote).orElse("").strip();
        Matcher matcher = TRAILING_NOTE.matcher(value);
        if (!matcher.matches()) return new ParsedCell(value, note);
        String primary = matcher.group(1).strip();
        String parenthetical = matcher.group(2).strip();
        if ("revenueModel".equals(field.key()) && Set.of("one_time", "subscription", "hybrid")
                .contains(parenthetical.toLowerCase(Locale.ROOT))) {
            return new ParsedCell(value, note);
        }
        String combinedNote = note.isBlank() ? parenthetical : parenthetical + "\n" + note;
        return new ParsedCell(primary, combinedNote);
    }

    private void put(ObjectNode values, InputField field, String raw) {
        String key = field.key();
        if ("revenueModel".equals(key)) values.put(key, revenueModel(raw));
        else if ("newCustomerCount".equals(key)) {
            BigDecimal number = number(raw, false, "1 이상의 정수를 입력해 주세요.");
            try {
                long value = number.longValueExact();
                if (value < 1) throw new ArithmeticException();
                values.put(key, value);
            } catch (ArithmeticException exception) { throw format("1 이상의 정수를 입력해 주세요."); }
        } else if ("monthlyChurnRate".equals(key)) {
            BigDecimal percent = number(raw.replaceFirst("\\s*%\\s*$", ""), false,
                "0에서 100 사이의 숫자를 입력해 주세요.");
            if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0)
                throw format("0에서 100 사이의 숫자를 입력해 주세요.");
            values.put(key, percent);
        } else if ("threeYearTargets".equals(key)) {
            Matcher matcher = THREE_TARGETS.matcher(raw.replaceAll("[\\s\\u00a0]", ""));
            if (!matcher.matches()) throw format("1·2·3년차 값을 ‘1000 / 2000 / 4000’ 형식으로 입력해 주세요.");
            ObjectNode target = values.putObject(key); target.put("metric", "customerCount"); target.put("unit", "명");
            var years = target.putArray("years");
            for (int index = 1; index <= 3; index++) {
                BigDecimal value = number(matcher.group(index), false,
                    "1·2·3년차 값은 0 이상의 숫자여야 합니다.");
                if (value.compareTo(BigDecimal.ZERO) < 0)
                    throw format("1·2·3년차 값은 0 이상의 숫자여야 합니다.");
                years.addObject().put("year", index).put("value", value);
            }
        } else {
            BigDecimal amount = number(raw.replaceFirst("(?i)\\s*(KRW|원)\\s*$", ""), true,
                "0 이상의 금액을 숫자로 입력하거나 선택 항목이면 비워 주세요.");
            if (amount.compareTo(BigDecimal.ZERO) < 0)
                throw format("0 이상의 금액을 입력해 주세요.");
            ObjectNode money = values.putObject(key); money.put("amount", amount); money.put("currency", "KRW");
        }
    }

    private BigDecimal number(String raw, boolean money, String message) {
        String normalized = raw.replaceAll("[\\s\\u00a0]", "");
        String pattern = money ? "[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?"
            : "[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";
        if (!normalized.matches(pattern)) throw format(message);
        try { return new BigDecimal(normalized.replace(",", "")); }
        catch (NumberFormatException exception) { throw format(message); }
    }

    private String revenueModel(String raw) {
        String value = raw.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (value.contains("one_time") || Set.of("일회성", "일회성 판매").contains(value)) return "ONE_TIME";
        if (value.contains("subscription") || Set.of("구독", "구독형", "정기 구독").contains(value)) return "SUBSCRIPTION";
        if (value.contains("hybrid") || Set.of("혼합", "혼합형").contains(value)) return "HYBRID";
        throw format("일회성 판매, 정기 구독, 혼합형 중 하나를 입력해 주세요.");
    }

    private boolean emptyOptional(String raw) {
        return EMPTY_OPTIONAL.contains(raw.strip().toLowerCase(Locale.ROOT));
    }

    private Map<String, InputField> fieldMap() {
        Map<String, InputField> result = new LinkedHashMap<>();
        for (InputField field : fields()) result.put(field.key(), field);
        return result;
    }

    private List<InputField> fields() { return List.of(
        field("1. 연간 고정비", "annualFixedLaborCost", "연간 고정 인건비", "급여·상여·보험 등 연간 인건비 합계", "120,000,000원"),
        field("1. 연간 고정비", "annualFixedRentAndManagementCost", "연간 임차료 및 관리비", "임차료와 관리비의 연간 합계", "36,000,000원"),
        field("1. 연간 고정비", "annualFixedInfrastructureCost", "연간 인프라 운영비", "서버·SaaS·통신 등 연간 비용", "18,000,000원"),
        field("2. 초기 투자비", "initialDevelopmentAndRnDCost", "초기 개발 및 R&D 비용", "출시 전 개발·연구·외주 비용", "80,000,000원"),
        field("2. 초기 투자비", "initialEquipmentAndInfrastructureCost", "초기 장비 및 인프라 비용", "장비 구매·초기 구축·설치 비용", "30,000,000원"),
        field("2. 초기 투자비", "initialPatentAndLicensingCost", "초기 특허 및 라이선스 비용", "특허·라이선스 취득 초기 비용", "5,000,000원"),
        field("3. 고객 확보와 성장", "totalMarketingCost", "연간 마케팅비", "고객 확보를 위한 연간 비용", "24,000,000원"),
        field("3. 고객 확보와 성장", "totalSalesCost", "연간 영업비", "영업 활동의 연간 비용", "12,000,000원"),
        field("3. 고객 확보와 성장", "newCustomerCount", "연간 신규 고객 수", "첫해 신규 고객 수를 정수로 작성", "1,500"),
        field("3. 고객 확보와 성장", "threeYearTargets", "3개년 성장 목표", "1·2·3년차 목표를 슬래시로 구분", "1,000 / 2,000 / 4,000"),
        field("4. 매출 모델", "revenueModel", "매출 모델", "일회성 판매·정기 구독·혼합형 중 선택", "정기 구독 (SUBSCRIPTION)"),
        optional("4. 매출 모델", "unitPrice", "건당 판매 가격", "일회성 판매의 고객 1건당 평균 가격", "50,000원"),
        optional("4. 매출 모델", "monthlySubscriptionPrice", "월 구독 가격", "고객 1인당 월 평균 구독료", "9,900원"),
        optional("4. 매출 모델", "monthlyChurnRate", "월 이탈률", "한 달 동안 이탈할 구독 고객 비율", "3.5%"),
        optional("5. 선택 비용", "unitVariableCost", "건당 변동비", "판매·서비스 1건 증가 시 드는 비용", "12,000원"),
        optional("5. 선택 비용", "paymentFee", "결제 수수료", "결제 1건당 처리 수수료", "1,500원"),
        optional("5. 선택 비용", "partnerPayout", "파트너 지급액", "판매 1건당 파트너 지급 금액", "5,000원"),
        optional("5. 선택 비용", "shippingCost", "배송비", "판매 1건당 평균 배송비", "3,000원"),
        optional("5. 선택 비용", "customerIncrementalInfraCost", "고객 증가 인프라비", "고객 증가에 따른 1인당 추가 비용", "500원"));
    }

    private InputField field(String section, String key, String label, String description, String example) {
        return new InputField(section, key, label, description, example, true);
    }
    private InputField optional(String section, String key, String label, String description, String example) {
        return new InputField(section, key, label, description, example, false);
    }
    private void paragraph(XWPFDocument doc, String text, boolean bold, int size, String color, int before, int after) {
        XWPFParagraph paragraph = doc.createParagraph(); paragraph.setSpacingBefore(before); paragraph.setSpacingAfter(after);
        paragraph.setSpacingBetween(1.12); XWPFRun run = paragraph.createRun(); run.setFontFamily("Malgun Gothic");
        run.setFontSize(size); run.setBold(bold); run.setColor(color); run.setText(text);
    }
    private void style(XWPFTable table) {
        table.setWidth("100%"); table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        for (int row = 0; row < table.getRows().size(); row++) for (XWPFTableCell cell : table.getRow(row).getTableCells()) {
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            cell.setColor(row == 0 ? "F5F7FA" : row == 1 || row == 3 ? "D9E2F3" : "F8FBFF");
            for (XWPFParagraph paragraph : cell.getParagraphs()) for (XWPFRun run : paragraph.getRuns()) {
                run.setFontFamily("Malgun Gothic"); run.setFontSize(10); run.setBold(row == 1 || row == 3);
            }
        }
    }

    private FieldFormatException format(String message) { return new FieldFormatException(message); }
    private String safe(String value) {
        String sanitized = Optional.ofNullable(value).orElse("").replaceAll("[^0-9A-Za-z가-힣.,%/+() _-]", "?")
            .replaceAll("\\s+", " ").strip();
        return sanitized.length() <= 32 ? sanitized : sanitized.substring(0, 32) + "…";
    }
    private String safeKey(String value) {
        String safe = safe(value); return safe.isBlank() ? "알 수 없는 항목" : safe;
    }

    private record InputField(String section, String key, String label, String description,
        String example, boolean required) {}
    private record ParsedCell(String value, String note) {}
    public record ValidationIssue(String field, String label, String message, String rawSafeSummary) {}
    public static final class FinancialInputDocumentException extends IllegalArgumentException {
        private final List<ValidationIssue> issues;
        public FinancialInputDocumentException(List<ValidationIssue> issues) {
            super("재무 입력 문서를 확인해 주세요."); this.issues = List.copyOf(issues);
        }
        public List<ValidationIssue> issues() { return issues; }
    }
    private static final class FieldFormatException extends IllegalArgumentException {
        private FieldFormatException(String message) { super(message); }
    }
}
