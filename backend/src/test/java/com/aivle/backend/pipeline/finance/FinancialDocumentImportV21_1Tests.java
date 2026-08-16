package com.aivle.backend.pipeline.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.exception.GlobalExceptionHandler;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.finance.service.FinancialAnalysisPdfService;
import com.aivle.backend.finance.service.FinancialSnapshotAnalysisService;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.artifact.api.ProjectEvidenceArtifactApiModels.ArtifactView;
import com.aivle.backend.pipeline.artifact.repository.ProjectEvidenceArtifactRepository;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels;
import com.aivle.backend.pipeline.finance.api.FinancialController;
import com.aivle.backend.pipeline.finance.application.*;
import com.aivle.backend.pipeline.finance.application.FinancialAnalysisService.ImportReplay;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService.FinancialInputDocumentException;
import com.aivle.backend.pipeline.finance.application.FinancialInputDocumentService.ValidationIssue;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class FinancialDocumentImportV21_1Tests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sameCommandAndDocumentReplaysBeforeCreatingAnyInputState() {
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        FinancialInputDocumentService documents = mock(FinancialInputDocumentService.class);
        FinancialService finance = mock(FinancialService.class);
        FinancialAnalysisService analysis = mock(FinancialAnalysisService.class);
        var service = new FinancialDocumentImportService(artifacts, documents, finance, analysis);
        var file = new MockMultipartFile("file", "finance.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1});
        var snapshot = snapshot("sha256:" + "a".repeat(64));
        var action = new FinancialApiModels.AnalysisActionResponse("task-1", "task-1", "RUNNING", "snapshot-1", snapshot.snapshotHash());
        when(artifacts.fingerprint(file)).thenReturn(new ProjectEvidenceArtifactService.UploadFingerprint(
            "sha256:" + "d".repeat(64), 1, file.getContentType()));
        when(analysis.replayImport(7L, 41L, "command-1", "sha256:" + "d".repeat(64)))
            .thenReturn(Optional.of(new ImportReplay(snapshot, action)));
        when(finance.preparation(7L, 41L, "preparation-1")).thenReturn(preparation());

        var response = service.importAndStart(7L, 41L, file, "command-1", "request-1");

        assertThat(response.snapshot().snapshotId()).isEqualTo("snapshot-1");
        assertThat(response.analysis().taskRunId()).isEqualTo("task-1");
        verify(finance).lockImportCommand(7L, 41L);
        verify(artifacts, never()).upload(anyLong(), anyLong(), any());
        verify(finance, never()).importUserDocument(anyLong(), anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(documents);
    }

    @Test
    void newProjectDocumentImportsAndStartsWithoutMarketOrBusinessModel() {
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        FinancialInputDocumentService documents = mock(FinancialInputDocumentService.class);
        FinancialService finance = mock(FinancialService.class);
        FinancialAnalysisService analysis = mock(FinancialAnalysisService.class);
        var service = new FinancialDocumentImportService(artifacts, documents, finance, analysis);
        var file = new MockMultipartFile("file", "finance.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1});
        String documentHash = "sha256:" + "d".repeat(64);
        var snapshot = snapshot(documentHash);
        var action = new FinancialApiModels.AnalysisActionResponse("task-new", "task-new", "QUEUED",
            snapshot.snapshotId(), snapshot.snapshotHash());
        when(artifacts.fingerprint(file)).thenReturn(new ProjectEvidenceArtifactService.UploadFingerprint(
            documentHash, 1, file.getContentType()));
        when(analysis.replayImport(7L, 41L, "command-new", documentHash)).thenReturn(Optional.empty());
        when(artifacts.upload(7L, 41L, file)).thenReturn(new ArtifactView("artifact-new", 41L,
            file.getOriginalFilename(), file.getContentType(), 1, documentHash, null));
        when(documents.parse(file)).thenReturn(mapper.createObjectNode());
        when(finance.importUserDocument(eq(7L), eq(41L), anyString(), anyString(), any())).thenReturn(snapshot);
        when(finance.preparation(7L, 41L, "preparation-1")).thenReturn(preparation());
        when(analysis.start(7L, 41L, "command-new", "request-new")).thenReturn(action);

        var response = service.importAndStart(7L, 41L, file, "command-new", "request-new");

        assertThat(response.analysis().status()).isEqualTo("QUEUED");
        verify(finance).importUserDocument(eq(7L), eq(41L), anyString(), anyString(), any());
        verify(analysis).start(7L, 41L, "command-new", "request-new");
    }

    @Test
    void actualUserDocumentFlowsThroughImportAndStartWithExactSemanticValues() throws Exception {
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        FinancialService finance = mock(FinancialService.class);
        FinancialAnalysisService analysis = mock(FinancialAnalysisService.class);
        var documents = new FinancialInputDocumentService(mapper);
        var service = new FinancialDocumentImportService(artifacts, documents, finance, analysis);
        var file = new MockMultipartFile("file", "finance-readiness-input.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", userFixture());
        String documentHash = "sha256:" + "d".repeat(64);
        var snapshot = snapshot(documentHash);
        var action = new FinancialApiModels.AnalysisActionResponse("task-user", "task-user", "QUEUED",
            snapshot.snapshotId(), snapshot.snapshotHash());
        when(artifacts.fingerprint(file)).thenReturn(new ProjectEvidenceArtifactService.UploadFingerprint(
            documentHash, file.getSize(), file.getContentType()));
        when(analysis.replayImport(7L, 41L, "command-user", documentHash)).thenReturn(Optional.empty());
        when(artifacts.upload(7L, 41L, file)).thenReturn(new ArtifactView("artifact-user", 41L,
            file.getOriginalFilename(), file.getContentType(), file.getSize(), documentHash, null));
        when(finance.importUserDocument(eq(7L), eq(41L), eq("artifact-user"), eq(documentHash), any()))
            .thenReturn(snapshot);
        when(finance.preparation(7L, 41L, "preparation-1")).thenReturn(preparation());
        when(analysis.start(7L, 41L, "command-user", "request-user")).thenReturn(action);

        var response = service.importAndStart(7L, 41L, file, "command-user", "request-user");

        var normalized = org.mockito.ArgumentCaptor.forClass(tools.jackson.databind.JsonNode.class);
        verify(finance).importUserDocument(eq(7L), eq(41L), eq("artifact-user"), eq(documentHash),
            normalized.capture());
        assertThat(response.analysis().status()).isEqualTo("QUEUED");
        assertThat(normalized.getValue().path("annualFixedLaborCost").path("amount").decimalValue())
            .isEqualByComparingTo("160000000");
        assertThat(normalized.getValue().path("revenueModel").asText()).isEqualTo("HYBRID");
        assertThat(normalized.getValue().path("threeYearTargets").path("years").get(2)
            .path("value").decimalValue()).isEqualByComparingTo("900");
        assertThat(normalized.getValue().path(FinancialInputDocumentService.INPUT_NOTES)
            .path("annualFixedLaborCost").asText()).contains("2명", "3~4인");
    }

    @Test
    void sameCommandWithDifferentDocumentConflictsBeforeUpload() {
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        FinancialInputDocumentService documents = mock(FinancialInputDocumentService.class);
        FinancialService finance = mock(FinancialService.class);
        FinancialAnalysisService analysis = mock(FinancialAnalysisService.class);
        var service = new FinancialDocumentImportService(artifacts, documents, finance, analysis);
        var file = new MockMultipartFile("file", "changed.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {2});
        when(artifacts.fingerprint(file)).thenReturn(new ProjectEvidenceArtifactService.UploadFingerprint(
            "sha256:" + "e".repeat(64), 1, file.getContentType()));
        when(analysis.replayImport(7L, 41L, "command-1", "sha256:" + "e".repeat(64)))
            .thenThrow(new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT));

        assertThatThrownBy(() -> service.importAndStart(7L, 41L, file, "command-1", "request-2"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        verify(finance).lockImportCommand(7L, 41L);
        verify(artifacts, never()).upload(anyLong(), anyLong(), any());
        verify(finance, never()).importUserDocument(anyLong(), anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(documents);
    }

    @Test
    void replayLookupUsesFinanceImportActionScopeAndChecksOriginalDocumentHash() {
        FinancialService finance = mock(FinancialService.class);
        TaskRunService taskRuns = mock(TaskRunService.class);
        TaskRunRepository runs = mock(TaskRunRepository.class);
        String input = "{\"snapshotId\":\"snapshot-1\"}";
        TaskRun task = TaskRun.create(mock(Project.class), TaskType.FINANCE_ANALYSIS_REPORT,
            "FINANCIAL_ANALYSIS_REPORT", "USER_DOCUMENT_INPUT", input, "sha256:" + "i".repeat(64),
            "command-1", "request-1", 2);
        String scope = "FINANCE_ANALYSIS_REPORT:FINANCIAL_ANALYSIS_REPORT:USER_DOCUMENT_INPUT";
        when(runs.findByProjectIdAndIdempotencyScopeAndIdempotencyKey(41L, scope, "command-1"))
            .thenReturn(Optional.of(task));
        when(taskRuns.getOwned(7L, 41L, task.getId())).thenReturn(task);
        when(finance.snapshot(7L, 41L, "snapshot-1")).thenReturn(snapshot("sha256:" + "d".repeat(64)));
        var service = new FinancialAnalysisService(finance, mock(FinancialSnapshotAnalysisService.class),
            taskRuns, runs, mock(TaskResultRepository.class), mock(CanonicalInputHasher.class),
            mock(JobEventPublisher.class), mock(ProjectEvidenceArtifactRepository.class), mapper);

        assertThat(service.replayImport(7L, 41L, "command-1", "sha256:" + "d".repeat(64)))
            .isPresent();
        assertThatThrownBy(() -> service.replayImport(7L, 41L, "command-1", "sha256:" + "e".repeat(64)))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        verify(runs, times(2)).findByProjectIdAndIdempotencyScopeAndIdempotencyKey(
            41L, scope, "command-1");
    }

    @Test
    void parserIssueBecomesBoundedFieldError() {
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class, RETURNS_DEEP_STUBS);
        FinancialInputDocumentService documents = mock(FinancialInputDocumentService.class);
        var service = new FinancialDocumentImportService(artifacts, documents,
            mock(FinancialService.class), mock(FinancialAnalysisService.class));
        var file = new MockMultipartFile("file", "finance.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1});
        when(documents.parse(file)).thenThrow(new FinancialInputDocumentException(List.of(
            new ValidationIssue("threeYearTargets", "3개년 성장 목표",
                "1·2·3년차 값을 확인해 주세요.", "1,000 / …"))));

        assertThatThrownBy(() -> service.importDocument(7L, 41L, file))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FINANCIAL_INPUT_INVALID);
                assertThat(error.getFieldErrors()).containsExactly(
                    new ApiResponse.FieldError("threeYearTargets", "1·2·3년차 값을 확인해 주세요."));
            });
    }

    @Test
    void http422PreservesFinancialFieldErrors() throws Exception {
        FinancialDocumentImportService imports = mock(FinancialDocumentImportService.class);
        CurrentUserProvider user = mock(CurrentUserProvider.class);
        when(user.currentUserId()).thenReturn(7L);
        when(imports.importAndStart(eq(7L), eq(41L), any(), eq("command-1"), any()))
            .thenThrow(new BusinessException(ErrorCode.FINANCIAL_INPUT_INVALID,
                "재무 입력 문서를 확인해 주세요.", List.of(
                    new ApiResponse.FieldError("threeYearTargets", "1·2·3년차 값을 확인해 주세요."))));
        FinancialController controller = new FinancialController(mock(FinancialService.class),
            mock(FinancialAnalysisService.class), user, mock(FinancialInputDocumentService.class),
            imports, mock(FinancialAnalysisPdfService.class), mapper);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        var file = new MockMultipartFile("file", "finance.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1});

        mvc.perform(multipart("/api/v3/projects/41/finance/preparation/import").file(file)
                .header("Idempotency-Key", "command-1").contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("FINANCIAL_INPUT_INVALID"))
            .andExpect(jsonPath("$.error.message").value("재무 입력 문서를 확인해 주세요."))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("threeYearTargets"))
            .andExpect(jsonPath("$.error.fieldErrors[0].message").value("1·2·3년차 값을 확인해 주세요."));
    }

    private FinancialApiModels.SnapshotView snapshot(String sourceDocumentHash) {
        ObjectNode json = mapper.createObjectNode();
        json.put("sourceMode", "USER_DOCUMENT_INPUT");
        json.put("sourceDocumentHash", sourceDocumentHash);
        return new FinancialApiModels.SnapshotView("FINANCIAL_INPUT_SNAPSHOT", "snapshot-1", "1.0", 41L,
            "preparation-1", null, null, null, null, "sha256:" + "s".repeat(64), Instant.now(), json, false);
    }

    private FinancialApiModels.PreparationView preparation() {
        return new FinancialApiModels.PreparationView("FINANCIAL_PREPARATION", "1.0", "preparation-1", 41L,
            null, null, null, null, null, false, 1, mapper.createObjectNode(), mapper.createObjectNode(),
            mapper.createObjectNode(), mapper.createObjectNode(), List.of(), true, "snapshot-1", null);
    }

    private byte[] userFixture() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/fixtures/finance/user-finance-readiness-input.docx.b64")) {
            Assertions.assertNotNull(input);
            return Base64.getMimeDecoder().decode(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII));
        }
    }
}
