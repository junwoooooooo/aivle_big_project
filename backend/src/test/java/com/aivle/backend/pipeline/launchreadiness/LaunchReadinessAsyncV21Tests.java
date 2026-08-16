package com.aivle.backend.pipeline.launchreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.api.ProjectEvidenceArtifactApiModels.ArtifactView;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessDocumentService;
import com.aivle.backend.pipeline.launchreadiness.application.LaunchReadinessService;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot;
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
import java.util.Optional;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

class LaunchReadinessAsyncV21Tests {
    @Test
    void repeatedCommandKeyReturnsExistingTaskBeforeUploadingAnotherDocument() {
        ObjectMapper mapper = new ObjectMapper();
        ProjectRepository projects = mock(ProjectRepository.class); Project project = mock(Project.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        TaskRunRepository runs = mock(TaskRunRepository.class);
        String input = "{\"inputSnapshotId\":\"snapshot-existing\",\"inputSnapshotHash\":\"" + hash('c') + "\"}";
        TaskRun existing = TaskRun.create(project, TaskType.LAUNCH_TECHNOLOGY_READINESS,
            "LAUNCH_READINESS_INPUT", "snapshot-existing", input, hash('d'), "command-1", "request-1", 2);
        when(runs.findFirstByProjectIdAndTaskTypeAndIdempotencyKeyAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, TaskType.LAUNCH_TECHNOLOGY_READINESS, "command-1")).thenReturn(Optional.of(existing));
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        LaunchReadinessService service = new LaunchReadinessService(projects, artifacts,
            new LaunchReadinessDocumentService(), mock(LaunchReadinessInputSnapshotRepository.class),
            mock(LaunchReadinessReportRepository.class), runs, mock(TaskRunService.class),
            mock(CanonicalInputHasher.class), new SnapshotHasher(mapper), mock(JobEventPublisher.class), mapper);

        var replay = service.start(7L, 41L, ModuleType.TECHNOLOGY,
            new MockMultipartFile("file", "different.docx", "application/octet-stream", new byte[] { 1 }),
            "command-1", "request-2");

        assertThat(replay.taskRunId()).isEqualTo(existing.getId());
        assertThat(replay.inputSnapshotId()).isEqualTo("snapshot-existing");
        verifyNoInteractions(artifacts);
    }

    @Test
    void uploadCreatesImmutableSnapshotAndQueuedTaskRunInsteadOfWaitingForAi() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = mock(Project.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        when(artifacts.upload(eq(7L), eq(41L), any())).thenReturn(new ArtifactView("artifact-1", 41L,
            "technology.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            1024, hash('a'), LocalDateTime.now()));
        LaunchReadinessInputSnapshotRepository snapshots = mock(LaunchReadinessInputSnapshotRepository.class);
        when(snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, ModuleType.TECHNOLOGY)).thenReturn(Optional.empty());
        LaunchReadinessReportRepository reports = mock(LaunchReadinessReportRepository.class);
        when(reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(
            41L, ModuleType.TECHNOLOGY)).thenReturn(Optional.empty());
        TaskRunService taskRuns = mock(TaskRunService.class);
        TaskRun task = TaskRun.create(project, TaskType.LAUNCH_TECHNOLOGY_READINESS,
            "LAUNCH_READINESS_INPUT", "snapshot", "{}", hash('b'), "command-1", "request-1", 2);
        when(taskRuns.createWithDisposition(eq(7L), eq(41L), eq(TaskType.LAUNCH_TECHNOLOGY_READINESS),
            eq("LAUNCH_READINESS_INPUT"), anyString(), anyString(), anyString(), eq("command-1"),
            eq("request-1"), eq(2))).thenReturn(new TaskRunService.CreateResult(task, true, false));
        CanonicalInputHasher inputHasher = mock(CanonicalInputHasher.class);
        when(inputHasher.hash(eq(TaskType.LAUNCH_TECHNOLOGY_READINESS), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn(hash('b'));
        JobEventPublisher events = mock(JobEventPublisher.class);
        LaunchReadinessDocumentService documents = new LaunchReadinessDocumentService();
        LaunchReadinessService service = new LaunchReadinessService(projects, artifacts, documents, snapshots,
            reports, mock(TaskRunRepository.class), taskRuns, inputHasher, new SnapshotHasher(mapper), events, mapper);

        var response = service.start(7L, 41L, ModuleType.TECHNOLOGY,
            completedDocument(documents), "command-1", "request-1");

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.inputSnapshotHash()).startsWith("sha256:");
        var savedSnapshot = org.mockito.ArgumentCaptor.forClass(LaunchReadinessInputSnapshot.class);
        verify(snapshots).save(savedSnapshot.capture());
        assertThat(savedSnapshot.getValue().getSourceDocumentArtifactId()).isEqualTo("artifact-1");
        assertThat(savedSnapshot.getValue().getParsedInputJson()).contains("3계층 구조");
        assertThat(savedSnapshot.getValue().isCurrent()).isTrue();
        verify(taskRuns).createWithDisposition(eq(7L), eq(41L),
            eq(TaskType.LAUNCH_TECHNOLOGY_READINESS), eq("LAUNCH_READINESS_INPUT"),
            eq(response.inputSnapshotId()), anyString(), anyString(), eq("command-1"), eq("request-1"), eq(2));
        verify(events).publish(any());

        when(snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, ModuleType.TECHNOLOGY)).thenReturn(Optional.of(savedSnapshot.getValue()));
        var current = service.current(7L, 41L, ModuleType.TECHNOLOGY);
        assertThat(current.professionalInput().path("systemArchitecture").asText()).contains("3계층 구조");
    }

    @Test
    void operationsStartsFromProjectAndProfessionalDocumentWithoutMarketOrBusinessModel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProjectRepository projects = mock(ProjectRepository.class); Project project = mock(Project.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
        when(artifacts.upload(eq(7L), eq(41L), any())).thenReturn(new ArtifactView("artifact-operations", 41L,
            "operations.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            1024, hash('a'), LocalDateTime.now()));
        LaunchReadinessInputSnapshotRepository snapshots = mock(LaunchReadinessInputSnapshotRepository.class);
        when(snapshots.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(
            41L, ModuleType.OPERATIONS)).thenReturn(Optional.empty());
        LaunchReadinessReportRepository reports = mock(LaunchReadinessReportRepository.class);
        when(reports.findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByCompletedAtDesc(
            41L, ModuleType.OPERATIONS)).thenReturn(Optional.empty());
        TaskRunService taskRuns = mock(TaskRunService.class);
        TaskRun task = TaskRun.create(project, TaskType.LAUNCH_OPERATIONS_READINESS,
            "LAUNCH_READINESS_INPUT", "snapshot", "{}", hash('b'), "command-ops", "request-ops", 2);
        when(taskRuns.createWithDisposition(eq(7L), eq(41L), eq(TaskType.LAUNCH_OPERATIONS_READINESS),
            eq("LAUNCH_READINESS_INPUT"), anyString(), anyString(), anyString(), eq("command-ops"),
            eq("request-ops"), eq(2))).thenReturn(new TaskRunService.CreateResult(task, true, false));
        CanonicalInputHasher inputHasher = mock(CanonicalInputHasher.class);
        when(inputHasher.hash(eq(TaskType.LAUNCH_OPERATIONS_READINESS), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn(hash('b'));
        LaunchReadinessDocumentService documents = new LaunchReadinessDocumentService();
        LaunchReadinessService service = new LaunchReadinessService(projects, artifacts, documents, snapshots,
            reports, mock(TaskRunRepository.class), taskRuns, inputHasher, new SnapshotHasher(mapper),
            mock(JobEventPublisher.class), mapper);

        var response = service.start(7L, 41L, ModuleType.OPERATIONS,
            completedDocument(documents, ModuleType.OPERATIONS, "operations.docx"), "command-ops", "request-ops");

        assertThat(response.status()).isEqualTo("QUEUED");
        verify(taskRuns).createWithDisposition(eq(7L), eq(41L), eq(TaskType.LAUNCH_OPERATIONS_READINESS),
            eq("LAUNCH_READINESS_INPUT"), anyString(), anyString(), anyString(), eq("command-ops"),
            eq("request-ops"), eq(2));
    }

    private MockMultipartFile completedDocument(LaunchReadinessDocumentService documents) throws Exception {
        return completedDocument(documents, ModuleType.TECHNOLOGY, "technology.docx");
    }

    private MockMultipartFile completedDocument(LaunchReadinessDocumentService documents,
            ModuleType type, String filename) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documents.template(type)));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getTables().get(0).getRow(2).getCell(0).setText("API와 데이터베이스를 분리한 3계층 구조");
            document.write(output);
            return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", output.toByteArray());
        }
    }

    private static String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
