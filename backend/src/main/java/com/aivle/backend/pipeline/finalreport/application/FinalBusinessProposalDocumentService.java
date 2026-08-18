package com.aivle.backend.pipeline.finalreport.application;

import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.pipeline.document.KoreanPdfFontResolver;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FinalBusinessProposalDocumentService {
    private final ObjectMapper mapper;
    private final KoreanPdfFontResolver fonts;
    private final UserRepository users;

    public byte[] renderDocx(FinalReportSnapshot snapshot) {
        return renderDocx(snapshot, null);
    }

    public byte[] renderDocx(FinalReportSnapshot snapshot, JsonNode review) {
        JsonNode report = json(snapshot.getReportJson());
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            title(document, report.path("cover").path("documentName").asText("사업기획서"), 24);
            paragraph(document, report.path("cover").path("businessName").asText("사업명 미정"), true);
            paragraph(document, "문서번호 " + snapshot.getId(), false);
            paragraph(document, "버전 " + snapshot.getReportVersion() + " · 작성자 " + generatedBy(snapshot)
                + " · " + report.path("cover").path("documentStatus").asText("검토용"), false);
            approvalTable(document, generatedBy(snapshot));
            document.createParagraph().createRun().addBreak(BreakType.PAGE);

            heading(document, "목차", 1);
            paragraph(document, "의사결정 요약", false);
            for (JsonNode section : report.path("sections"))
                paragraph(document, section.path("number").asText() + " " + section.path("title").asText(), false);
            paragraph(document, "부록", false);
            if (review != null && review.isObject()) paragraph(document, "부록 · AI 사업기획서 검토 의견", false);
            document.createParagraph().createRun().addBreak(BreakType.PAGE);

            heading(document, "의사결정 요약", 1);
            JsonNode summary = report.path("executiveDecisionSummary");
            labeled(document, "사업 한 줄 정의", summary.path("businessDefinition"));
            labeled(document, "추진 목적", summary.path("purpose"));
            labeled(document, "핵심 가치", summary.path("coreValue"));
            labeled(document, "승인 요청사항", summary.path("approvalRequest"));
            list(document, "대상 고객", summary.path("targetCustomers"));
            list(document, "주요 시장 근거", summary.path("marketEvidence"));
            list(document, "예상 비용·재무 핵심", summary.path("financialHighlights"));
            list(document, "핵심 위험", summary.path("keyRisks"));
            appendEvidence(document, summary);

            for (JsonNode section : report.path("sections")) {
                heading(document, section.path("number").asText() + " " + section.path("title").asText(), 1);
                paragraph(document, section.path("summary").asText(), false);
                for (JsonNode narrative : section.path("narratives")) {
                    heading(document, narrative.path("heading").asText(), 2);
                    paragraph(document, narrative.path("body").asText(), false);
                }
                list(document, "주요 확인사항", section.path("keyPoints"));
                section.path("tables").forEach(table -> appendTable(document, table));
                appendEvidence(document, section);
            }
            heading(document, "부록 · 자료와 가정", 1);
            JsonNode appendix = report.path("appendix");
            list(document, "가정", appendix.path("assumptions"));
            list(document, "포함하지 않은 분석", appendix.path("omittedAnalyses"));
            list(document, "사용 자료 버전", appendix.path("sourceVersions"));
            appendEvidence(document, appendix);
            appendReview(document, review);
            document.write(output);
            return output.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException("사업기획서 DOCX를 생성할 수 없습니다.", failure);
        }
    }

    public byte[] renderPdf(FinalReportSnapshot snapshot) {
        return renderPdf(snapshot, null);
    }

    public byte[] renderPdf(FinalReportSnapshot snapshot, JsonNode review) {
        JsonNode report = json(snapshot.getReportJson());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(() -> fonts.open(false), KoreanPdfFontResolver.FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> fonts.open(true), KoreanPdfFontResolver.FAMILY, 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(html(report, snapshot, review), null); builder.toStream(output); builder.run();
            return output.toByteArray();
        } catch (Exception failure) {
            throw new IllegalStateException("사업기획서 PDF를 생성할 수 없습니다.", failure);
        }
    }

    private String html(JsonNode report, FinalReportSnapshot snapshot, JsonNode review) {
        String shell = """
            <html><head><meta charset='UTF-8'/><style>
            @page{size:A4;margin:16mm 14mm 18mm;@bottom-center{content:'사업기획서 · ' counter(page);font-size:8pt;color:#687875}}
            *{box-sizing:border-box}body{font-family:'Korean Report';font-size:10pt;line-height:1.62;color:#20302d;overflow-wrap:anywhere}
            .cover{min-height:245mm;page-break-after:always;padding-top:35mm;border-bottom:3px solid #176d62}.cover h1{font-size:27pt}.meta{margin-top:18mm;border-top:1px solid #9badaa;padding-top:8mm}.toc{min-height:245mm;page-break-after:always}.toc li{padding:2.5mm 0;border-bottom:1px dotted #aeb9b7}
            h2{margin:10mm 0 4mm;border-bottom:1px solid #43625d;padding-bottom:2mm;page-break-after:avoid}h3{page-break-after:avoid;color:#176d62}
            section{page-break-inside:auto}.callout,table{page-break-inside:avoid}.callout{padding:5mm;background:#eef8f6;border-left:4px solid #168173}
            table{width:100%;border-collapse:collapse;table-layout:fixed;margin:4mm 0}th,td{border:1px solid #bcc9c6;padding:2.5mm;vertical-align:top;overflow-wrap:anywhere}th{background:#edf2f1}
            ul{padding-left:5mm}.source{font-size:8pt;color:#73817f}.page{page-break-before:always}
            </style></head><body><section class='cover'><p>BUSINESS PROPOSAL</p><h1>
            """;
        StringBuilder out = new StringBuilder(shell)
            .append(escape(report.path("cover").path("documentName").asText("사업기획서"))).append("</h1><h2>")
            .append(escape(report.path("cover").path("businessName").asText("사업명 미정"))).append("</h2><div class='meta'>버전 ")
            .append(snapshot.getReportVersion()).append(" · 작성자 ").append(escape(generatedBy(snapshot)))
            .append(" · ").append(escape(report.path("cover").path("documentStatus").asText("검토용")))
            .append("<br/>문서번호 ").append(escape(snapshot.getId()))
            .append("</div><table><thead><tr><th>구분</th><th>작성</th><th>검토</th><th>승인</th></tr></thead><tbody><tr><th>성명</th><td>")
            .append(escape(generatedBy(snapshot))).append("</td><td></td><td></td></tr><tr><th>서명/날인</th><td></td><td></td><td></td></tr><tr><th>일자</th><td></td><td></td><td></td></tr></tbody></table></section>");
        out.append("<section class='toc'><h2>목차</h2><ol><li>의사결정 요약</li>");
        report.path("sections").forEach(section -> out.append("<li>")
            .append(escape(section.path("number").asText())).append(". ")
            .append(escape(section.path("title").asText())).append("</li>"));
        out.append("<li>부록</li>");
        if (review != null && review.isObject()) out.append("<li>부록 · AI 사업기획서 검토 의견</li>");
        out.append("</ol></section>");
        JsonNode summary = report.path("executiveDecisionSummary");
        out.append("<section><h2>의사결정 요약</h2><div class='callout'><strong>사업 한 줄 정의</strong><p>")
            .append(escape(summary.path("businessDefinition").asText())).append("</p><strong>승인 요청사항</strong><p>")
            .append(escape(summary.path("approvalRequest").asText())).append("</p></div>");
        appendHtmlList(out, "대상 고객", summary.path("targetCustomers"));
        appendHtmlList(out, "주요 시장 근거", summary.path("marketEvidence"));
        appendHtmlList(out, "재무 핵심", summary.path("financialHighlights"));
        appendHtmlList(out, "핵심 위험", summary.path("keyRisks")); appendEvidence(out, summary); out.append("</section>");
        for (JsonNode section : report.path("sections")) {
            out.append("<section><h2>").append(escape(section.path("number").asText())).append(". ")
                .append(escape(section.path("title").asText())).append("</h2><p>")
                .append(escape(section.path("summary").asText())).append("</p>");
            for (JsonNode narrative : section.path("narratives")) out.append("<h3>")
                .append(escape(narrative.path("heading").asText())).append("</h3><p>")
                .append(escape(narrative.path("body").asText())).append("</p>");
            appendHtmlList(out, "주요 확인사항", section.path("keyPoints"));
            section.path("tables").forEach(table -> appendHtmlTable(out, table));
            appendEvidence(out, section); out.append("</section>");
        }
        out.append("<section class='page'><h2>부록 · 자료와 가정</h2>");
        appendHtmlList(out, "가정", report.path("appendix").path("assumptions"));
        appendHtmlList(out, "포함하지 않은 분석", report.path("appendix").path("omittedAnalyses"));
        appendHtmlList(out, "사용 자료 버전", report.path("appendix").path("sourceVersions"));
        appendEvidence(out, report.path("appendix"));
        appendHtmlReview(out, review);
        return out.append("</section></body></html>").toString();
    }

    private void appendReview(XWPFDocument document, JsonNode review) {
        if (review == null || !review.isObject()) return;
        heading(document, "AI 사업기획서 검토 의견", 1);
        appendReviewGroup(document, "잘 갖춰진 부분", review.path("wellPrepared"));
        appendReviewGroup(document, "보완 필요", review.path("needsImprovement"));
        appendReviewGroup(document, "결재 전 필수 확인", review.path("requiredBeforeApproval"));
        appendReviewGroup(document, "후속 조치", review.path("followUpActions"));
    }

    private void appendReviewGroup(XWPFDocument document, String title, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) return;
        heading(document, title, 2);
        values.forEach(item -> {
            paragraph(document, item.path("rubric").asText() + " · " + item.path("finding").asText(), false);
            if (item.path("evidenceRefs").isArray() && !item.path("evidenceRefs").isEmpty())
                paragraph(document, "근거: " + join(item.path("evidenceRefs")), false);
        });
    }

    private void appendHtmlReview(StringBuilder out, JsonNode review) {
        if (review == null || !review.isObject()) return;
        out.append("<h2>AI 사업기획서 검토 의견</h2>");
        appendHtmlReviewGroup(out, "잘 갖춰진 부분", review.path("wellPrepared"));
        appendHtmlReviewGroup(out, "보완 필요", review.path("needsImprovement"));
        appendHtmlReviewGroup(out, "결재 전 필수 확인", review.path("requiredBeforeApproval"));
        appendHtmlReviewGroup(out, "후속 조치", review.path("followUpActions"));
    }

    private void appendHtmlReviewGroup(StringBuilder out, String title, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) return;
        out.append("<h3>").append(escape(title)).append("</h3><ul>");
        values.forEach(item -> out.append("<li><strong>").append(escape(item.path("rubric").asText()))
            .append("</strong> · ").append(escape(item.path("finding").asText()))
            .append(item.path("evidenceRefs").isArray() && !item.path("evidenceRefs").isEmpty()
                ? "<br/><span class='source'>근거: " + escape(join(item.path("evidenceRefs"))) + "</span>" : "")
            .append("</li>"));
        out.append("</ul>");
    }

    private String join(JsonNode values) {
        java.util.List<String> result = new java.util.ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return String.join(" · ", result);
    }

    private void appendTable(XWPFDocument document, JsonNode table) {
        heading(document, table.path("title").asText(), 2);
        int columns = Math.max(1, table.path("columns").size());
        XWPFTable value = document.createTable(1, columns);
        for (int i = 0; i < columns; i++) value.getRow(0).getCell(i).setText(table.path("columns").path(i).asText());
        for (JsonNode row : table.path("rows")) {
            var cells = value.createRow().getTableCells();
            for (int i = 0; i < columns; i++) cells.get(i).setText(row.path(i).asText(""));
        }
    }

    private void approvalTable(XWPFDocument document, String author) {
        XWPFTable table = document.createTable(4, 4);
        String[][] values = {{"구분", "작성", "검토", "승인"}, {"성명", author, "", ""},
            {"서명/날인", "", "", ""}, {"일자", "", "", ""}};
        for (int row = 0; row < values.length; row++)
            for (int column = 0; column < values[row].length; column++)
                table.getRow(row).getCell(column).setText(values[row][column]);
    }

    private String generatedBy(FinalReportSnapshot snapshot) {
        return users.findByIdAndDeletedAtIsNull(snapshot.getGeneratedBy())
            .map(user -> user.getName()).orElse("알 수 없는 사용자");
    }

    private void appendHtmlTable(StringBuilder out, JsonNode table) {
        out.append("<h3>").append(escape(table.path("title").asText())).append("</h3><table><thead><tr>");
        table.path("columns").forEach(cell -> out.append("<th>").append(escape(cell.asText())).append("</th>"));
        out.append("</tr></thead><tbody>");
        table.path("rows").forEach(row -> { out.append("<tr>"); row.forEach(cell -> out.append("<td>")
            .append(escape(cell.asText())).append("</td>")); out.append("</tr>"); });
        out.append("</tbody></table>");
    }

    private void appendHtmlList(StringBuilder out, String title, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) return;
        out.append("<h3>").append(escape(title)).append("</h3><ul>");
        values.forEach(item -> out.append("<li>").append(escape(item.asText())).append("</li>")); out.append("</ul>");
    }
    private void appendEvidence(XWPFDocument document, JsonNode owner) {
        JsonNode details = owner.path("evidenceDetails");
        if (!details.isArray() || details.isEmpty()) return;
        heading(document, "근거 상세", 2);
        details.forEach(item -> {
            paragraph(document, item.path("label").asText("근거"), true);
            paragraph(document, item.path("summary").asText(), false);
            if (item.path("actualQuote").isTextual())
                paragraph(document, "대표 원문: “" + item.path("actualQuote").asText() + "”", false);
            paragraph(document, "출처 위치: " + item.path("sourcePath").asText("확인 필요"), false);
            if (item.path("limitation").isTextual()) paragraph(document, item.path("limitation").asText(), false);
        });
    }
    private void appendEvidence(StringBuilder out, JsonNode owner) {
        JsonNode details = owner.path("evidenceDetails");
        if (details.isArray() && !details.isEmpty()) {
            out.append("<h3>근거 상세</h3><table><thead><tr><th>근거</th><th>확인 내용</th><th>출처 위치</th></tr></thead><tbody>");
            details.forEach(item -> out.append("<tr><td>").append(escape(item.path("label").asText()))
                .append("</td><td>").append(escape(item.path("summary").asText()))
                .append(item.path("actualQuote").isTextual() ? "<br/>대표 원문: “" + escape(item.path("actualQuote").asText()) + "”" : "")
                .append("</td><td>").append(escape(item.path("sourcePath").asText()))
                .append(item.path("limitation").isTextual() ? "<br/>" + escape(item.path("limitation").asText()) : "")
                .append("</td></tr>"));
            out.append("</tbody></table>");
            return;
        }
        JsonNode refs = owner.path("evidenceRefs");
        if (refs.isArray() && !refs.isEmpty()) {
            out.append("<p class='source'>사용 근거: ");
            for (int i=0;i<refs.size();i++) { if(i>0)out.append(" · "); out.append(escape(refs.path(i).asText())); }
            out.append("</p>");
        }
    }
    private void title(XWPFDocument d,String text,int size){XWPFParagraph p=d.createParagraph();p.setAlignment(ParagraphAlignment.CENTER);var r=p.createRun();r.setBold(true);r.setFontSize(size);r.setText(text);}
    private void heading(XWPFDocument d,String text,int level){XWPFParagraph p=d.createParagraph();p.setStyle("Heading"+level);var r=p.createRun();r.setBold(true);r.setText(text);}
    private void paragraph(XWPFDocument d,String text,boolean bold){var r=d.createParagraph().createRun();r.setBold(bold);r.setText(text==null?"":text);}
    private void labeled(XWPFDocument d,String label,JsonNode value){heading(d,label,2);paragraph(d,value.asText("자료 없음"),false);}
    private void list(XWPFDocument d,String title,JsonNode values){if(!values.isArray()||values.isEmpty())return;heading(d,title,2);values.forEach(v->{XWPFParagraph p=d.createParagraph();p.setStyle("ListBullet");p.createRun().setText(v.asText());});}
    private JsonNode json(String value){try{return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    /** OpenHTMLToPDF parses XHTML, so only XML predefined entities are emitted. */
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
