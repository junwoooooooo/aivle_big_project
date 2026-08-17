package com.aivle.backend.pipeline.launchreadiness;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aivle.backend.common.exception.GlobalExceptionHandler;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.api.ProjectEvidenceArtifactApiModels.ArtifactView;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService.UploadFingerprint;
import com.aivle.backend.pipeline.launchreadiness.api.LaunchReadinessController;
import com.aivle.backend.pipeline.launchreadiness.application.*;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.*;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class LaunchReadinessMultipartControllerTests {
    @ParameterizedTest
    @EnumSource(ModuleType.class)
    void validProfessionalDocxStartsThroughMultipartControllerWithoutGeneric500(ModuleType type) throws Exception {
        Harness harness = new Harness(type);

        harness.mvc.perform(multipart("/api/v3/projects/41/launch-readiness/{module}/analysis-runs",
                type.name().toLowerCase()).file(harness.completedDocument())
                .header("Idempotency-Key", "command-" + type.name().toLowerCase())
                .header("X-Request-Id", "request-" + type.name().toLowerCase()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void invalidDocxReturnsExplicitValidationInsteadOfInternalServerError() throws Exception {
        Harness harness = new Harness(ModuleType.TECHNOLOGY);
        MockMultipartFile invalid = new MockMultipartFile("file", "invalid.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[] {0x50, 0x4b, 0x03, 0x04, 1, 2, 3});

        harness.mvc.perform(multipart("/api/v3/projects/41/launch-readiness/technology/analysis-runs")
                .file(invalid).header("Idempotency-Key", "command-invalid")
                .header("X-Request-Id", "request-invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private static final class Harness {
        private final ModuleType type;
        private final LaunchReadinessDocumentService documents = new LaunchReadinessDocumentService();
        private final MockMvc mvc;

        private Harness(ModuleType type) {
            this.type = type;
            ObjectMapper mapper = new ObjectMapper();
            ProjectRepository projects = mock(ProjectRepository.class);
            Project project = mock(Project.class);
            when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L))
                .thenReturn(java.util.Optional.of(project));
            ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
            when(artifacts.fingerprint(any())).thenReturn(new UploadFingerprint(hash('a'), 1024,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            when(artifacts.upload(eq(7L), eq(41L), any())).thenReturn(new ArtifactView(
                "artifact-1", 41L, type.name().toLowerCase() + ".docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                1024, hash('a'), LocalDateTime.now()));
            LaunchReadinessInputSnapshotRepository snapshots = mock(LaunchReadinessInputSnapshotRepository.class);
            LaunchReadinessReportRepository reports = mock(LaunchReadinessReportRepository.class);
            TaskRunRepository runs = mock(TaskRunRepository.class);
            TaskRunService taskRuns = mock(TaskRunService.class);
            TaskType taskType = switch (type) {
                case TECHNOLOGY -> TaskType.LAUNCH_TECHNOLOGY_READINESS;
                case OPERATIONS -> TaskType.LAUNCH_OPERATIONS_READINESS;
                case LAUNCH -> TaskType.LAUNCH_READINESS;
            };
            TaskRun task = TaskRun.create(project, taskType, "LAUNCH_READINESS_INPUT", "snapshot",
                "{}", hash('b'), "command-" + type.name().toLowerCase(),
                "request-" + type.name().toLowerCase(), 1);
            when(taskRuns.createWithDisposition(eq(7L), eq(41L), eq(taskType),
                eq("LAUNCH_READINESS_INPUT"), anyString(), anyString(), anyString(), anyString(),
                anyString(), eq(1))).thenReturn(new TaskRunService.CreateResult(task, true, false));
            CanonicalInputHasher inputHasher = mock(CanonicalInputHasher.class);
            when(inputHasher.hash(eq(taskType), eq("1.0"), eq("ko-KR"), anyString())).thenReturn(hash('b'));
            LaunchReadinessService service = new LaunchReadinessService(projects, artifacts, documents,
                snapshots, reports, runs, taskRuns, inputHasher, new SnapshotHasher(mapper),
                mock(JobEventPublisher.class), mapper);
            CurrentUserProvider user = mock(CurrentUserProvider.class);
            when(user.currentUserId()).thenReturn(7L);
            this.mvc = MockMvcBuilders.standaloneSetup(new LaunchReadinessController(
                    service, mock(LaunchReadinessPdfService.class), user))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        }

        private MockMultipartFile completedDocument() throws Exception {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documents.template(type)));
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.getTables().get(0).getRow(2).getCell(0)
                    .setText("선택 사업안을 위한 전문 실행 계획");
                document.write(output);
                return new MockMultipartFile("file", type.name().toLowerCase() + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    output.toByteArray());
            }
        }
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
