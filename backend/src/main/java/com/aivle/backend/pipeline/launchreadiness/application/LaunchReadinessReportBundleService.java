package com.aivle.backend.pipeline.launchreadiness.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessIntegratedReportManifest;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessIntegratedReportManifestRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Service
@RequiredArgsConstructor
public class LaunchReadinessReportBundleService {
    private final LaunchReadinessService readiness;
    private final LaunchReadinessPdfService professionalPdf;
    private final FinancialAnalysisService financialAnalysis;
    private final FinancialAnalysisPdfService financialPdf;
    private final LaunchReadinessIntegratedReportManifestRepository manifests;
    private final ObjectMapper mapper;

    @Transactional
    public byte[] create(Long ownerId, Long projectId, List<String> requested) {
        List<String> modules = requested == null ? List.of() : requested.stream()
            .map(String::toLowerCase).filter(Set.of("technology", "operations", "finance")::contains).distinct().toList();
        if (modules.isEmpty()) throw new IllegalArgumentException("다운로드할 보고서를 선택해 주세요.");
        boolean integrated = modules.size() > 1;
        List<byte[]> documents = new ArrayList<>(); ArrayNode sources = mapper.createArrayNode();
        LinkedHashMap<String, Source> external = new LinkedHashMap<>();
        for (String module : modules) {
            if ("finance".equals(module)) {
                var view = financialAnalysis.current(ownerId, projectId);
                if (view.result() == null || view.stale()) throw new BusinessException(ErrorCode.FINANCIAL_SNAPSHOT_NOT_READY);
                documents.add(financialPdf.create(mapper.readValue(mapper.writeValueAsString(view.result()), FinancialModuleResponse.class)));
                sources.addObject().put("module", module).put("taskRunId", view.taskRunId()).put("snapshotHash", view.snapshotHash());
            } else {
                ModuleType type = "technology".equals(module) ? ModuleType.TECHNOLOGY : ModuleType.OPERATIONS;
                var view = readiness.current(ownerId, projectId, type);
                if (view.analysis() == null || view.stale()) throw new IllegalArgumentException(module + " 분석 보고서가 준비되지 않았습니다.");
                documents.add(professionalPdf.create(ownerId, projectId, type, !integrated));
                sources.addObject().put("module", module).put("resultId", view.resultId()).put("resultHash", view.resultHash()).put("snapshotHash", view.inputSnapshotHash());
                if (integrated) for (var item : view.externalEvidence()) {
                    String url = item.path("url").asText("").trim(); String title = item.path("title").asText("").trim();
                    if (!url.isBlank()) external.putIfAbsent(url, new Source(title, url));
                }
            }
        }
        if (!integrated) return documents.get(0);
        Instant now = Instant.now();
        manifests.save(LaunchReadinessIntegratedReportManifest.create(UUID.randomUUID().toString(), projectId,
            mapper.writeValueAsString(modules), mapper.writeValueAsString(sources), ownerId, now));
        return merge(documents, modules, external.values(), now);
    }

    private byte[] merge(List<byte[]> documents, List<String> modules, Collection<Source> sources, Instant generatedAt) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfCopyFields copy = new PdfCopyFields(output);
            append(copy, cover("출시 준비 통합 보고서", "선택 보고서: " + String.join(", ", modules) + "\n생성 시각: " + generatedAt));
            for (byte[] bytes : documents) append(copy, bytes);
            append(copy, sources(sources)); copy.close();
            return addSourceLinks(output.toByteArray(), sources);
        } catch (Exception exception) { throw new IllegalStateException("통합 보고서를 만들 수 없습니다.", exception); }
    }
    private byte[] cover(String title, String subtitle) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 90, 50); PdfWriter.getInstance(doc, out); doc.open();
            Font titleFont = KoreanPdfFonts.font(24, Font.BOLD, new Color(31, 78, 121));
            Font body = KoreanPdfFonts.font(11, Font.NORMAL, new Color(60, 70, 82));
            Paragraph heading = new Paragraph(title, titleFont); heading.setAlignment(Element.ALIGN_CENTER); heading.setSpacingBefore(220); doc.add(heading);
            Paragraph description = new Paragraph(subtitle, body); description.setAlignment(Element.ALIGN_CENTER); description.setSpacingBefore(18); doc.add(description);
            doc.close(); return out.toByteArray();
        }
    }
    private byte[] sources(Collection<Source> sources) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter.getInstance(doc, out); doc.open();
            Font title = KoreanPdfFonts.font(20, Font.BOLD, new Color(31, 78, 121)); Font body = KoreanPdfFonts.font(9, Font.NORMAL, Color.DARK_GRAY);
            doc.add(new Paragraph("통합 외부 참고 출처", title));
            if (sources.isEmpty()) doc.add(new Paragraph("외부 검색 근거 없음", body));
            for (Source source : sources) {
                doc.add(new Paragraph(new Chunk(source.title().isBlank() ? source.url() : source.title(), body)));
                doc.add(new Paragraph(source.url(), body));
            }
            doc.close(); return out.toByteArray();
        }
    }

    private byte[] addSourceLinks(byte[] merged, Collection<Source> sources) throws Exception {
        if (sources.isEmpty()) return merged;
        PdfReader reader = new PdfReader(merged);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfStamper stamper = new PdfStamper(reader, output);
            int sourcePage = reader.getNumberOfPages(); int index = 0;
            for (Source source : sources) {
                float top = PageSize.A4.getHeight() - 105 - (index++ * 35);
                PdfAnnotation link = PdfAnnotation.createLink(stamper.getWriter(),
                    new Rectangle(48, top - 28, PageSize.A4.getWidth() - 48, top),
                    PdfAnnotation.HIGHLIGHT_INVERT, new PdfAction(source.url()));
                link.setBorder(new PdfBorderArray(0, 0, 0));
                stamper.addAnnotation(link, sourcePage);
            }
            stamper.close(); reader.close(); return output.toByteArray();
        }
    }
    private void append(PdfCopyFields copy, byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        copy.addDocument(reader);
        reader.close();
    }
    private record Source(String title, String url) {}
}
