package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService;
import com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessApiModels.ProfessionalAnalysisView;
import com.aivle.backend.pipeline.launchreadiness.application.*;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessIntegratedReportManifestRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LaunchReadinessReportBundleV21Tests {
    @Test
    void oneReportRemainsIndividualAndTwoReportsCreateManifestWithOneDedupedSourceLink() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LaunchReadinessService readiness = mock(LaunchReadinessService.class);
        LaunchReadinessPdfService professionalPdf = mock(LaunchReadinessPdfService.class);
        FinancialAnalysisService finance = mock(FinancialAnalysisService.class);
        FinancialAnalysisPdfService financePdf = mock(FinancialAnalysisPdfService.class);
        LaunchReadinessIntegratedReportManifestRepository manifests = mock(LaunchReadinessIntegratedReportManifestRepository.class);
        byte[] technologyPdf = onePage("technology");
        byte[] operationsPdf = onePage("operations");
        when(professionalPdf.create(7L, 41L, ModuleType.TECHNOLOGY, true)).thenReturn(technologyPdf);
        when(professionalPdf.create(7L, 41L, ModuleType.TECHNOLOGY, false)).thenReturn(technologyPdf);
        when(professionalPdf.create(7L, 41L, ModuleType.OPERATIONS, false)).thenReturn(operationsPdf);
        ProfessionalAnalysisView technology = view(mapper, "technology-1");
        ProfessionalAnalysisView operations = view(mapper, "operations-1");
        assertThat(technology.externalEvidence()).hasSize(1);
        when(readiness.current(7L, 41L, ModuleType.TECHNOLOGY)).thenReturn(technology);
        when(readiness.current(7L, 41L, ModuleType.OPERATIONS)).thenReturn(operations);
        when(manifests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LaunchReadinessReportBundleService service = new LaunchReadinessReportBundleService(readiness,
            professionalPdf, finance, financePdf, manifests, mapper);

        assertThat(service.create(7L, 41L, List.of("technology"))).isEqualTo(technologyPdf);
        verify(manifests, never()).save(any());

        byte[] integrated = service.create(7L, 41L, List.of("technology", "operations"));
        Files.createDirectories(Path.of("build", "qa"));
        Files.write(Path.of("build", "qa", "launch-integrated-test.pdf"), integrated);
        PdfReader reader = new PdfReader(integrated);
        assertThat(reader.getNumberOfPages()).isEqualTo(4);
        String pdfSyntax = new String(integrated, StandardCharsets.ISO_8859_1);
        assertThat(pdfSyntax.split("https://example.com/official", -1)).hasSize(2);
        reader.close();
        verify(manifests).save(argThat(value -> value.getSelectedModulesJson().contains("technology")
            && value.getSelectedModulesJson().contains("operations")
            && value.getSourceReportsJson().contains("technology-1")
            && value.getSourceReportsJson().contains("operations-1")
            && !value.getSourceReportsJson().contains("sourceBinding")));
    }

    private ProfessionalAnalysisView view(ObjectMapper mapper, String id) {
        var evidence = mapper.createArrayNode();
        evidence.addObject().put("title", "공통 공식 출처")
            .put("url", "https://example.com/official");
        return new ProfessionalAnalysisView("TECHNOLOGY", "SUCCEEDED", false, null, "run", "run",
            "snapshot", "input.docx", mapper.createObjectNode(), hash('a'), hash('b'), id, hash('c'), mapper.createObjectNode(),
            mapper.createObjectNode().put("passed", true), evidence, Instant.parse("2026-08-16T00:00:00Z"), true, false,
            false, null, "PROFESSIONAL_INPUT", null);
    }

    private byte[] onePage(String text) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(); PdfWriter.getInstance(document, output); document.open();
            document.add(new Paragraph(text)); document.close(); return output.toByteArray();
        }
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
