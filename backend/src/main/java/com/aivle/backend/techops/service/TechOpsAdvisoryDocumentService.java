package com.aivle.backend.techops.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** User-facing, styled DOCX report. Internal fact IDs and JSON paths are intentionally excluded. */
@Service
@RequiredArgsConstructor
public class TechOpsAdvisoryDocumentService {
    private static final String BLUE = "1F4E79";
    private static final String LIGHT_BLUE = "EAF2F8";
    private static final String LIGHT_GRAY = "F5F7FA";
    private final ProjectRepository projects;

    public byte[] create(Long ownerId, Long projectId, JsonNode result) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        if (result == null || result.isMissingNode() || result.isNull()) throw new IllegalArgumentException("기술·운영 분석 결과가 없습니다.");
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            title(document, "기술·운영 분석 전체 보고서");
            subtitle(document, "프로젝트 " + projectId + " · 생성일 " + LocalDate.now());
            callout(document, "이 문서는 기술 구현 가능성, 운영 준비도, 파일럿 계획과 출시 조건을 한눈에 검토하기 위한 참고 자료입니다.");

            heading(document, "1. 한눈에 보는 결론", 1);
            Map<String, String> overview = new LinkedHashMap<>();
            overview.put("종합 판정", decision(text(result, "decision", "미정")));
            overview.put("분석 대상", text(result, "productName", "기술·운영 적용성"));
            overview.put("핵심 요약", text(result, "summary", "요약 정보 없음"));
            keyValueTable(document, overview);

            heading(document, "2. 시장·사업모델에서 확인한 전제", 1);
            Map<String, String> context = friendlyFacts(result.path("layer1Facts"));
            if (context.isEmpty()) paragraph(document, "시장·사업모델 분석에서 전달된 사용자용 요약 정보가 없습니다.");
            else keyValueTable(document, context);
            note(document, "참고: 시장 규모와 성장률은 시장 분석 단계의 관측값·가정을 요약한 것입니다. 내부 FACT 번호나 시스템 경로는 표시하지 않습니다.");

            heading(document, "3. 우선 실행할 기술·운영 과제", 1);
            note(document, "각 과제는 실행할 일과 검증 설계를 분리해 표시합니다. 설계값으로 제안된 표본·기간·기준은 실행 전에 담당자가 확정해야 합니다.");
            int adviceNumber = 1;
            for (JsonNode item : result.path("advice")) adviceCard(document, item, adviceNumber++);

            heading(document, "4. 파일럿 실행 계획", 1);
            JsonNode pilot = result.path("pilotPlan");
            keyValueTable(document, Map.of("파일럿 목표", text(pilot, "objective", "정보 없음")));
            listSection(document, "파일럿 범위", pilot.path("scope"));
            listSection(document, "측정 지표", pilot.path("metrics"));
            listSection(document, "중단 조건", pilot.path("stopConditions"));
            listSection(document, "확장 조건", pilot.path("scaleConditions"));

            heading(document, "5. 운영비와 확장 준비도", 1);
            for (JsonNode item : result.path("operatingCosts")) costCard(document, item);
            for (JsonNode item : result.path("readiness")) readinessCard(document, item);

            heading(document, "6. 출시 전 확인할 게이트", 1);
            for (JsonNode item : result.path("gates")) gateCard(document, item);

            heading(document, "7. 외부 참고 출처", 1);
            boolean hasSource = false;
            for (JsonNode source : result.path("layer2Evidence")) {
                hasSource = true;
                paragraph(document, text(source, "title", "참고 출처"), true, 11, BLUE);
                paragraph(document, text(source, "url", "URL 정보 없음"));
            }
            if (!hasSource) paragraph(document, "이번 분석 결과에는 사용자에게 표시할 외부 URL 출처가 포함되지 않았습니다.");
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("기술·운영 보고서를 만들 수 없습니다.", exception); }
    }

    private Map<String, String> friendlyFacts(JsonNode facts) {
        Map<String, String> values = new LinkedHashMap<>();
        for (JsonNode fact : facts) {
            String path = text(fact, "path", ""); String value = text(fact, "value", "");
            if (path.equals("MARKET.market.tam.value")) values.put("추정 시장 규모(TAM)", formatNumber(value) + "원");
            else if (path.equals("MARKET.market.tam.factors[0].value")) values.put("전국 대상 사업체 수", formatNumber(value) + "개");
            else if (path.equals("MARKET.scorecard[1].detail")) values.put("시장 성장 관찰", value);
            else if (path.contains("BM.canvas.cells[0].content")) values.put("핵심 고객", value);
            else if (path.contains("BM.canvas.cells[1].content")) values.put("해결하려는 문제와 가치", value);
            else if (path.contains("BM.canvas.cells[2].content")) values.merge("주요 고객 접점", value, (oldValue, newValue) -> oldValue + "\n" + newValue);
            else if (path.contains("BM.canvas.cells[3].content")) values.put("고객 관계 방식", value);
            else if (path.contains("BM.canvas.cells[4].content")) values.put("수익 모델", value);
            else if (path.contains("BM.canvas.cells[5].content")) values.merge("핵심 운영 자원", value, (oldValue, newValue) -> oldValue + "\n" + newValue);
            else if (path.contains("BM.canvas.cells[6].content")) values.merge("핵심 운영 활동", value, (oldValue, newValue) -> oldValue + "\n" + newValue);
        }
        return values;
    }

    private void adviceCard(XWPFDocument document, JsonNode item, int number) {
        XWPFParagraph header = document.createParagraph();
        header.setSpacingBefore(number == 1 ? 40 : 160);
        header.setSpacingAfter(90);
        header.setKeepNext(true);
        shade(header, LIGHT_BLUE);
        leftBorder(header, BLUE, 18);
        XWPFRun index = run(header, String.format("%02d  ", number), true, 10, "5B7185");
        index.setCharacterSpacing(20);
        run(header, area(text(item, "area", "기타")), true, 12, BLUE);
        run(header, "   " + priority(text(item, "priority", "MEDIUM")), true, 9, priorityColor(text(item, "priority", "MEDIUM")));

        labelledBlock(document, "실행 권고", text(item, "advice", "정보 없음"), false);
        labelledBlock(document, "검증 설계", text(item, "validationMethod", "정보 없음"), true);
    }
    private void costCard(XWPFDocument document, JsonNode item) { keyValueTable(document, Map.of("운영비 항목", text(item, "category", "정보 없음"), "비용 발생 요인", text(item, "driver", "정보 없음"), "발생 조건", text(item, "trigger", "정보 없음"), "파일럿 측정 방법", text(item, "pilotMeasurement", "정보 없음"))); }
    private void readinessCard(XWPFDocument document, JsonNode item) { keyValueTable(document, Map.of("준비 영역", topic(text(item, "topic", "기타")), "평가", text(item, "assessment", "정보 없음"), "검증 방법", text(item, "validationMethod", "정보 없음"), "권장 통제", join(item.path("controls")))); }
    private void gateCard(XWPFDocument document, JsonNode item) { keyValueTable(document, Map.of("출시 조건", text(item, "title", "정보 없음"), "상태", text(item, "status", "미정"), "담당", text(item, "owner", "미정"), "통과 기준", text(item, "exitCriteria", "정보 없음"))); }

    private void title(XWPFDocument document, String value) { paragraph(document, value, true, 24, BLUE); }
    private void subtitle(XWPFDocument document, String value) { paragraph(document, value, false, 10, "667085"); }
    private void heading(XWPFDocument document, String value, int level) {
        XWPFParagraph paragraph = paragraph(document, value, true, level == 1 ? 16 : 12, BLUE);
        paragraph.setSpacingBefore(level == 1 ? 260 : 150);
        paragraph.setSpacingAfter(level == 1 ? 120 : 80);
        paragraph.setKeepNext(true);
    }
    private void paragraph(XWPFDocument document, String value) { paragraph(document, value, false, 10, "1F2937"); }
    private XWPFParagraph paragraph(XWPFDocument document, String value, boolean bold, int size, String color) { XWPFParagraph paragraph = document.createParagraph(); paragraph.setSpacingAfter(120); paragraph.setSpacingBetween(1.15); run(paragraph, value == null || value.isBlank() ? "정보 없음" : value, bold, size, color); return paragraph; }
    private void callout(XWPFDocument document, String value) { paragraph(document, "핵심 안내  |  " + value, true, 10, BLUE); }
    private void note(XWPFDocument document, String value) { paragraph(document, "참고  |  " + value, false, 9, "667085"); }
    /** Report layout intentionally uses labelled paragraphs, not tables, to prevent page-space waste. */
    private void keyValueTable(XWPFDocument document, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) labelledParagraph(document, entry.getKey(), entry.getValue());
        XWPFParagraph spacer = document.createParagraph(); spacer.setSpacingAfter(100);
    }
    private void labelledParagraph(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph(); paragraph.setSpacingAfter(80); paragraph.setSpacingBetween(1.15);
        run(paragraph, label + "  ", true, 10, BLUE);
        run(paragraph, value == null || value.isBlank() ? "정보 없음" : value, false, 10, "1F2937");
    }
    private void labelledBlock(XWPFDocument document, String label, String value, boolean validation) {
        XWPFParagraph labelParagraph = document.createParagraph();
        labelParagraph.setSpacingBefore(30); labelParagraph.setSpacingAfter(35); labelParagraph.setKeepNext(true);
        run(labelParagraph, label, true, 9, validation ? "276749" : BLUE);
        XWPFParagraph body = paragraph(document, value, false, 10, "1F2937");
        body.setIndentationLeft(240); body.setIndentationRight(120); body.setSpacingAfter(validation ? 130 : 70);
        if (validation) shade(body, "F2F8F4");
    }
    private XWPFRun run(XWPFParagraph paragraph, String value, boolean bold, int size, String color) {
        XWPFRun run = paragraph.createRun(); run.setFontFamily("Malgun Gothic"); run.setFontSize(size); run.setBold(bold); run.setColor(color); run.setText(value); return run;
    }
    private void shade(XWPFParagraph paragraph, String fill) {
        CTPPr properties = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTShd shading = properties.isSetShd() ? properties.getShd() : properties.addNewShd(); shading.setVal(STShd.CLEAR); shading.setFill(fill);
    }
    private void leftBorder(XWPFParagraph paragraph, String color, int size) {
        CTPPr properties = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTBorder border = properties.isSetPBdr() ? properties.getPBdr().isSetLeft() ? properties.getPBdr().getLeft() : properties.getPBdr().addNewLeft() : properties.addNewPBdr().addNewLeft();
        border.setVal(STBorder.SINGLE); border.setColor(color); border.setSz(java.math.BigInteger.valueOf(size)); border.setSpace(java.math.BigInteger.valueOf(8));
    }
    private String priorityColor(String value) { return Map.of("CRITICAL", "B42318", "HIGH", "A15C00", "MEDIUM", "52606D", "LOW", "667085").getOrDefault(value, "52606D"); }
    private void listSection(XWPFDocument document, String label, JsonNode values) { if (!values.isArray() || values.isEmpty()) return; heading(document, label, 2); for (JsonNode value : values) paragraph(document, value.asText()); }
    private String text(JsonNode node, String key, String fallback) { String value = node.path(key).asText(); return value == null || value.isBlank() ? fallback : value; }
    private String join(JsonNode values) { if (!values.isArray() || values.isEmpty()) return "정보 없음"; StringBuilder out = new StringBuilder(); for (JsonNode value : values) { if (!out.isEmpty()) out.append("\n"); out.append(value.asText()); } return out.toString(); }
    private String formatNumber(String value) { try { return String.format("%,.0f", new BigDecimal(value)); } catch (NumberFormatException ignored) { return value; } }
    private String decision(String value) { return Map.of("GO", "진행 가능", "CONDITIONAL_GO", "조건부 진행", "REVISE", "보완 후 진행", "NO_GO", "진행 보류").getOrDefault(value, value); }
    private String priority(String value) { return Map.of("CRITICAL", "즉시 확인", "HIGH", "우선 확인", "MEDIUM", "확인 필요", "LOW", "참고").getOrDefault(value, value); }
    private String area(String value) { return Map.of("MARKET_BM", "시장·사업모델", "PRODUCT_TECH", "제품·기술 구현", "OPERATIONS", "운영 구조", "RISK_GATE", "출시 위험", "PARTNER_SUPPLY", "파트너·공급", "PILOT", "파일럿", "SCALE", "확장 준비").getOrDefault(value, value); }
    private String topic(String value) { return Map.of("DATA_AI", "데이터·AI 운영", "CUSTOMER_TRUST", "고객 신뢰", "OBSERVABILITY_SLA", "관측성·서비스 수준", "SCALABILITY", "확장 준비도").getOrDefault(value, value); }
}
