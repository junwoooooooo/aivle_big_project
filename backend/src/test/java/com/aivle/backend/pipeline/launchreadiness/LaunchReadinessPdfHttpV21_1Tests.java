package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.common.exception.GlobalExceptionHandler;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels.AnalysisView;
import com.aivle.backend.pipeline.finance.api.FinancialController;
import com.aivle.backend.pipeline.finance.application.*;
import com.aivle.backend.pipeline.launchreadiness.api.*;
import com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessApiModels.ProfessionalAnalysisView;
import com.aivle.backend.pipeline.launchreadiness.application.*;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.*;
import com.lowagie.text.pdf.PdfReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class LaunchReadinessPdfHttpV21_1Tests {
    private final ObjectMapper mapper = new ObjectMapper();
    private LaunchReadinessService readiness;
    private FinancialAnalysisService finance;
    private LaunchReadinessInputSnapshotRepository snapshots;
    private CurrentUserProvider user;
    private LaunchReadinessPdfService professionalPdf;
    private FinancialAnalysisPdfService financialPdf;

    @BeforeEach
    void setUp() {
        readiness = mock(LaunchReadinessService.class);
        finance = mock(FinancialAnalysisService.class);
        snapshots = mock(LaunchReadinessInputSnapshotRepository.class);
        user = mock(CurrentUserProvider.class);
        financialPdf = new FinancialAnalysisPdfService();
        professionalPdf = new LaunchReadinessPdfService(readiness, snapshots,
            new LaunchReadinessDocumentService(), mapper);
        when(user.currentUserId()).thenReturn(7L);
        when(readiness.template(any())).thenReturn(new byte[] {1, 2, 3});
        when(readiness.current(eq(7L), eq(41L), any())).thenAnswer(invocation ->
            professionalView(invocation.getArgument(2)));
        when(snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            eq(41L), any())).thenAnswer(invocation -> Optional.of(snapshot(invocation.getArgument(1))));
        when(finance.current(7L, 41L)).thenReturn(financialView());
    }

    @Test
    void technologyAndOperationsHttpResponsesAreReadablePdfs() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LaunchReadinessController(readiness, professionalPdf, user)).build();
        assertPdf(mvc.perform(get("/api/v3/projects/41/launch-readiness/technology/report"))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn());
        assertPdf(mvc.perform(get("/api/v3/projects/41/launch-readiness/operations/report"))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn());
    }

    @Test
    void pdfDownloadsAndDocxTemplatesUseAttachmentDisposition() throws Exception {
        MockMvc professionalMvc = MockMvcBuilders.standaloneSetup(
            new LaunchReadinessController(readiness, professionalPdf, user)).build();
        professionalMvc.perform(get("/api/v3/projects/41/launch-readiness/technology/template"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=technology-readiness-input.docx"));

        FinancialController financeController = new FinancialController(mock(FinancialService.class), finance, user,
            new FinancialInputDocumentService(mapper), mock(FinancialDocumentImportService.class), financialPdf, mapper);
        MockMvc financeMvc = MockMvcBuilders.standaloneSetup(financeController).build();
        financeMvc.perform(get("/api/v3/projects/41/finance/preparation/template"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=finance-readiness-input.docx"));
    }

    @Test
    void financeHttpResponseUsesRuntimeResultShapeAndIsReadable() throws Exception {
        FinancialController controller = new FinancialController(mock(FinancialService.class), finance, user,
            mock(FinancialInputDocumentService.class), mock(FinancialDocumentImportService.class), financialPdf, mapper);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        assertPdf(mvc.perform(get("/api/v3/projects/41/finance/analysis/report"))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn());
    }

    @Test
    void pdfGenerationFailureIsNon2xxJsonInsteadOfFakePdf() throws Exception {
        FinancialAnalysisService unavailable = mock(FinancialAnalysisService.class);
        when(unavailable.current(7L, 41L)).thenReturn(new AnalysisView(null, null, "NOT_STARTED", false,
            null, "finance-snapshot", hash('f'), null, false, false, null, null));
        FinancialController controller = new FinancialController(mock(FinancialService.class), unavailable, user,
            mock(FinancialInputDocumentService.class), mock(FinancialDocumentImportService.class), financialPdf, mapper);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        MvcResult response = mvc.perform(get("/api/v3/projects/41/finance/analysis/report"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("FINANCIAL_SNAPSHOT_NOT_READY"))
            .andReturn();
        byte[] body = response.getResponse().getContentAsByteArray();
        assertThat(new String(body, 0, Math.min(5, body.length), StandardCharsets.US_ASCII))
            .isNotEqualTo("%PDF-");
    }

    @Test
    void everyIntegratedReportCombinationIsReadable() throws Exception {
        LaunchReadinessIntegratedReportManifestRepository manifests = mock(LaunchReadinessIntegratedReportManifestRepository.class);
        when(manifests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var bundle = new LaunchReadinessReportBundleService(readiness, professionalPdf, finance,
            financialPdf, manifests, mapper);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LaunchReadinessReportController(user, bundle)).build();
        for (List<String> modules : List.of(
                List.of("technology", "operations"),
                List.of("technology", "finance"),
                List.of("technology", "operations", "finance"))) {
            var request = get("/api/v3/projects/41/reports/download");
            for (String module : modules) request = request.param("modules", module);
            assertPdf(mvc.perform(request).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn());
        }
        verify(manifests, times(3)).save(any());
    }

    private void assertPdf(MvcResult result) throws Exception {
        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getHeader("Content-Disposition"))
            .startsWith("attachment; filename=\"");
        assertThat(body.length).isGreaterThan(64);
        assertThat(body).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        PdfReader reader = new PdfReader(body);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        reader.close();
    }

    private ProfessionalAnalysisView professionalView(ModuleType type) {
        var analysis = mapper.readTree("""
            {"decision":"CONDITIONAL","score":78,"summary":"출시 전 확인 기준을 보완하면 진행할 수 있습니다.",
             "dimensions":[{"name":"구조","score":78,"status":"CAUTION","finding":"핵심 구조가 정의되어 있습니다."}],
             "risks":[{"title":"검증 기준","severity":"MEDIUM","impact":"출시 지연","mitigation":"완료 증빙 확인"}],
             "gates":[{"title":"출시 기준","status":"OPEN","criterion":"검증 완료","evidenceNeeded":"검증 결과"}],
             "actions":[{"priority":"P0","title":"증빙 확인","owner":"담당자","completionEvidence":"검증 결과"}],
             "externalEvidence":[],"quality":{"passed":true,"reviewScore":92,"attempts":1,"feedback":[],"unsupportedClaims":[]}}
            """);
        return new ProfessionalAnalysisView(type.name(), "SUCCEEDED", false, null, "run-" + type,
            "run-" + type, "snapshot-" + type, type.name().toLowerCase() + ".docx",
            mapper.createObjectNode().put("systemArchitecture", "웹·API·DB 구조"), hash('a'), hash('b'),
            "report-" + type, hash('c'),
            analysis, analysis.path("quality"), analysis.path("externalEvidence"),
            Instant.parse("2026-08-16T00:00:00Z"), true, false, false, null,
            "CURRENT_CONCEPT_AND_PROFESSIONAL_INPUT",
            mapper.createObjectNode().put("marketSeedSnapshotId", "seed-1")
                .put("selectionId", 11L).put("selectionRevision", 3).put("bmPlanRevision", 4));
    }

    private LaunchReadinessInputSnapshot snapshot(ModuleType type) {
        return LaunchReadinessInputSnapshot.create("snapshot-" + type, 41L, type, "artifact-" + type,
            hash('a'), type.name().toLowerCase() + ".docx", "{}", hash('b'), 7L,
            Instant.parse("2026-08-16T00:00:00Z"));
    }

    private AnalysisView financialView() {
        return new AnalysisView("finance-run", "finance-run", "SUCCEEDED", false, null,
            "finance-snapshot", hash('f'), mapper.valueToTree(financialResult()), false, false,
            java.time.LocalDateTime.of(2026, 8, 16, 9, 0), "finance-plan.docx",
            mapper.createObjectNode().put("marketSeedSnapshotId", "seed-1")
                .put("selectionId", 11L).put("selectionRevision", 3).put("bmPlanRevision", 4), null);
    }

    private FinancialModuleResponse financialResult() {
        return new FinancialModuleResponse(null,
            List.of(new FinancialModuleResponse.ChartPoint(1, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(-200_000), BigDecimal.valueOf(-1_200_000))),
            List.of(new FinancialModuleResponse.AnnualProjection(1, BigDecimal.valueOf(12_000_000), BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(9_000_000), BigDecimal.valueOf(6_000_000), BigDecimal.valueOf(3_000_000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25))),
            List.of(new FinancialModuleResponse.StressScenario("BASE", "기준", 2, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(5_000_000), List.of())),
            new FinancialModuleResponse.MonteCarloSummary(2_000, BigDecimal.valueOf(-100), BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(40), BigDecimal.valueOf(30), 1L),
            new FinancialModuleResponse.ModuleReport("기준 시나리오 결과", List.of("핵심 발견"), List.of("주의 사항"), List.of("권장 조치"), "계획 시뮬레이션입니다.", "SYSTEM_CALCULATION", "SUCCEEDED", null), null);
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
