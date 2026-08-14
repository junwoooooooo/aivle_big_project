package com.aivle.backend.launchreadiness.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport.ModuleType;
import com.aivle.backend.launchreadiness.repository.ProfessionalAnalysisReportRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Anchor;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ProfessionalLaunchReadinessService {
    private static final String NAVY = "1F4E79";
    private static final String INK = "1F2937";
    private static final String MUTED = "52657D";
    private static final String BLUE = "D9E2F3";
    private static final String BLUE_LIGHT = "EAF2F8";
    private static final String INPUT = "F8FBFF";
    private static final String BORDER = "C8D4E3";
    private final ProjectRepository projects;
    private final ProfessionalAnalysisReportRepository reports;
    private final ObjectMapper mapper;
    private final ProfessionalAnalysisAiClient ai;

    public byte[] template(ModuleType type) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            paragraph(document, label(type) + " 전문 입력 템플릿", true, 22, "1F4E79");
            paragraph(document, "각 항목의 값 칸을 기업의 실제 계획과 운영 기준으로 작성하세요. 이 문서의 입력값만 분석에 사용됩니다.", false, 10, "52657D");
            for (Field field : fields(type)) {
                paragraph(document, field.label(), true, 12, "1F4E79");
                paragraph(document, "무엇을 적나요: " + field.guide(), false, 10, "1F2937");
                paragraph(document, "작성 방법: 현재 상태와 목표, 담당자·기한·수치가 있으면 함께 적어 주세요.", false, 9, "52657D");
                XWPFTable table = document.createTable(3, 1);
                table.getRow(0).getCell(0).setText("fieldKey: " + field.key());
                table.getRow(1).getCell(0).setText("입력 내용 (아래 칸에 직접 작성)");
                table.getRow(2).getCell(0).setText("\n\n\n");
                styleInputTable(table);
            }
            document.write(output); return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("입력 템플릿을 만들 수 없습니다.", exception); }
    }

    public AnalysisView analyze(Long ownerId, Long projectId, ModuleType type, MultipartFile file) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId).orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        Map<String, String> input = parse(type, file);
        List<String> missing = fields(type).stream().filter(field -> input.getOrDefault(field.key(), "").isBlank()).map(Field::label).toList();
        List<String> completed = fields(type).stream().filter(field -> !input.getOrDefault(field.key(), "").isBlank()).map(Field::label).toList();
        JsonNode aiResult = ai.analyze(type, input);
        if (aiResult == null || !aiResult.isObject()) {
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE,
                "전문 분석 AI가 올바른 결과를 반환하지 않았습니다.");
        }
        ObjectNode analysis = (ObjectNode) aiResult.deepCopy();
        analysis.put("moduleType", type.name());
        analysis.set("missing", mapper.valueToTree(missing));
        analysis.set("completed", mapper.valueToTree(completed));
        analysis.put("inputCompleteness", fields(type).isEmpty() ? 0
            : Math.round((completed.size() * 100.0f) / fields(type).size()));
        ProfessionalAnalysisReport saved = ProfessionalAnalysisReport.create(UUID.randomUUID().toString(), projectId, type,
            mapper.writeValueAsString(input), mapper.writeValueAsString(analysis), ownerId, Instant.now());
        reports.save(saved);
        return view(saved);
    }

    public AnalysisView current(Long ownerId, Long projectId, ModuleType type) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId).orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        return reports.findFirstByProjectIdAndModuleTypeAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, type).map(this::view).orElse(null);
    }

    public byte[] pdf(Long ownerId, Long projectId, ModuleType type) {
        AnalysisView result = current(ownerId, projectId, type);
        if (result == null) throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY, "분석을 먼저 실행해 주세요.");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 42, 42); PdfWriter.getInstance(document, output); document.open();
            Font title = koreanFont(22, Font.BOLD, "1F4E79"); Font heading = koreanFont(14, Font.BOLD, "1F4E79"); Font body = koreanFont(9, Font.NORMAL, "1F2937"); Font small = koreanFont(8, Font.NORMAL, "52657D");
            document.add(new Paragraph(label(type) + " 분석 보고서", title));
            document.add(new Paragraph("전문 입력 문서 기반 · 프로젝트 " + projectId + " · " + result.completedAt(), small));
            document.add(space());
            PdfPTable metrics = new PdfPTable(new float[] {1, 1, 1}); metrics.setWidthPercentage(100);
            metric(metrics, "종합 준비도", result.score() + "점", body, small);
            metric(metrics, "판정", decisionLabel(result.decision()), body, small);
            metric(metrics, "검증", result.qualityPassed() ? "독립 검증 통과" : "검토 필요", body, small);
            document.add(metrics);
            addSection(document, "1. 경영진 요약", heading); document.add(callout(result.summary(), body));
            addSection(document, "2. 평가에 사용한 입력 근거", heading);
            document.add(new Paragraph("아래 내용은 사용자가 전문 입력 문서에 작성한 값입니다. 각 평가는 이 입력값과 외부 참고 근거를 바탕으로 판단했습니다.", small));
            PdfPTable inputs = new PdfPTable(new float[] {2, 5.8f}); inputs.setWidthPercentage(100);
            headers(inputs, List.of("입력 항목", "사용자 입력 내용"), small);
            for (Field field : fields(type)) {
                String value = result.inputs().getOrDefault(field.key(), "").trim();
                if (!value.isBlank()) { inputs.addCell(cell(field.label(), body)); inputs.addCell(cell(value, body)); }
            }
            document.add(inputs);
            addSection(document, "3. 영역별 준비도와 판단 근거", heading);
            PdfPTable dimensions = new PdfPTable(new float[] {2.1f, 1, 1.2f, 5}); dimensions.setWidthPercentage(100);
            headers(dimensions, List.of("평가 영역", "점수", "상태", "판단 근거"), small);
            for (Map<String, Object> row : result.dimensions()) {
                dimensions.addCell(cell(text(row, "name"), body)); dimensions.addCell(cell(text(row, "score"), body));
                dimensions.addCell(cell(statusLabel(text(row, "status")), body)); dimensions.addCell(cell("평가: " + text(row, "finding") + "\n판정: " + statusLabel(text(row, "status")) + " · " + text(row, "score") + "점", body));
            }
            document.add(dimensions);
            addSection(document, "4. 핵심 위험", heading);
            PdfPTable risks = new PdfPTable(new float[] {2, 1, 2.4f, 3.6f}); risks.setWidthPercentage(100);
            headers(risks, List.of("위험", "등급", "사업 영향", "대응책"), small);
            for (Map<String, Object> row : result.risks()) {
                risks.addCell(cell(text(row, "title"), body)); risks.addCell(cell(statusLabel(text(row, "severity")), body));
                risks.addCell(cell(text(row, "impact"), body)); risks.addCell(cell(text(row, "mitigation"), body));
            }
            document.add(risks);
            addSection(document, "5. 우선 실행 과제", heading);
            for (Map<String, Object> action : result.actions()) {
                document.add(new Paragraph("[" + text(action, "priority") + "] " + text(action, "title") + " · 담당: " + text(action, "owner"), body));
                document.add(new Paragraph("완료 증빙: " + text(action, "completionEvidence"), small));
            }
            addSection(document, "6. 사업 적용 결론과 권장 모듈", heading);
            document.add(callout(result.businessConclusion(type), body));
            document.add(new Paragraph("우선 도입·보완할 모듈", body));
            for (Map<String, Object> action : result.actions()) {
                document.add(new Paragraph("• " + text(action, "title") + " — " + text(action, "completionEvidence"), small));
            }
            addSection(document, "7. 외부 참고 출처", heading);
            if (result.externalEvidence().isEmpty()) document.add(new Paragraph("외부 검색 근거 없음 · 사용자 전문입력만으로 분석", small));
            for (Map<String, Object> source : result.externalEvidence()) {
                Anchor link = new Anchor(text(source, "title"), body); link.setReference(text(source, "url"));
                document.add(new Paragraph(link)); document.add(new Paragraph(text(source, "url"), small));
            }
            document.add(space()); document.add(new Paragraph("본 보고서는 입력 자료와 공개 참고자료를 바탕으로 한 의사결정 지원 문서이며, 법률·보안 인증 또는 성과를 보장하지 않습니다.", small));
            document.close(); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("분석 PDF를 만들 수 없습니다.", exception); }
    }

    private Map<String, String> parse(ModuleType type, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("작성한 DOCX 파일을 업로드해 주세요.");
        Map<String, String> values = new LinkedHashMap<>(); fields(type).forEach(field -> values.put(field.key(), ""));
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (XWPFTable table : document.getTables()) {
                if (table.getRows().size() >= 3 && table.getRow(0).getTableCells().size() == 1) {
                    String key = table.getRow(0).getCell(0).getText().trim().replaceFirst("^fieldKey:\\s*", "");
                    if (values.containsKey(key)) values.put(key, table.getRow(2).getCell(0).getText().trim());
                    continue;
                }
                for (var row : table.getRows()) {
                if (row.getTableCells().size() < 2) continue;
                String key = row.getCell(0).getText().trim();
                if (values.containsKey(key)) values.put(key, row.getCell(row.getTableCells().size() >= 3 ? 2 : 1).getText().trim());
                }
            }
            return values;
        } catch (IOException exception) { throw new IllegalArgumentException("DOCX 템플릿 형식을 읽을 수 없습니다.", exception); }
    }
    private AnalysisView view(ProfessionalAnalysisReport value) { return new AnalysisView(value.getModuleType().name(),
        mapper.readValue(value.getInputJson(), Map.class), mapper.readValue(value.getAnalysisJson(), Map.class), value.getCompletedAt().toString()); }
    @SuppressWarnings("unchecked") public record AnalysisView(String moduleType, Map<String, String> inputs, Map<String, Object> analysis, String completedAt) {
        int score() { return ((Number) analysis.getOrDefault("score", 0)).intValue(); }
        String summary() { return String.valueOf(analysis.getOrDefault("summary", "")); }
        String decision() { return String.valueOf(analysis.getOrDefault("decision", "REVISE")); }
        List<String> missing() { return (List<String>) analysis.getOrDefault("missing", List.of()); }
        List<String> completed() { return (List<String>) analysis.getOrDefault("completed", List.of()); }
        List<Map<String, Object>> dimensions() { return (List<Map<String, Object>>) analysis.getOrDefault("dimensions", List.of()); }
        List<Map<String, Object>> risks() { return (List<Map<String, Object>>) analysis.getOrDefault("risks", List.of()); }
        List<Map<String, Object>> gates() { return (List<Map<String, Object>>) analysis.getOrDefault("gates", List.of()); }
        List<Map<String, Object>> actions() { return (List<Map<String, Object>>) analysis.getOrDefault("actions", List.of()); }
        List<Map<String, Object>> externalEvidence() { return (List<Map<String, Object>>) analysis.getOrDefault("externalEvidence", List.of()); }
        String businessConclusion(ModuleType type) {
            String module = type == ModuleType.TECHNOLOGY ? "기술" : "운영";
            String priority = actions().stream().limit(2).map(action -> value(action, "title")).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining("와 "));
            String risk = risks().stream().findFirst().map(item -> value(item, "title")).filter(value -> !value.isBlank()).orElse("핵심 위험");
            String actionText = priority.isBlank() ? "핵심 실행 과제" : priority;
            String decisionText = switch (decision()) { case "READY" -> "출시 준비"; case "CONDITIONAL" -> "조건부 준비"; default -> "보완 후 재검토"; };
            return "결론: 현재 " + module + " 준비도는 '" + decisionText + "'입니다. " + summary()
                + " 입력된 " + module + " 요소를 계획대로 도입·검증하면 사업 실행의 재현성과 확장 가능성을 높일 수 있습니다. "
                + "다만 " + risk + "을 먼저 관리하고, " + actionText + "을 우선 완료해야 이 결론을 실행 단계로 연결할 수 있습니다.";
        }
        private static String value(Map<String, Object> row, String key) { return String.valueOf(row.getOrDefault(key, "")); }
        boolean qualityPassed() { Object value = analysis.get("quality"); return value instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("passed")); }
    }
    private record Field(String key, String label, String guide) { }
    private List<Field> fields(ModuleType type) { return type == ModuleType.TECHNOLOGY ? List.of(
        new Field("systemArchitecture", "시스템·제품 구조", "구성도, 주요 구성 요소와 연결 관계"), new Field("coreFunctions", "핵심 기능과 구현 상태", "기능별 현재 상태와 출시 기준"), new Field("techStack", "기술 스택·인프라", "언어, 프레임워크, 클라우드, 데이터베이스"), new Field("integrations", "외부 연동·의존성", "API, 결제, 인증, 장애 시 대안"), new Field("dataSecurity", "데이터·보안 기준", "개인정보, 권한, 백업, 보안 요구사항"), new Field("performanceTarget", "성능·확장 목표", "사용자 수, 응답 시간, 처리량"), new Field("developmentTeam", "개발 인력·역할", "담당자, 외주 여부, 책임 범위"), new Field("releaseSchedule", "개발·출시 일정", "마일스톤과 완료 기준"), new Field("testPlan", "테스트·검증 계획", "테스트 범위, 방법, 통과 기준"), new Field("technicalRisks", "기술 위험과 대응", "위험, 영향, 대응책")) : List.of(
        new Field("operatingProcess", "운영 프로세스", "주문·서비스 제공·정산 등 단계별 흐름"), new Field("staffing", "인력·역할 체계", "담당자, 근무 체계, 승인 책임"), new Field("supplyPartners", "공급·파트너 운영", "공급처, 파트너, 계약·정산 방식"), new Field("customerSupport", "고객 지원 체계", "채널, 응답 시간, 문의 처리 기준"), new Field("qualityStandards", "품질·SLA 기준", "품질 기준, 서비스 수준, 점검 주기"), new Field("incidentResponse", "장애·민원 대응", "발생 시 담당자와 복구·공지 절차"), new Field("operatingKpis", "운영 KPI", "처리 시간, 오류율, 만족도 등"), new Field("pilotPlan", "파일럿 계획", "대상, 기간, 성공·중단 기준"), new Field("scalabilityPlan", "확장 계획", "물량 증가 시 인력·시스템·파트너 대응"), new Field("operationalRisks", "운영 위험과 대응", "위험, 영향, 대응책")); }
    private String label(ModuleType type) { return type == ModuleType.TECHNOLOGY ? "기술" : "운영"; }
    private void paragraph(XWPFDocument document, String text, boolean bold, int size, String color) { var paragraph = document.createParagraph(); paragraph.setSpacingAfter(55); paragraph.setSpacingBetween(1.15); var run = paragraph.createRun(); run.setFontFamily("Malgun Gothic"); run.setFontSize(size); run.setBold(bold); run.setColor(color); run.setText(text); }
    private void styleInputTable(XWPFTable table) {
        table.setWidth("100%"); table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, BORDER);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER); table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER); table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, BORDER);
        table.getRow(0).setHeightRule(TableRowHeightRule.AUTO);
        for (int row = 0; row < table.getRows().size(); row++) for (XWPFTableCell cell : table.getRow(row).getTableCells()) {
            cell.setVerticalAlignment(XWPFVertAlign.CENTER); cell.setColor(row == 0 ? BLUE_LIGHT : row == 1 ? BLUE : INPUT);
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                paragraph.setSpacingBefore(70); paragraph.setSpacingAfter(row == 2 ? 400 : 70); paragraph.setSpacingBetween(1.15);
                for (var run : paragraph.getRuns()) { run.setFontFamily("Malgun Gothic"); run.setFontSize(row == 1 ? 10 : 10); run.setBold(row == 1); run.setColor(row == 1 ? NAVY : INK); }
            }
        }
    }
    private Font koreanFont(int size, int style, String color) { for (String path : List.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", "C:/Windows/Fonts/malgun.ttf")) { try { return new Font(BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED), size, style, java.awt.Color.decode("#" + color)); } catch (Exception ignored) { } } return new Font(Font.HELVETICA, size, style, java.awt.Color.decode("#" + color)); }
    private PdfPCell cell(String value, Font font) { var cell = new PdfPCell(new Phrase(value == null ? "-" : value, font)); cell.setPadding(10); cell.setLeading(15, 0); cell.setBorderColor(new java.awt.Color(210, 222, 232)); return cell; }
    private PdfPTable callout(String value, Font font) { PdfPTable table = new PdfPTable(1); table.setWidthPercentage(100); PdfPCell cell = cell(value, font); cell.setBackgroundColor(new java.awt.Color(238, 244, 249)); cell.setBorderColor(new java.awt.Color(31, 78, 121)); cell.setPadding(10); table.addCell(cell); return table; }
    private void metric(PdfPTable table, String label, String value, Font body, Font small) { PdfPCell cell = new PdfPCell(); cell.setPadding(9); cell.setBorderColor(java.awt.Color.WHITE); cell.setBackgroundColor(new java.awt.Color(238, 244, 249)); cell.addElement(new Paragraph(label, small)); cell.addElement(new Paragraph(value, body)); table.addCell(cell); }
    private void headers(PdfPTable table, List<String> values, Font font) { Font white = new Font(font.getBaseFont(), font.getSize(), font.getStyle(), java.awt.Color.WHITE); for (String value : values) { PdfPCell cell = cell(value, white); cell.setBackgroundColor(new java.awt.Color(31, 78, 121)); cell.setHorizontalAlignment(Element.ALIGN_CENTER); table.addCell(cell); } }
    private void addSection(Document document, String value, Font heading) throws Exception { document.add(space()); Paragraph paragraph = new Paragraph(value, heading); paragraph.setSpacingAfter(10); document.add(paragraph); }
    private Paragraph space() { Paragraph value = new Paragraph(" "); value.setLeading(14); return value; }
    private String text(Map<String, Object> value, String key) { return String.valueOf(value.getOrDefault(key, "-")); }
    private String decisionLabel(String value) { return switch (value) { case "READY" -> "출시 준비"; case "CONDITIONAL" -> "조건부 준비"; default -> "보완 후 재검토"; }; }
    private String statusLabel(String value) { return switch (value) { case "READY", "PASS" -> "준비"; case "CAUTION", "OPEN", "MEDIUM" -> "주의"; case "RISK", "BLOCKED", "CRITICAL", "HIGH" -> "위험"; case "LOW" -> "낮음"; default -> value; }; }
}
