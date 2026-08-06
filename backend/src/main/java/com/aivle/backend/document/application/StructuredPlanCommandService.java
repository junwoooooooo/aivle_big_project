package com.aivle.backend.document.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.common.entity.MissingFieldStatus;
import com.aivle.backend.common.entity.ProjectStage;
import com.aivle.backend.common.entity.StructuredPlanStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.dto.request.UpdateMissingFieldRequest;
import com.aivle.backend.document.dto.response.StructuredMissingFieldResponse;
import com.aivle.backend.document.dto.response.StructuredPlanResponse;
import com.aivle.backend.document.entity.MissingField;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.document.repository.MissingFieldRepository;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.document.repository.StructuredPlanSectionRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StructuredPlanCommandService {
    private final ProjectRepository projectRepository;
    private final StructuredPlanRepository planRepository;
    private final StructuredPlanSectionRepository sectionRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final UserRepository userRepository;
    private final StructuredPlanCompletionService completionService;
    private final StructuredPlanQueryService queryService;
    private final DomainAuditService auditService;
    private final Clock jobClock;
    private final ServicePolicyService servicePolicy;

    @Transactional
    public StructuredMissingFieldResponse updateMissingField(
        Long userId,
        Long projectId,
        Long planId,
        Long fieldId,
        UpdateMissingFieldRequest request,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        requireOwnedProject(userId, projectId);
        StructuredPlan plan = requirePlan(projectId, planId);
        if (!plan.isEditable()) {
            throw new BusinessException(ErrorCode.PLAN_NOT_EDITABLE);
        }
        MissingField field = missingFieldRepository
            .findByIdAndStructuredPlanIdAndDeletedAtIsNull(fieldId, planId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.MISSING_FIELD_NOT_FOUND));
        requireVersion(field.getVersion(), request.version());

        MissingFieldStatus previousStatus = field.getStatus();
        if (request.status() == MissingFieldStatus.FILLED) {
            field.fill(requireUserText(request.value(), 4000));
        } else if (request.status() == MissingFieldStatus.WAIVED) {
            field.waive(requireUserText(request.reason(), 500));
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        completionService.recalculate(
            plan,
            sectionRepository
                .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(planId),
            missingFieldRepository
                .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(planId)
        );
        missingFieldRepository.flush();
        auditService.record(
            userId,
            projectId,
            request.status() == MissingFieldStatus.FILLED
                ? AuditEventType.MISSING_FIELD_FILLED
                : AuditEventType.MISSING_FIELD_WAIVED,
            "MISSING_FIELD",
            fieldId,
            requestId,
            Map.of(
                "fieldCode", field.getFieldCode(),
                "previousStatus", previousStatus.name(),
                "newStatus", field.getStatus().name()
            )
        );
        return toResponse(field);
    }

    @Transactional
    public StructuredPlanResponse confirm(
        Long userId,
        Long projectId,
        Long planId,
        Long version,
        String requestId
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Project project = requireOwnedProject(userId, projectId);
        StructuredPlan plan = requirePlan(projectId, planId);
        if (plan.getStatus() == StructuredPlanStatus.CONFIRMED) {
            requireVersion(plan.getVersion(), version);
            return queryService.findById(userId, projectId, planId);
        }
        requireVersion(plan.getVersion(), version);
        boolean hasOpenRequired = missingFieldRepository
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(planId)
            .stream()
            .anyMatch(field ->
                field.getRequired()
                    && field.getStatus() == MissingFieldStatus.OPEN);
        if (plan.getStatus() != StructuredPlanStatus.DRAFT
            || plan.getCompletionRate() != 100
            || hasOpenRequired
            || plan.getSourceDocumentVersion().isDeleted()) {
            throw new BusinessException(ErrorCode.PLAN_INCOMPLETE);
        }
        if (project.getStage() != ProjectStage.STRUCTURING) {
            throw new BusinessException(ErrorCode.PROJECT_STAGE_INVALID);
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        plan.confirm(user, LocalDateTime.now(jobClock));
        project.enterLegalReview();
        planRepository.flush();
        auditService.record(
            userId,
            projectId,
            AuditEventType.STRUCTURED_PLAN_CONFIRMED,
            "STRUCTURED_PLAN",
            planId,
            requestId,
            Map.of(
                "status", StructuredPlanStatus.CONFIRMED.name(),
                "newStatus", ProjectStage.LEGAL_REVIEW.name()
            )
        );
        return queryService.findById(userId, projectId, planId);
    }

    private Project requireOwnedProject(Long userId, Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
            .filter(project -> project.getOwner().getId().equals(userId))
            .orElseThrow(() ->
                new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private StructuredPlan requirePlan(Long projectId, Long planId) {
        return planRepository
            .findByIdAndProjectIdAndDeletedAtIsNull(planId, projectId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.STRUCTURED_PLAN_NOT_FOUND));
    }

    private void requireVersion(Long current, Long requested) {
        if (!current.equals(requested)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
    }

    private String requireUserText(String value, int maxLength) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String trimmed = value.trim();
        boolean containsControl = trimmed.codePoints()
            .anyMatch(Character::isISOControl);
        if (trimmed.isEmpty()
            || trimmed.length() > maxLength
            || containsControl) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return value;
    }

    private StructuredMissingFieldResponse toResponse(MissingField field) {
        return new StructuredMissingFieldResponse(
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
        );
    }
}
