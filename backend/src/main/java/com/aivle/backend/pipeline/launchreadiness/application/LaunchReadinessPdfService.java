package com.aivle.backend.pipeline.launchreadiness.application;

import static com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessApiModels.ProfessionalAnalysisView;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessInputSnapshotRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class LaunchReadinessPdfService {
    private static final Color NAVY = new Color(31, 78, 121);
    private static final Color LIGHT = new Color(238, 244, 249);
    private final LaunchReadinessService readiness;
    private final LaunchReadinessInputSnapshotRepository snapshots;
    private final LaunchReadinessDocumentService documents;
    private final ObjectMapper mapper;

    public byte[] create(Long ownerId, Long projectId, ModuleType type, boolean includeSources) {
        ProfessionalAnalysisView view = readiness.current(ownerId, projectId, type);
        if (view.analysis() == null || view.stale()) throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY, "현재 입력으로 완료된 분석이 없습니다.");
        var snapshot = snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type).orElseThrow();
        @SuppressWarnings("unchecked") Map<String, String> inputs = mapper.readValue(snapshot.getParsedInputJson(), Map.class);
        JsonNode result = view.analysis();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 42, 42);
            PdfWriter.getInstance(document, output); document.open();
            Font title = KoreanPdfFonts.font(22, Font.BOLD, NAVY);
            Font heading = KoreanPdfFonts.font(14, Font.BOLD, NAVY);
            Font body = KoreanPdfFonts.font(9, Font.NORMAL, new Color(31, 41, 55));
            Font small = KoreanPdfFonts.font(8, Font.NORMAL, new Color(82, 101, 125));
            document.add(new Paragraph(label(type) + " 분석 보고서", title));
            document.add(new Paragraph("현재 확정 사업안과 제출한 전문 입력 문서 기반 · " + view.completedAt(), small));
            document.add(spacer());
            PdfPTable metrics = new PdfPTable(new float[] {1, 1, 1}); metrics.setWidthPercentage(100);
            metric(metrics, "종합 준비도", result.path("score").asText() + "점", body, small);
            metric(metrics, "판정", decision(result.path("decision").asText()), body, small);
            metric(metrics, "독립 검증", view.quality().path("passed").asBoolean() ? "통과" : "검토 필요", body, small);
            document.add(metrics);
            section(document, "1. 경영진 요약", heading); document.add(callout(result.path("summary").asText(), body));
            section(document, "2. 평가에 사용한 입력 근거", heading);
            PdfPTable inputTable = new PdfPTable(new float[] {2, 5.8f}); inputTable.setWidthPercentage(100);
            headers(inputTable, List.of("입력 항목", "사용자 입력 내용"), small);
            for (var field : documents.fields(type)) {
                String value = inputs.getOrDefault(field.key(), "").trim();
                if (!value.isBlank()) { inputTable.addCell(cell(field.label(), body)); inputTable.addCell(cell(value, body)); }
            }
            document.add(inputTable);
            section(document, "3. 영역별 준비도와 판단 근거", heading);
            PdfPTable dimensions = new PdfPTable(new float[] {2, 1, 1.2f, 5}); dimensions.setWidthPercentage(100);
            headers(dimensions, List.of("평가 영역", "점수", "상태", "판단 근거"), small);
            for (JsonNode row : result.path("dimensions")) {
                dimensions.addCell(cell(row.path("name").asText(), body)); dimensions.addCell(cell(row.path("score").asText(), body));
                dimensions.addCell(cell(status(row.path("status").asText()), body)); dimensions.addCell(cell(row.path("finding").asText(), body));
            }
            document.add(dimensions);
            section(document, "4. 핵심 위험", heading);
            PdfPTable risks = new PdfPTable(new float[] {2, 1, 2.4f, 3.6f}); risks.setWidthPercentage(100);
            headers(risks, List.of("위험", "등급", "사업 영향", "대응책"), small);
            for (JsonNode row : result.path("risks")) {
                risks.addCell(cell(row.path("title").asText(), body)); risks.addCell(cell(status(row.path("severity").asText()), body));
                risks.addCell(cell(row.path("impact").asText(), body)); risks.addCell(cell(row.path("mitigation").asText(), body));
            }
            document.add(risks);
            section(document, "5. 출시 전 확인 기준", heading);
            for (JsonNode gate : result.path("gates")) document.add(new Paragraph("• " + gate.path("title").asText() + " [" + status(gate.path("status").asText()) + "] — " + gate.path("criterion").asText() + " / 확인 자료: " + gate.path("evidenceNeeded").asText(), body));
            section(document, "6. 우선 실행 과제", heading);
            for (JsonNode action : result.path("actions")) {
                document.add(new Paragraph("[" + action.path("priority").asText() + "] " + action.path("title").asText() + " · 담당: " + action.path("owner").asText(), body));
                document.add(new Paragraph("완료 증빙: " + action.path("completionEvidence").asText(), small));
            }
            section(document, "7. 사업 적용 결론", heading);
            document.add(callout("현재 " + label(type) + " 준비도는 '" + decision(result.path("decision").asText()) + "'입니다. " + result.path("summary").asText(), body));
            if (includeSources) {
                section(document, "8. 외부 참고 출처", heading);
                if (view.externalEvidence().isEmpty()) document.add(new Paragraph("외부 검색 근거 없음 · 사용자 전문 입력만으로 분석", small));
                for (JsonNode source : view.externalEvidence()) {
                    Anchor link = new Anchor(source.path("title").asText(), body); link.setReference(source.path("url").asText());
                    document.add(new Paragraph(link)); document.add(new Paragraph(source.path("url").asText(), small));
                }
            }
            document.add(spacer());
            document.add(new Paragraph("본 보고서는 입력 자료와 공개 참고자료를 바탕으로 한 의사결정 지원 문서이며 인증 또는 성과를 보장하지 않습니다.", small));
            document.close(); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("분석 PDF를 만들 수 없습니다.", exception); }
    }
    private String label(ModuleType type) { return switch (type) {
        case TECHNOLOGY -> "기술"; case OPERATIONS -> "운영"; case LAUNCH -> "출시 준비";
    }; }
    private String decision(String value) { return switch (value) { case "READY" -> "출시 준비"; case "CONDITIONAL" -> "조건부 준비"; default -> "보완 후 재검토"; }; }
    private String status(String value) { return switch (value) { case "READY", "PASS" -> "준비"; case "CAUTION", "OPEN", "MEDIUM" -> "주의"; case "RISK", "BLOCKED", "CRITICAL", "HIGH" -> "위험"; case "LOW" -> "낮음"; default -> value; }; }
    private PdfPCell cell(String value, Font font) { PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : value, font)); cell.setPadding(9); cell.setLeading(14, 0); cell.setBorderColor(new Color(210, 220, 230)); return cell; }
    private void headers(PdfPTable table, List<String> values, Font font) { Font white = new Font(font.getBaseFont(), font.getSize(), Font.BOLD, Color.WHITE); for (String value : values) { PdfPCell cell = cell(value, white); cell.setBackgroundColor(NAVY); table.addCell(cell); } }
    private void metric(PdfPTable table, String label, String value, Font body, Font small) { PdfPCell cell = new PdfPCell(); cell.setPadding(9); cell.setBackgroundColor(LIGHT); cell.setBorderColor(Color.WHITE); cell.addElement(new Paragraph(label, small)); cell.addElement(new Paragraph(value, body)); table.addCell(cell); }
    private PdfPTable callout(String value, Font font) { PdfPTable table = new PdfPTable(1); table.setWidthPercentage(100); PdfPCell cell = cell(value, font); cell.setBackgroundColor(LIGHT); cell.setBorderColor(NAVY); table.addCell(cell); return table; }
    private void section(Document document, String value, Font font) throws Exception { document.add(spacer()); Paragraph paragraph = new Paragraph(value, font); paragraph.setSpacingAfter(8); document.add(paragraph); }
    private Paragraph spacer() { Paragraph value = new Paragraph(" "); value.setLeading(14); return value; }
}
