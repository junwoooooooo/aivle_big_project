package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.selection.domain.SelectedConceptSnapshot;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.repository.SelectedConceptSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectModuleStatusService {
    private final ProjectRepository projectRepository;
    private final ConceptSelectionRepository selectionRepository;
    private final SelectedConceptSnapshotRepository snapshotRepository;
    private final ModuleRunRepository moduleRunRepository;

    public List<ProjectModuleStatusResponse> findAll(Long userId, Long projectId) {
        Project project = projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        boolean hasIdeaDescription = project.getDescription() != null && !project.getDescription().isBlank();
        var selection = selectionRepository.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).orElse(null);
        SelectedConceptSnapshot snapshot = selection == null ? null
            : snapshotRepository.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId).orElse(null);
        var moduleRun = moduleRunRepository.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).orElse(null);
        PipelineModuleStatus marketStatus = moduleRun == null ? PipelineModuleStatus.NOT_CONNECTED
            : snapshot != null && !snapshot.getId().equals(moduleRun.getInputSnapshotId()) ? PipelineModuleStatus.STALE
            : PipelineModuleStatus.valueOf(moduleRun.getStatus().name());
        return List.of(
            response(projectId, PipelineModuleType.IDEA,
                hasIdeaDescription ? PipelineModuleStatus.READY : PipelineModuleStatus.NEEDS_INPUT,
                hasIdeaDescription ? List.of() : List.of("ideaDescription"),
                new NextAction("아이디어 정리", "/idea")),
            response(projectId, PipelineModuleType.CONCEPT_FACTORY, PipelineModuleStatus.NOT_READY,
                List.of("ideaBriefSnapshotId"), new NextAction("아이디어 정리 확인", "/idea")),
            response(projectId, PipelineModuleType.CONCEPT_SELECTION,
                selection == null ? PipelineModuleStatus.NOT_READY : PipelineModuleStatus.COMPLETED,
                selection == null ? List.of("eligibleConcepts") : List.of(),
                new NextAction(selection == null ? "컨셉 생성 결과 확인" : "현재 선택 확인", "/concepts/compare"),
                null, snapshot == null ? null : snapshot.getId(), selection == null ? null : selection.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKET_ANALYSIS, marketStatus,
                snapshot == null ? List.of("selectedConceptSnapshotId", "marketAnalysisConnection") : List.of("marketAnalysisConnection"),
                new NextAction(snapshot == null ? "컨셉 비교·선택" : "시장분석 연결 상태 확인", snapshot == null ? "/concepts/compare" : "/market"),
                moduleRun == null ? null : moduleRun.getId(), snapshot == null ? null : snapshot.getId(), moduleRun == null ? null : moduleRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.BUSINESS_PERSONA_TEST, PipelineModuleStatus.NOT_CONNECTED,
                List.of("finalizedPlanningSnapshotId", "businessPersonaModuleConnection"),
                new NextAction("시장분석 결과 확인", "/market")),
            response(projectId, PipelineModuleType.MARKETING, PipelineModuleStatus.NOT_READY,
                List.of("finalizedPlanningSnapshotId"), new NextAction("기획 확정 상태 확인", "/market"))
        );
    }

    private ProjectModuleStatusResponse response(
        Long projectId,
        PipelineModuleType module,
        PipelineModuleStatus status,
        List<String> requiredInputs,
        NextAction nextAction
    ) {
        return response(projectId, module, status, requiredInputs, nextAction, null, null, null);
    }

    private ProjectModuleStatusResponse response(
        Long projectId,
        PipelineModuleType module,
        PipelineModuleStatus status,
        List<String> requiredInputs,
        NextAction nextAction,
        String activeRunId,
        String sourceSnapshotId,
        java.time.LocalDateTime updatedAt
    ) {
        return new ProjectModuleStatusResponse(
            projectId, module, status, status.getLabelKey(), List.copyOf(requiredInputs), nextAction,
            activeRunId, sourceSnapshotId, updatedAt
        );
    }
}
