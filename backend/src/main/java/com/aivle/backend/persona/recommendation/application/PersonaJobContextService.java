package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.analysis.feasibility.repository.*;
import com.aivle.backend.common.entity.StructuredPlanStatus;
import com.aivle.backend.document.repository.*;
import com.aivle.backend.document.structure.BusinessPlanSectionCode;
import com.aivle.backend.integration.ai.persona.PersonaRecommendationAiRequest;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.*;
import com.aivle.backend.persona.catalog.BaselinePersonaCatalog;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.recommendation.PersonaRecommendationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PersonaJobContextService {
    private static final Set<BusinessPlanSectionCode> INPUT_SECTIONS = Set.of(
        BusinessPlanSectionCode.TARGET_CUSTOMER,
        BusinessPlanSectionCode.MARKET_SIZE,
        BusinessPlanSectionCode.PRODUCT_SERVICE,
        BusinessPlanSectionCode.BUSINESS_MODEL,
        BusinessPlanSectionCode.COMPETITIVE_ANALYSIS,
        BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS);

    private final AnalysisJobRepository jobs;
    private final StructuredPlanSectionRepository sections;
    private final MissingFieldRepository missingFields;
    private final FeasibilityDimensionResultRepository dimensions;
    private final FeasibilityValidationTaskRepository validationTasks;
    private final BaselinePersonaRepository personas;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PersonaJobContext load(JobClaim claim) {
        var job = jobs.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> invalid("PERSONA_JOB_NOT_FOUND"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())
            || job.getSourceStructuredPlan() == null
            || job.getSourceFeasibilityAssessment() == null) {
            throw invalid("PERSONA_JOB_CLAIM_LOST");
        }
        var plan = job.getSourceStructuredPlan();
        var feasibility = job.getSourceFeasibilityAssessment();
        if (!plan.getProject().getId().equals(job.getProject().getId())
            || !feasibility.getProject().getId().equals(job.getProject().getId())
            || !feasibility.getStructuredPlan().getId().equals(plan.getId())
            || plan.getStatus() != StructuredPlanStatus.CONFIRMED
            || plan.getCompletionRate() != 100
            || plan.getSourceDocumentVersion() == null) {
            throw invalid("PERSONA_INPUT_SNAPSHOT_INVALID");
        }
        Map<BusinessPlanSectionCode, String> completionSources = new EnumMap<>(
            BusinessPlanSectionCode.class);
        missingFields.findAllByStructuredPlanIdAndDeletedAtIsNullOrderById(plan.getId())
            .forEach(item -> completionSources.put(item.getSectionCode(),
                item.getStatus().name()));
        var sectionInputs = sections
            .findAllByStructuredPlanIdAndDeletedAtIsNullOrderBySequence(plan.getId()).stream()
            .filter(item -> INPUT_SECTIONS.contains(item.getSectionCode()))
            .map(item -> new PersonaRecommendationAiRequest.Section(
                item.getSectionCode().name(), item.getSourceText(),
                item.getItemStatus().name(),
                completionSources.getOrDefault(item.getSectionCode(), "DOCUMENT")))
            .toList();
        var dimensionInputs = dimensions
            .findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(
                feasibility.getId()).stream()
            .filter(item -> Set.of(
                "TARGET_CUSTOMER", "MARKET_ATTRACTIVENESS",
                "PRODUCT_SOLUTION_FIT", "GO_TO_MARKET")
                .contains(item.getDimensionCode().name()))
            .map(item -> new PersonaRecommendationAiRequest.Dimension(
                item.getDimensionCode().name(), item.getScore(),
                item.getConfidence().name(), item.getStatus().name(),
                item.getFinding(), item.getEvidenceJson()))
            .toList();
        var taskInputs = validationTasks
            .findByFeasibilityAssessmentIdAndDeletedAtIsNullOrderByDisplayOrder(
                feasibility.getId()).stream()
            .map(item -> new PersonaRecommendationAiRequest.ValidationTask(
                item.getId(), item.getTaskCode(), item.getDimensionCode().name(),
                item.getTitle(), item.getReason(), item.getPriority().name(),
                item.getValidationMethod(), item.getExpectedEvidence()))
            .toList();
        var catalogInputs = personas
            .findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
                BaselinePersonaCatalog.VERSION).stream()
            .map(item -> new PersonaRecommendationAiRequest.BaselinePersona(
                item.getPersonaCode(), item.getClusterId(), item.getDisplayName(),
                item.getAgeGroup(), item.getGender(), item.getSampleSize(),
                item.getWeightedShare(), item.getDataSource(), item.getDataVersion(),
                item.getCatalogVersion(), item.getKeyTraitsJson(),
                item.getDemographicSummaryJson(), item.getEvidenceMetricsJson(),
                item.getLimitationsJson()))
            .toList();
        if (catalogInputs.size() != BaselinePersonaCatalog.EXPECTED_PERSONA_COUNT) {
            throw invalid("PERSONA_CATALOG_UNAVAILABLE");
        }
        var request = new PersonaRecommendationAiRequest(
            job.getProject().getId(), plan.getId(), feasibility.getId(),
            plan.getSourceDocumentVersion().getId(),
            PersonaRecommendationPolicy.PROMPT_VERSION, BaselinePersonaCatalog.VERSION,
            PersonaRecommendationPolicy.PROMPT, sectionInputs,
            new PersonaRecommendationAiRequest.FeasibilityContext(
                feasibility.getVerdict().name(), feasibility.getOverallScore(),
                feasibility.getConfidence().name(), dimensionInputs, taskInputs,
                feasibility.getLegalReview().getRiskLevel().name()),
            catalogInputs);
        try {
            String snapshot = objectMapper.writeValueAsString(request);
            return new PersonaJobContext(request, snapshot, sha256(snapshot));
        } catch (JacksonException exception) {
            throw invalid("PERSONA_INPUT_SERIALIZATION_FAILED");
        }
    }

    private JobProcessingException invalid(String code) {
        return JobProcessingException.nonRetryable(
            code, "Persona 추천 입력을 안전하게 구성할 수 없습니다.", null);
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
