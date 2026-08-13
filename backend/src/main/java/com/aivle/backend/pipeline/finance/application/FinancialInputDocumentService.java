package com.aivle.backend.pipeline.finance.application;

import java.io.*;
import java.util.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** DOCX is the user-facing import contract; only the input table is parsed. */
@Service
public class FinancialInputDocumentService {
    private final ObjectMapper mapper;
    private final MarketResearchVersionRepository marketVersions;
    public FinancialInputDocumentService(ObjectMapper mapper, MarketResearchVersionRepository marketVersions) { this.mapper = mapper; this.marketVersions = marketVersions; }

    public byte[] template(Long projectId) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("재무 분석 입력 템플릿");
            doc.createParagraph().createRun().setText("아래 입력값 표의 값 열만 수정하세요. 금액은 원 단위 숫자로 입력하며, 시장 참고값은 재무 계산에 사용되지 않습니다.");
            var market = marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, MarketResearchRun.Kind.FULL)
                .map(v -> mapper.readTree(v.getResultJson()).path("market")).orElse(mapper.createObjectNode());
            XWPFTable ref = doc.createTable(4, 2);
            String[][] refs={{"시장 참고값 (계산 미사용)","최신 시장분석 값"},{"TAM",display(market.path("tam"))},{"SAM",display(market.path("sam"))},{"시장 성장률",display(market.path("growth"))}};
            for(int r=0;r<refs.length;r++) for(int c=0;c<2;c++) ref.getRow(r).getCell(c).setText(refs[r][c]);
            doc.createParagraph().createRun().setText("재무 입력값 (필수 항목은 반드시 작성)");
            List<String[]> rows=List.of(
                new String[]{"fieldKey","값","작성 안내"},
                new String[]{"annualFixedLaborCost","","연간 고정 인건비 (원)"}, new String[]{"annualFixedRentAndManagementCost","","연간 임차·관리비 (원)"},
                new String[]{"annualFixedInfrastructureCost","","연간 인프라비 (원)"}, new String[]{"initialDevelopmentAndRnDCost","","초기 개발·R&D (원)"},
                new String[]{"initialEquipmentAndInfrastructureCost","","초기 설비·인프라 (원)"}, new String[]{"initialPatentAndLicensingCost","","초기 특허·라이선스 (원)"},
                new String[]{"totalMarketingCost","","연간 총 마케팅비 (원)"}, new String[]{"totalSalesCost","","연간 총 영업비 (원)"},
                new String[]{"newCustomerCount","","연간 신규 고객 수"}, new String[]{"revenueModel","ONE_TIME","ONE_TIME / SUBSCRIPTION / HYBRID"},
                new String[]{"unitPrice","","일회성 판매 단가 (원)"}, new String[]{"monthlySubscriptionPrice","","월 구독가격 (원)"}, new String[]{"monthlyChurnRate","","월 이탈률 (%)"},
                new String[]{"threeYearTargets","","1년차,2년차,3년차 목표를 쉼표로 입력 (예: 100,200,400)"});
            XWPFTable table=doc.createTable(rows.size(),3); for(int r=0;r<rows.size();r++) for(int c=0;c<3;c++) table.getRow(r).getCell(c).setText(rows.get(r)[c]);
            doc.write(out); return out.toByteArray();
        } catch(IOException e) { throw new IllegalStateException("재무 입력 템플릿을 만들 수 없습니다.",e); }
    }

    private String display(tools.jackson.databind.JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "시장분석 결과 없음";
        if (value.path("value").isNumber()) {
            double number = value.path("value").asDouble();
            String unit = value.path("unit").asText("");
            String formatted = Math.rint(number) == number
                ? String.format("%,.0f", number) : String.format("%,.2f", number);
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
        ObjectNode values=mapper.createObjectNode();
        try (XWPFDocument doc=new XWPFDocument(file.getInputStream())) {
            for(XWPFTable table:doc.getTables()) for(XWPFTableRow row:table.getRows()) {
                if(row.getTableCells().size()<2) continue; String key=row.getCell(0).getText().trim(), raw=row.getCell(1).getText().trim();
                if(!FinancialPreparationFactory.ALL_KEYS.contains(key)||raw.isBlank()) continue;
                if("revenueModel".equals(key)) values.put(key,raw.toUpperCase());
                else if("newCustomerCount".equals(key)) values.put(key,Long.parseLong(raw.replace(",","")));
                else if("monthlyChurnRate".equals(key)) values.put(key,Double.parseDouble(raw.replace("%", "").replace(",","")));
                else if("threeYearTargets".equals(key)) { String[] p=raw.split(","); if(p.length!=3) throw new IllegalArgumentException("threeYearTargets는 세 숫자를 쉼표로 구분해야 합니다.");
                    ObjectNode target=values.putObject(key); target.put("metric","customerCount"); target.put("unit","명"); var years=target.putArray("years"); for(int i=0;i<3;i++) years.addObject().put("year",i+1).put("value",Double.parseDouble(p[i].trim().replace(",",""))); }
                else { ObjectNode money=values.putObject(key); money.put("amount",Double.parseDouble(raw.replaceAll("[^0-9.]",""))); money.put("currency","KRW"); }
            }
        } catch(IOException|NumberFormatException e) { throw new IllegalArgumentException("템플릿의 값 형식을 확인해 주세요.",e); }
        return values;
    }
}
