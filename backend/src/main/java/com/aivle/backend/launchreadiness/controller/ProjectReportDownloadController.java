package com.aivle.backend.launchreadiness.controller;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import com.aivle.backend.launchreadiness.domain.ProfessionalAnalysisReport.ModuleType;
import com.aivle.backend.launchreadiness.service.ProfessionalLaunchReadinessService;
import com.aivle.backend.pipeline.finance.application.FinancialService;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;
import java.io.ByteArrayOutputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/reports")
@RequiredArgsConstructor
public class ProjectReportDownloadController {
    private final CurrentUserProvider user;
    private final ProfessionalLaunchReadinessService readiness;
    private final FinancialService finance;
    private final FinancialAnalysisService financialAnalysis;
    private final FinancialAnalysisPdfService financialPdf;
    private final ObjectMapper mapper;

    @GetMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long projectId, @RequestParam List<String> modules) {
        if (modules == null || modules.isEmpty()) throw new IllegalArgumentException("다운로드할 보고서를 선택해 주세요.");
        boolean integrated = modules.stream().distinct().count() > 1;
        List<byte[]> documents = modules.stream().distinct().map(module -> switch (module) {
            case "technology" -> readiness.pdf(user.currentUserId(), projectId, ModuleType.TECHNOLOGY, !integrated);
            case "operations" -> readiness.pdf(user.currentUserId(), projectId, ModuleType.OPERATIONS, !integrated);
            case "finance" -> financial(projectId);
            default -> throw new IllegalArgumentException("알 수 없는 보고서 유형입니다: " + module);
        }).toList();
        byte[] body = documents.size() == 1 ? documents.get(0) : merge(documents, modules, projectId);
        String name = documents.size() == 1 ? modules.get(0) + "-analysis-report.pdf" : "integrated-analysis-report.pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=" + name).body(new ByteArrayResource(body));
    }

    private byte[] financial(Long projectId) {
        var view = financialAnalysis.current(user.currentUserId(), projectId);
        if (view.result() == null) throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY);
        FinancialModuleResponse report = mapper.readValue(mapper.writeValueAsString(view.result()), FinancialModuleResponse.class);
        return financialPdf.create(report);
    }

    private byte[] merge(List<byte[]> documents, List<String> modules, Long projectId) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(); PdfCopy copy = new PdfCopy(document, output); document.open();
            append(copy, coverPage("통합보고서", "선택한 기술·운영·재무 분석 결과를 하나의 보고서로 구성했습니다."));
            for (byte[] bytes : documents) { PdfReader reader = new PdfReader(bytes); for (int page = 1; page <= reader.getNumberOfPages(); page++) copy.addPage(copy.getImportedPage(reader, page)); reader.close(); }
            append(copy, frontMatter("출처", String.join("\n\n", modules.stream().map(value -> source(projectId, value)).toList())));
            document.close(); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("통합 보고서를 만들 수 없습니다.", exception); }
    }

    private void append(PdfCopy copy, byte[] bytes) throws Exception { PdfReader reader = new PdfReader(bytes); for (int page = 1; page <= reader.getNumberOfPages(); page++) copy.addPage(copy.getImportedPage(reader, page)); reader.close(); }
    private byte[] coverPage(String title, String content) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4); var writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, output); document.open();
            BaseFont base = BaseFont.createFont("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            float centerX = PageSize.A4.getWidth() / 2;
            float centerY = PageSize.A4.getHeight() / 2;
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, new Phrase(title, new Font(base, 24, Font.BOLD)), centerX, centerY + 18, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, new Phrase(content, new Font(base, 11)), centerX, centerY - 14, 0);
            document.close(); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("통합 보고서 표지를 만들 수 없습니다.", exception); }
    }
    private byte[] frontMatter(String title, String content) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 70, 50); var writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, output); document.open();
            BaseFont base = BaseFont.createFont("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            document.add(new Paragraph(title, new Font(base, 24, Font.BOLD))); document.add(new Paragraph(" ")); document.add(new Paragraph(content, new Font(base, 11))); document.close(); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("통합 보고서 표지를 만들 수 없습니다.", exception); }
    }
    private String source(Long projectId, String value) {
        ModuleType type = switch (value) {
            case "technology" -> ModuleType.TECHNOLOGY;
            case "operations" -> ModuleType.OPERATIONS;
            default -> null;
        };
        if (type == null) return "finance".equals(value) ? "재무 분석 보고서 · 업로드된 재무 입력값" : value;
        String label = type == ModuleType.TECHNOLOGY ? "기술 분석 보고서 · 외부 참고 출처" : "운영 분석 보고서 · 외부 참고 출처";
        List<ProfessionalLaunchReadinessService.ExternalReference> references = readiness.externalReferences(
            user.currentUserId(), projectId, type);
        if (references.isEmpty()) return label + "\n• 외부 검색 근거 없음";
        return label + "\n" + String.join("\n", references.stream()
            .map(reference -> "• " + reference.title() + "\n  " + reference.url()).toList());
    }
}
