package com.aivle.backend.project.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.project.dto.request.*;
import com.aivle.backend.project.dto.response.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels;
import com.aivle.backend.pipeline.finalreport.application.FinalReportService;
import com.aivle.backend.pipeline.module.PipelineModuleStatus;
import com.aivle.backend.pipeline.module.PipelineModuleType;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.text.Normalizer;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DomainAuditService auditService;
    private final ServicePolicyService servicePolicy;
    private final ProjectModuleStatusService moduleStatuses;
    private final FinalReportService finalReports;

    @Transactional
    public ProjectDetailResponse create(Long userId, CreateProjectRequest request) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User owner = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        ensureUniqueTitle(userId, request.title(), null);
        return detail(projectRepository.save(Project.create(owner, request.title(), request.description(), request.industryCategory())));
    }

    public List<ProjectSummaryResponse> findAll(Long userId) {
        return projectRepository
                .findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)
                .stream()
                .map(project -> summary(userId, project))
                .toList();
    }

    public ProjectDetailResponse find(Long userId, Long projectId) {
        return detail(ownedProject(userId, projectId));
    }

    @Transactional
    public ProjectDetailResponse update(Long userId, Long projectId, UpdateProjectRequest request) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Project project = ownedProject(userId, projectId);
        ensureUniqueTitle(userId, request.title(), projectId);
        project.updateBasicInfo(request.title(), request.description(), request.industryCategory());
        return detail(project);
    }

    @Transactional
    public void delete(Long userId, Long projectId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Project project = ownedProject(userId, projectId);
        project.softDelete();
        auditService.record(userId, projectId, AuditEventType.PROJECT_DELETED, "PROJECT", projectId, requestId, java.util.Map.of());
    }

    private Project ownedProject(Long userId, Long projectId) {
        return projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void ensureUniqueTitle(Long userId, String title, Long ignoredProjectId) {
        String normalizedTitle = normalizeTitle(title);
        boolean duplicated = projectRepository.findAllByOwnerIdAndDeletedAtIsNull(userId).stream()
                .filter(project -> ignoredProjectId == null || !project.getId().equals(ignoredProjectId))
                .anyMatch(project -> normalizeTitle(project.getTitle()).equals(normalizedTitle));
        if (duplicated) throw new BusinessException(ErrorCode.PROJECT_NAME_ALREADY_EXISTS);
    }

    private String normalizeTitle(String title) {
        return Normalizer.normalize(title == null ? "" : title.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC)
                .toLowerCase(java.util.Locale.ROOT);
    }

    private ProjectSummaryResponse summary(Long userId, Project p) {
        List<ProjectModuleStatusResponse> statuses = moduleStatuses.findAll(userId, p.getId());
        List<JourneySummary> journeys = List.of(
            journey("사업 기획", statuses, PipelineModuleType.IDEA, PipelineModuleType.CONCEPT_PORTFOLIO),
            journey("사업 검증", statuses, PipelineModuleType.MARKET_ANALYSIS, PipelineModuleType.BUSINESS_MODEL),
            journey("출시 준비", statuses, PipelineModuleType.TECH_OPS, PipelineModuleType.FINANCE),
            journey("가상 인터뷰", statuses, PipelineModuleType.TWIN_SURVEY),
            journey("마케팅 전략", statuses, PipelineModuleType.MARKETING));
        FinalReportApiModels.State reportState = finalReports.state(userId, p.getId());
        boolean reportCurrent = reportState == FinalReportApiModels.State.CURRENT;
        boolean reportStale = reportState == FinalReportApiModels.State.STALE;
        int completed = (int) journeys.stream().filter(JourneySummary::completed).count() + (reportCurrent ? 1 : 0);
        String current = journeys.stream().filter(value -> !value.completed()).map(JourneySummary::label)
            .findFirst().orElse("최종 보고서");
        List<ProjectModuleStatusResponse> attentionModules = statuses.stream()
            .filter(this::requiresUserAttention)
            .toList();
        int attentionCount = attentionModules.size() + (reportStale ? 1 : 0);
        boolean started = statuses.stream().anyMatch(this::hasStartedWork) || reportCurrent || reportStale;
        String presentationState = completed == 6 ? "COMPLETED"
            : attentionCount > 0 ? "NEEDS_ATTENTION"
            : started ? "IN_PROGRESS" : "NOT_STARTED";
        String attentionReason = !attentionModules.isEmpty()
            ? attentionReason(attentionModules.get(0).status())
            : reportStale ? "최종 보고서를 업데이트해 주세요." : null;
        return new ProjectSummaryResponse(p.getId(), p.getTitle(), p.getIndustryCategory(),
                p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(), current, completed,
                presentationState, attentionCount, attentionReason);
    }

    private boolean requiresUserAttention(ProjectModuleStatusResponse status) {
        if (status.status() == PipelineModuleStatus.FAILED || status.status() == PipelineModuleStatus.STALE) {
            return hasStartedWork(status);
        }
        return status.status() == PipelineModuleStatus.NEEDS_INPUT && hasStartedWork(status);
    }

    private boolean hasStartedWork(ProjectModuleStatusResponse status) {
        return status.updatedAt() != null || status.activeRunId() != null || status.activeTaskRunId() != null
            || status.sourceSnapshotId() != null || status.confirmedSnapshotId() != null
            || status.status() == PipelineModuleStatus.QUEUED || status.status() == PipelineModuleStatus.RUNNING
            || status.status() == PipelineModuleStatus.COMPLETED;
    }

    private String attentionReason(PipelineModuleStatus status) {
        return switch (status) {
            case NEEDS_INPUT -> "계속하려면 필요한 내용을 입력해 주세요.";
            case STALE -> "이전 결과를 최신 내용에 맞게 업데이트해 주세요.";
            case FAILED -> "완료하지 못한 작업을 확인해 주세요.";
            default -> "확인이 필요한 항목이 있습니다.";
        };
    }

    private JourneySummary journey(String label, List<ProjectModuleStatusResponse> statuses,
            PipelineModuleType... modules) {
        List<PipelineModuleType> types = List.of(modules);
        boolean completed = statuses.stream().filter(value -> types.contains(value.module()))
            .allMatch(value -> value.status() == PipelineModuleStatus.COMPLETED);
        return new JourneySummary(label, completed);
    }

    private record JourneySummary(String label, boolean completed) {}

    private ProjectDetailResponse detail(Project p) {
        return new ProjectDetailResponse(p.getId(), p.getOwner().getId(), p.getTitle(), p.getDescription(),
                p.getIndustryCategory(), p.getStatus(), p.getStartedAt(), p.getCompletedAt(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }
}
