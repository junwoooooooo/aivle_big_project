package com.aivle.backend.pipeline.finance.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class FinancialInputDocumentService {
    private final ObjectMapper mapper;
    public FinancialInputDocumentService(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] template(Long projectId) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            paragraph(doc, "재무 분석 입력 템플릿", true, 22, "1F4E79");
            paragraph(doc, "필수값을 빠짐없이 입력하면 손익·현금흐름·핵심 지표를 분석할 수 있습니다. 금액은 원(KRW) 단위입니다.", false, 10, "52657D");
            for (InputField field : fields()) {
                paragraph(doc, field.label(), true, 12, "1F4E79");
                paragraph(doc, field.description() + " · 예: " + field.example(), false, 9, "52657D");
                XWPFTable table = doc.createTable(3, 1);
                table.getRow(0).getCell(0).setText("fieldKey: " + field.key());
                table.getRow(1).getCell(0).setText("입력값 (아래 칸에 직접 작성)");
                table.getRow(2).getCell(0).setText("\n\n\n");
                table.setWidth("100%");
                for (int row = 0; row < 3; row++) table.getRow(row).getCell(0).setColor(row == 2 ? "F8FBFF" : row == 1 ? "D9E2F3" : "F5F7FA");
            }
            doc.write(out); return out.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("재무 입력 템플릿을 만들 수 없습니다.", exception); }
    }

    public ObjectNode parse(MultipartFile file) {
        if (file == null || file.isEmpty() || !Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase().endsWith(".docx"))
            throw new IllegalArgumentException("DOCX 파일만 업로드할 수 있습니다.");
        ObjectNode values = mapper.createObjectNode();
        Set<String> seen = new HashSet<>();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : doc.getTables()) {
                if (table.getRows().size() < 3 || table.getRow(0).getTableCells().size() != 1) continue;
                String marker = table.getRow(0).getCell(0).getText().trim();
                if (!marker.startsWith("fieldKey:")) continue;
                String key = marker.replaceFirst("^fieldKey:\\s*", "");
                if (!FinancialPreparationFactory.ALL_KEYS.contains(key))
                    throw new IllegalArgumentException("지원하지 않는 재무 입력 항목입니다: " + key);
                if (!seen.add(key)) throw new IllegalArgumentException("재무 입력 항목이 중복되어 있습니다: " + key);
                String raw = table.getRow(2).getCell(0).getText().trim();
                if (!raw.isBlank()) put(values, key, raw);
            }
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalArgumentException("문서의 숫자와 입력 형식을 확인해 주세요.", exception);
        }
        List<String> missing = FinancialPreparationFactory.REQUIRED_KEYS.stream().filter(key -> !values.has(key)).toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("문서 입력 내용을 확인해 주세요. 필수 항목: " + String.join(", ", missing));
        return values;
    }
    private void put(ObjectNode values, String key, String raw) {
        if ("revenueModel".equals(key)) {
            String model = raw.strip().toUpperCase();
            if (!Set.of("ONE_TIME", "SUBSCRIPTION", "HYBRID").contains(model))
                throw new IllegalArgumentException("매출 모델은 ONE_TIME, SUBSCRIPTION, HYBRID 중 하나여야 합니다.");
            values.put(key, model);
        } else if ("newCustomerCount".equals(key)) {
            long count = Long.parseLong(raw.replace(",", ""));
            if (count <= 0) throw new IllegalArgumentException("연간 신규 고객 수는 1명 이상이어야 합니다.");
            values.put(key, count);
        } else if ("monthlyChurnRate".equals(key)) {
            double percent = Double.parseDouble(raw.replace("%", "").replace(",", ""));
            if (!Double.isFinite(percent) || percent < 0 || percent > 100)
                throw new IllegalArgumentException("월 이탈률은 0에서 100 사이여야 합니다.");
            values.put(key, percent);
        }
        else if ("threeYearTargets".equals(key)) {
            String[] parts = raw.split(",");
            if (parts.length != 3) throw new IllegalArgumentException("3개년 성장 목표는 숫자 3개를 쉼표로 구분해 주세요.");
            ObjectNode target = values.putObject(key); target.put("metric", "customerCount"); target.put("unit", "명");
            var years = target.putArray("years");
            for (int index = 0; index < 3; index++) {
                double value = Double.parseDouble(parts[index].trim().replace(",", ""));
                if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("3개년 성장 목표는 0 이상이어야 합니다.");
                years.addObject().put("year", index + 1).put("value", value);
            }
        } else {
            String normalized = raw.replaceAll("[^0-9.-]", "");
            double amount = Double.parseDouble(normalized); if (amount < 0) throw new IllegalArgumentException(key + " 값은 0 이상이어야 합니다.");
            ObjectNode money = values.putObject(key); money.put("amount", amount); money.put("currency", "KRW");
        }
    }
    private void paragraph(XWPFDocument doc, String text, boolean bold, int size, String color) { XWPFParagraph p = doc.createParagraph(); XWPFRun r = p.createRun(); r.setFontFamily("Malgun Gothic"); r.setFontSize(size); r.setBold(bold); r.setColor(color); r.setText(text); }
    private List<InputField> fields() { return List.of(
        new InputField("annualFixedLaborCost", "연간 고정 인건비", "연간 반복 인건비 합계", "120000000"),
        new InputField("annualFixedRentAndManagementCost", "연간 임차료 및 관리비", "연간 임차·관리비", "36000000"),
        new InputField("annualFixedInfrastructureCost", "연간 인프라 운영비", "서버·SaaS 등 연간 비용", "18000000"),
        new InputField("initialDevelopmentAndRnDCost", "초기 개발 및 R&D 비용", "출시 전 개발 비용", "80000000"),
        new InputField("initialEquipmentAndInfrastructureCost", "초기 장비 및 인프라 비용", "초기 구축 비용", "30000000"),
        new InputField("initialPatentAndLicensingCost", "초기 특허 및 라이선스 비용", "특허·라이선스 초기 비용", "5000000"),
        new InputField("threeYearTargets", "3개년 성장 목표", "1~3년 차 목표를 쉼표로 구분", "100,200,400"),
        new InputField("totalMarketingCost", "연간 마케팅비", "연간 고객 확보 비용", "24000000"),
        new InputField("totalSalesCost", "연간 영업비", "연간 영업 활동 비용", "12000000"),
        new InputField("newCustomerCount", "연간 신규 고객 수", "첫해 신규 고객 정수", "1500"),
        new InputField("revenueModel", "매출 모델", "ONE_TIME, SUBSCRIPTION, HYBRID", "SUBSCRIPTION"),
        new InputField("unitPrice", "건당 판매 가격", "일회성 평균 판매가", "50000"),
        new InputField("monthlySubscriptionPrice", "월 구독 가격", "월 평균 구독료", "9900"),
        new InputField("monthlyChurnRate", "월 이탈률", "0~100 사이 비율", "3.5"),
        new InputField("unitVariableCost", "건당 변동비", "판매 1건 증가 비용", "12000"),
        new InputField("paymentFee", "결제 수수료", "건당 결제 비용", "1500"),
        new InputField("partnerPayout", "파트너 지급액", "건당 파트너 지급액", "5000"),
        new InputField("shippingCost", "배송비", "건당 배송비", "3000"),
        new InputField("customerIncrementalInfraCost", "고객 증가 인프라비", "고객당 추가 인프라 비용", "500")); }
    private record InputField(String key, String label, String description, String example) {}
}
