package com.aivle.backend.document.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.dto.response.*;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.document.repository.MissingFieldRepository;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.document.repository.StructuredPlanSectionRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredPlanQueryService {
    private final ProjectRepository projectRepository;
    private final StructuredPlanRepository planRepository;
    private final StructuredPlanSectionRepository sectionRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final ObjectMapper objectMapper;

    public StructuredPlanResponse findLatest(Long userId, Long projectId) {
        requireOwnedProject(userId, projectId);
        StructuredPlan plan = planRepository
            .findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STRUCTURED_PLAN_NOT_FOUND));
        return toResponse(plan);
    }

    public StructuredPlanResponse findById(
        Long userId,
        Long projectId,
        Long planId
    ) {
        requireOwnedProject(userId, projectId);
        StructuredPlan plan = planRepository
            .findByIdAndProjectIdAndDeletedAtIsNull(planId, projectId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.STRUCTURED_PLAN_NOT_FOUND));
        return toResponse(plan);
    }

    private StructuredPlanResponse toResponse(StructuredPlan plan) {
        var sections = sectionRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(plan.getId())
            .stream()
            .map(section -> new StructuredPlanSectionResponse(
                section.getSectionCode(),
                section.getTitle(),
                section.getSequence(),
                section.getItemStatus(),
                section.getSourceText(),
                section.getReason(),
                section.getConfidence(),
                stringList(section.getEvidenceJson()),
                integerList(section.getSourceBlockReferencesJson())
            ))
            .toList();
        var missingFields = missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId())
            .stream()
            .map(field -> new StructuredMissingFieldResponse(
                field.getId(),
                field.getFieldCode(),
                field.getSectionCode(),
                field.getLabel(),
                field.getRequired(),
                field.getStatus(),
                field.getReason(),
                field.getPriority(),
                field.getUserValueJson(),
                field.getVersion()
            ))
            .toList();
        return new StructuredPlanResponse(
            plan.getId(),
            plan.getProject().getId(),
            plan.getSourceDocumentVersion().getId(),
            plan.getVersionNumber(),
            plan.getStatus(),
            plan.getCompletionRate(),
            plan.getVersion(),
            plan.getConfirmedAt(),
            plan.getConfirmedBy() == null ? null : plan.getConfirmedBy().getId(),
            sections,
            missingFields,
            plan.getCreatedAt(),
            plan.getParserVersion(),
            plan.getPromptVersion(),
            plan.getModelName(),
            plan.getProvider()
        );
    }

    private void requireOwnedProject(Long userId, Long projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        if (userId == null || !project.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private List<String> stringList(String json) {
        return readList(json, new TypeReference<List<String>>() {});
    }

    private List<Integer> integerList(String json) {
        return readList(json, new TypeReference<List<Integer>>() {});
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored structured plan metadata is invalid", exception);
        }
    }
}
