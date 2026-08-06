package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.analysis.feasibility.repository.FeasibilityAssessmentRepository;
import com.aivle.backend.audit.*;
import com.aivle.backend.common.entity.*;
import com.aivle.backend.common.exception.*;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.recommendation.PersonaRecommendationPolicy;
import com.aivle.backend.persona.recommendation.dto.PersonaStartResponse;
import com.aivle.backend.persona.recommendation.repository.PersonaRecommendationRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PersonaRecommendationCommandService {
    private final ProjectRepository projects;
    private final StructuredPlanRepository plans;
    private final FeasibilityAssessmentRepository feasibilityAssessments;
    private final BaselinePersonaRepository personas;
    private final PersonaRecommendationRepository recommendations;
    private final AnalysisJobRepository jobs;
    private final ApplicationEventPublisher events;
    private final DomainAuditService audit;
    private final ServicePolicyService servicePolicy;

    @Transactional
    public PersonaStartResponse start(Long userId, Long projectId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        servicePolicy.requireDocumentProcessingEnabled();
        var project = projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        var plan = plans
            .findTopByProjectIdAndStatusAndCompletionRateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, StructuredPlanStatus.CONFIRMED, 100)
            .orElseThrow(() -> new BusinessException(ErrorCode.PERSONA_INPUT_INVALID));
        var feasibility = feasibilityAssessments
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PERSONA_INPUT_INVALID));
        if (!feasibility.getStructuredPlan().getId().equals(plan.getId())
            || plan.getSourceDocumentVersion() == null
            || personas.countByCatalogVersionAndDeletedAtIsNull(
                BaselinePersonaCatalog.VERSION) != BaselinePersonaCatalog.EXPECTED_PERSONA_COUNT) {
            throw new BusinessException(ErrorCode.PERSONA_INPUT_INVALID);
        }
        var existing = recommendations
            .findByStructuredPlanIdAndFeasibilityAssessmentIdAndPromptVersionAndCatalogVersionAndDeletedAtIsNull(
                plan.getId(), feasibility.getId(), PersonaRecommendationPolicy.PROMPT_VERSION,
                BaselinePersonaCatalog.VERSION);
        if (existing.isPresent()) {
            var recommendation = existing.get();
            return response(projectId, recommendation.getId(),
                recommendation.getAnalysisJob(), plan.getId(), feasibility.getId());
        }
        if (project.getStage() != ProjectStage.PERSONA_CONFIGURATION) {
            throw new BusinessException(ErrorCode.PERSONA_INPUT_INVALID);
        }
        if (jobs.existsByProjectIdAndJobTypeAndStatusInAndDeletedAtIsNull(
            projectId, JobType.PERSONA_RECOMMENDATION,
            List.of(JobStatus.QUEUED, JobStatus.RUNNING))) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        String source = projectId + ":" + plan.getId() + ":" + feasibility.getId() + ":"
            + BaselinePersonaCatalog.VERSION + ":"
            + PersonaRecommendationPolicy.PROMPT_VERSION;
        String requestJson = "{\"structuredPlanId\":" + plan.getId()
            + ",\"feasibilityAssessmentId\":" + feasibility.getId()
            + ",\"catalogVersion\":\"" + BaselinePersonaCatalog.VERSION
            + "\",\"promptVersion\":\"" + PersonaRecommendationPolicy.PROMPT_VERSION + "\"}";
        AnalysisJob job = jobs.save(AnalysisJob.queuedPersonaRecommendation(
            project, plan, feasibility, requestJson,
            "persona:" + plan.getId() + ":" + feasibility.getId() + ":"
                + BaselinePersonaCatalog.VERSION + ":"
                + PersonaRecommendationPolicy.PROMPT_VERSION,
            sha256(source)));
        audit.record(userId, projectId, AuditEventType.PERSONA_RECOMMENDATION_REQUESTED,
            "AnalysisJob", job.getId(), null,
            Map.of("jobId", job.getId().toString(),
                "structuredPlanId", plan.getId().toString(),
                "feasibilityAssessmentId", feasibility.getId().toString()));
        events.publishEvent(new PersonaRecommendationRequested(job.getId()));
        return response(projectId, null, job, plan.getId(), feasibility.getId());
    }

    private PersonaStartResponse response(
        Long projectId, Long recommendationId, AnalysisJob job,
        Long planId, Long feasibilityId
    ) {
        return new PersonaStartResponse(
            projectId, recommendationId, job.getId(), job.getStatus(),
            planId, feasibilityId, BaselinePersonaCatalog.VERSION);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
