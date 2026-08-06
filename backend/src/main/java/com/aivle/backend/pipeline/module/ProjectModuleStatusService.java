package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
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

    public List<ProjectModuleStatusResponse> findAll(Long userId, Long projectId) {
        Project project = projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        boolean hasIdeaDescription = project.getDescription() != null && !project.getDescription().isBlank();
        return List.of(
            response(projectId, PipelineModuleType.IDEA,
                hasIdeaDescription ? PipelineModuleStatus.READY : PipelineModuleStatus.NEEDS_INPUT,
                hasIdeaDescription ? List.of() : List.of("ideaDescription"),
                new NextAction("아이디어 정리", "/idea")),
            response(projectId, PipelineModuleType.CONCEPT_FACTORY, PipelineModuleStatus.NOT_READY,
                List.of("ideaBriefSnapshotId"), new NextAction("아이디어 정리 확인", "/idea")),
            response(projectId, PipelineModuleType.CONCEPT_SELECTION, PipelineModuleStatus.NOT_READY,
                List.of("eligibleConcepts"), new NextAction("컨셉 생성 결과 확인", "/concepts")),
            response(projectId, PipelineModuleType.MARKET_ANALYSIS, PipelineModuleStatus.NOT_CONNECTED,
                List.of("selectedConceptSnapshotId", "marketAnalysisConnection"),
                new NextAction("컨셉 비교·선택", "/concepts/compare")),
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
        return new ProjectModuleStatusResponse(
            projectId, module, status, status.getLabelKey(), List.copyOf(requiredInputs), nextAction,
            null, null, null
        );
    }
}
