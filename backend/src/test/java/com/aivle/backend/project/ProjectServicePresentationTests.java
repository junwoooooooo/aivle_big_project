package com.aivle.backend.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.entity.ProjectStatus;
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
    private final ProjectService service = new ProjectService(projects, mock(UserRepository.class),
        mock(DomainAuditService.class), mock(ServicePolicyService.class), modules);

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

    @Test
    void optionalLaunchAndFinalReportDoNotAffectProjectCompletion() {
        var allCompleted = Arrays.stream(PipelineModuleType.values())
            .map(type -> response(type, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)))
            .toList();
        stubProject(allCompleted);
        var summary = service.findAll(2L).get(0);

        assertThat(summary.completedJourneyCount()).isEqualTo(4);
        assertThat(summary.presentationState()).isEqualTo("COMPLETED");
        assertThat(summary.currentJourneyLabel()).isEqualTo("최종 보고서");
    }

    @Test
    void completedMarketInterviewIsNotBlockedByTwinSurvey() {
        var statuses = allOtherJourneysCompleted();
        statuses.add(response(PipelineModuleType.MARKET_INTERVIEW, PipelineModuleStatus.COMPLETED,
            LocalDateTime.of(2026, 8, 17, 10, 0)));
        statuses.add(response(PipelineModuleType.TWIN_SURVEY, PipelineModuleStatus.NOT_READY, null));
        stubProject(statuses);

        var summary = service.findAll(2L).get(0);

        assertThat(summary.completedJourneyCount()).isEqualTo(4);
        assertThat(summary.currentJourneyLabel()).isEqualTo("최종 보고서");
        assertThat(summary.attentionCount()).isZero();
    }

    @Test
    void completedMarketAndBusinessModelDoNotCompleteValidationBeforeRefinement() {
        var statuses = allOtherJourneysCompleted();
        statuses.removeIf(status -> status.module() == PipelineModuleType.CONCEPT_REFINEMENT);
        statuses.add(response(PipelineModuleType.CONCEPT_REFINEMENT, PipelineModuleStatus.READY, null));
        statuses.add(response(PipelineModuleType.MARKET_INTERVIEW, PipelineModuleStatus.COMPLETED,
            LocalDateTime.of(2026, 8, 17, 10, 0)));
        statuses.add(response(PipelineModuleType.TWIN_SURVEY, PipelineModuleStatus.NOT_READY, null));
        stubProject(statuses);

        var summary = service.findAll(2L).get(0);

        assertThat(summary.completedJourneyCount()).isEqualTo(3);
        assertThat(summary.currentJourneyLabel()).isEqualTo("사업 검증");
    }

    @Test
    void legacyCompletedTwinSurveyDoesNotReplaceCanonicalInterview() {
        var statuses = allOtherJourneysCompleted();
        statuses.add(response(PipelineModuleType.MARKET_INTERVIEW, PipelineModuleStatus.READY, null));
        statuses.add(response(PipelineModuleType.TWIN_SURVEY, PipelineModuleStatus.COMPLETED,
            LocalDateTime.of(2026, 8, 16, 10, 0)));
        stubProject(statuses);

        var summary = service.findAll(2L).get(0);

        assertThat(summary.completedJourneyCount()).isEqualTo(3);
        assertThat(summary.currentJourneyLabel()).isEqualTo("가상 인터뷰");
    }

    @Test
    void currentInterviewFailureWinsAndLegacyTwinFailureCannotBlockCompletedInterview() {
        var failedInterview = allOtherJourneysCompleted();
        failedInterview.add(response(PipelineModuleType.MARKET_INTERVIEW, PipelineModuleStatus.FAILED,
            LocalDateTime.of(2026, 8, 17, 10, 0)));
        failedInterview.add(response(PipelineModuleType.TWIN_SURVEY, PipelineModuleStatus.COMPLETED,
            LocalDateTime.of(2026, 8, 16, 10, 0)));
        stubProject(failedInterview);
        assertThat(service.findAll(2L).get(0).attentionCount()).isEqualTo(1);

        var completedInterview = allOtherJourneysCompleted();
        completedInterview.add(response(PipelineModuleType.MARKET_INTERVIEW, PipelineModuleStatus.COMPLETED,
            LocalDateTime.of(2026, 8, 17, 10, 0)));
        completedInterview.add(response(PipelineModuleType.TWIN_SURVEY, PipelineModuleStatus.FAILED,
            LocalDateTime.of(2026, 8, 16, 10, 0)));
        stubProject(completedInterview);
        assertThat(service.findAll(2L).get(0).completedJourneyCount()).isEqualTo(4);
        assertThat(service.findAll(2L).get(0).attentionCount()).isZero();
    }

    private java.util.ArrayList<ProjectModuleStatusResponse> allOtherJourneysCompleted() {
        return new java.util.ArrayList<>(List.of(
            response(PipelineModuleType.IDEA, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.CONCEPT_PORTFOLIO, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.MARKET_ANALYSIS, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.BUSINESS_MODEL, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.CONCEPT_REFINEMENT, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.TECH_OPS, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.FINANCE, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0)),
            response(PipelineModuleType.MARKETING, PipelineModuleStatus.COMPLETED, LocalDateTime.of(2026, 8, 17, 9, 0))));
    }

    private void stubProject(List<ProjectModuleStatusResponse> statuses) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(41L);
        when(project.getTitle()).thenReturn("스마트 킥포인트");
        when(project.getStatus()).thenReturn(ProjectStatus.DRAFT);
        when(projects.findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(2L)).thenReturn(List.of(project));
        when(modules.findAll(2L, 41L)).thenReturn(statuses);
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
