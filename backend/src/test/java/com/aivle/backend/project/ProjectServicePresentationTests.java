package com.aivle.backend.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.pipeline.module.PipelineModuleStatus;
import com.aivle.backend.pipeline.module.PipelineModuleType;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.project.service.ProjectService;
import com.aivle.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectServicePresentationTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectModuleStatusService modules = mock(ProjectModuleStatusService.class);
    private final FinalReportService reports = mock(FinalReportService.class);
    private final ProjectService service = new ProjectService(projects, mock(UserRepository.class),
        mock(DomainAuditService.class), mock(ServicePolicyService.class), modules, reports);

    @Test
    void untouchedProjectIsNotStartedInsteadOfNeedsAttention() {
        stubProject(statuses(PipelineModuleStatus.NEEDS_INPUT, null));

        var summary = service.findAll(2L).get(0);

        assertThat(summary.presentationState()).isEqualTo("NOT_STARTED");
        assertThat(summary.attentionCount()).isZero();
    }

    @Test
    void needsInputAfterWorkStartedRequiresAttention() {
        stubProject(statuses(PipelineModuleStatus.NEEDS_INPUT, LocalDateTime.of(2026, 8, 14, 9, 0)));

        var summary = service.findAll(2L).get(0);

        assertThat(summary.presentationState()).isEqualTo("NEEDS_ATTENTION");
        assertThat(summary.attentionCount()).isEqualTo(1);
        assertThat(summary.attentionReason()).contains("입력");
    }

    private void stubProject(List<ProjectModuleStatusResponse> statuses) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(41L);
        when(project.getTitle()).thenReturn("스마트 킥포인트");
        when(project.getStatus()).thenReturn(ProjectStatus.DRAFT);
        when(projects.findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(2L)).thenReturn(List.of(project));
        when(modules.findAll(2L, 41L)).thenReturn(statuses);
        when(reports.state(2L, 41L)).thenReturn(FinalReportApiModels.State.NOT_READY);
    }

    private List<ProjectModuleStatusResponse> statuses(PipelineModuleStatus ideaStatus, LocalDateTime ideaUpdatedAt) {
        return Arrays.stream(PipelineModuleType.values()).map(type -> response(type,
            type == PipelineModuleType.IDEA ? ideaStatus : PipelineModuleStatus.NOT_READY,
            type == PipelineModuleType.IDEA ? ideaUpdatedAt : null)).toList();
    }

    private ProjectModuleStatusResponse response(PipelineModuleType type, PipelineModuleStatus status,
            LocalDateTime updatedAt) {
        return new ProjectModuleStatusResponse(41L, type, status, status.getLabelKey(), List.of(),
            new NextAction("확인", "/overview"), null, null, null, null, null, null, updatedAt);
    }
}
