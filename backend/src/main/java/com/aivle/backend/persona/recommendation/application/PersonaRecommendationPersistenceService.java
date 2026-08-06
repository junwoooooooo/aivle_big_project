package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.analysis.feasibility.repository.FeasibilityValidationTaskRepository;
import com.aivle.backend.audit.*;
import com.aivle.backend.common.entity.*;
import com.aivle.backend.integration.ai.persona.PersonaRecommendationAiResponse;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import com.aivle.backend.persona.catalog.*;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.recommendation.PersonaRecommendationPolicy;
import com.aivle.backend.persona.recommendation.entity.*;
import com.aivle.backend.persona.recommendation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Service
@RequiredArgsConstructor
public class PersonaRecommendationPersistenceService {
    private final AnalysisJobRepository jobs;
    private final PersonaRecommendationRepository recommendations;
    private final PersonaRecommendationItemRepository items;
    private final CustomerHypothesisRepository hypotheses;
    private final CustomerValidationPlanRepository validationPlans;
    private final PersonaValidationTaskLinkRepository taskLinks;
    private final FeasibilityValidationTaskRepository feasibilityTasks;
    private final BaselinePersonaRepository personas;
    private final PersonaFitScorePolicy scorePolicy;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;
    private final DomainAuditService audit;

    @Transactional
    public Long complete(
        JobClaim claim, PersonaJobContext context, PersonaRecommendationAiResponse result
    ) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        var plan = job.getSourceStructuredPlan();
        var feasibility = job.getSourceFeasibilityAssessment();
        Map<String, BaselinePersona> catalog = personas
            .findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
                BaselinePersonaCatalog.VERSION).stream()
            .collect(java.util.stream.Collectors.toMap(
                BaselinePersona::getPersonaCode, item -> item));
        Map<Long, com.aivle.backend.analysis.feasibility.entity.FeasibilityValidationTask>
            availableTasks = feasibilityTasks
                .findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(
                    feasibility.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                    com.aivle.backend.analysis.feasibility.entity.FeasibilityValidationTask::getId,
                    item -> item));
        validate(result, catalog.keySet(), availableTasks.keySet());
        var existing = recommendations
            .findByStructuredPlanIdAndFeasibilityAssessmentIdAndPromptVersionAndCatalogVersionAndDeletedAtIsNull(
                plan.getId(), feasibility.getId(),
                PersonaRecommendationPolicy.PROMPT_VERSION, BaselinePersonaCatalog.VERSION);
        PersonaRecommendation recommendation;
        if (existing.isPresent()) {
            recommendation = existing.get();
        } else {
            var outcome = scorePolicy.evaluate(result.items());
            LocalDateTime now = LocalDateTime.now(jobClock);
            String canonical = json(result);
            recommendation = recommendations.save(PersonaRecommendation.completed(
                job.getProject(), job, plan, feasibility,
                RecommendationStatus.NEEDS_VALIDATION,
                outcome.primaryPersonaCode(), outcome.secondaryPersonaCode(),
                outcome.confidence(), result.summary(),
                PersonaRecommendationPolicy.DISCLAIMER,
                result.provider(), result.model(),
                PersonaRecommendationPolicy.PROMPT_VERSION,
                BaselinePersonaCatalog.VERSION,
                sha256(PersonaRecommendationPolicy.PROMPT), context.inputHash(),
                sha256(canonical), context.snapshotJson(), now));
            for (var item : result.items()) {
                items.save(PersonaRecommendationItem.create(
                    recommendation, catalog.get(item.personaCode()), item.rank(),
                    outcome.levels().get(item.personaCode()), item.fitScore(),
                    item.confidence(), json(item.matchReasons()), json(item.mismatchRisks()),
                    json(item.assumptions()), json(item.evidence()),
                    json(item.verificationQuestions()), item.interpretation()));
            }
            for (var hypothesis : result.hypotheses()) {
                hypotheses.save(CustomerHypothesis.open(
                    recommendation, hypothesis.personaCode(), hypothesis.type(),
                    hypothesis.statement(), hypothesis.rationale(), hypothesis.sourceType(),
                    hypothesis.sourceReference(), hypothesis.confidence(),
                    hypothesis.priority()));
            }
            Set<Long> linkedTaskIds = new LinkedHashSet<>();
            for (var validationPlan : result.validationPlans()) {
                validationPlans.save(CustomerValidationPlan.draft(
                    recommendation, validationPlan.personaCode(), validationPlan.method(),
                    validationPlan.objective(),
                    validationPlan.targetParticipantDescription(),
                    validationPlan.suggestedSampleSize(),
                    validationPlan.recruitmentChannel(),
                    json(validationPlan.successCriteria()),
                    json(validationPlan.expectedEvidence()),
                    json(validationPlan.interviewQuestions()),
                    json(validationPlan.surveyQuestions()),
                    json(validationPlan.linkedFeasibilityTaskIds()),
                    validationPlan.priority(), PersonaRecommendationPolicy.DISCLAIMER));
                linkedTaskIds.addAll(validationPlan.linkedFeasibilityTaskIds());
            }
            for (Long taskId : linkedTaskIds) {
                taskLinks.save(PersonaValidationTaskLink.create(
                    recommendation, availableTasks.get(taskId)));
            }
        }
        job.complete(claim.claimToken(), claim.attempt(), JobStatus.SUCCEEDED,
            "PERSONA_RECOMMENDATION", recommendation.getId(),
            LocalDateTime.now(jobClock));
        audit.record(job.getProject().getOwner().getId(), job.getProject().getId(),
            AuditEventType.PERSONA_RECOMMENDATION_COMPLETED,
            "PersonaRecommendation", recommendation.getId(), null,
            Map.of("recommendationId", recommendation.getId().toString(),
                "structuredPlanId", plan.getId().toString(),
                "feasibilityAssessmentId", feasibility.getId().toString(),
                "jobId", job.getId().toString(),
                "primaryPersonaCode",
                    Optional.ofNullable(recommendation.getPrimaryPersonaCode()).orElse("NONE")));
        return recommendation.getId();
    }

    private void validate(
        PersonaRecommendationAiResponse result,
        Set<String> allowedCodes,
        Set<Long> allowedTaskIds
    ) {
        if (result == null || blank(result.provider()) || blank(result.model())
            || blank(result.summary()) || result.confidence() == null
            || result.items() == null || result.hypotheses() == null
            || result.validationPlans() == null) {
            throw new IllegalArgumentException("persona result contract is invalid");
        }
        scorePolicy.evaluate(result.items());
        Set<String> itemCodes = new HashSet<>();
        for (var item : result.items()) {
            if (!allowedCodes.contains(item.personaCode()) || !itemCodes.add(item.personaCode())
                || item.matchReasons() == null || item.mismatchRisks() == null
                || item.assumptions() == null || item.evidence() == null
                || item.verificationQuestions() == null || blank(item.interpretation())) {
                throw new IllegalArgumentException("persona item is invalid");
            }
        }
        for (var hypothesis : result.hypotheses()) {
            if (hypothesis == null || !itemCodes.contains(hypothesis.personaCode())
                || hypothesis.type() == null || blank(hypothesis.statement())
                || hypothesis.sourceType() == null || hypothesis.confidence() == null
                || hypothesis.priority() == null) {
                throw new IllegalArgumentException("customer hypothesis is invalid");
            }
        }
        Set<String> planCodes = new HashSet<>();
        for (var plan : result.validationPlans()) {
            if (plan == null || !itemCodes.contains(plan.personaCode())
                || !planCodes.add(plan.personaCode()) || plan.method() == null
                || plan.successCriteria() == null || plan.expectedEvidence() == null
                || plan.interviewQuestions() == null || plan.interviewQuestions().size() < 5
                || plan.interviewQuestions().size() > 10 || plan.surveyQuestions() == null
                || plan.linkedFeasibilityTaskIds() == null
                || !allowedTaskIds.containsAll(plan.linkedFeasibilityTaskIds())
                || plan.priority() == null) {
                throw new IllegalArgumentException("customer validation plan is invalid");
            }
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("persona result serialization failed", exception);
        }
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
