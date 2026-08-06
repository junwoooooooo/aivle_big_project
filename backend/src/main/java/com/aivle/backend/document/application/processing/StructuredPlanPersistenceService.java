package com.aivle.backend.document.application.processing;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.common.entity.PlanSectionType;
import com.aivle.backend.common.entity.StructuredPlanStatus;
import com.aivle.backend.document.entity.*;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.repository.*;
import com.aivle.backend.document.structure.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.job.runner.JobClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StructuredPlanPersistenceService {
    private static final String RESULT_REFERENCE_TYPE = "STRUCTURED_PLAN";

    private final AnalysisJobRepository jobRepository;
    private final StructuredPlanRepository planRepository;
    private final StructuredPlanSectionRepository sectionRepository;
    private final MissingFieldRepository missingFieldRepository;
    private final BusinessPlanSectionCatalog catalog;
    private final ObjectMapper objectMapper;
    private final Clock jobClock;

    @Transactional
    public Long complete(
        JobClaim claim,
        ParsedDocument parsed,
        AiStructuredPlanResult aiResult,
        StructuredPlanMappingResult mapping
    ) {
        if (!mapping.mappingErrors().isEmpty()) {
            throw new IllegalArgumentException("mapping result contains validation errors");
        }
        AnalysisJob job = jobRepository.findByIdForUpdate(claim.jobId())
            .orElseThrow(() -> new IllegalStateException("job does not exist"));
        if (!job.hasCurrentClaim(claim.claimToken(), claim.attempt())) {
            throw new IllegalStateException("job claim is no longer current");
        }
        DocumentVersion sourceVersion = job.getSourceDocumentVersion();
        var existing = planRepository.findBySourceDocumentVersionIdAndDeletedAtIsNull(
            sourceVersion.getId()
        );
        StructuredPlan plan;
        if (existing.isPresent()) {
            plan = existing.get();
        } else {
            plan = planRepository.save(StructuredPlan.create(
                job.getProject(),
                sourceVersion,
                parsed,
                aiResult,
                mapping,
                processingSummary(mapping)
            ));
            saveSections(plan, mapping.sectionDrafts());
            missingFieldRepository.saveAll(
                mapping.missingFieldDrafts().stream()
                    .map(draft -> MissingField.create(plan, draft))
                    .toList()
            );
        }

        JobStatus completionStatus = plan.getStatus() == StructuredPlanStatus.DRAFT
            ? JobStatus.SUCCEEDED
            : JobStatus.PARTIAL;
        sourceVersion.completeProcessing(completionStatus);
        job.getProject().enterStructuring();
        job.complete(
            claim.claimToken(),
            claim.attempt(),
            completionStatus,
            RESULT_REFERENCE_TYPE,
            plan.getId(),
            LocalDateTime.now(jobClock)
        );
        return plan.getId();
    }

    private void saveSections(
        StructuredPlan plan,
        List<StructuredPlanSectionDraft> drafts
    ) {
        if (drafts.size() != catalog.all().size()) {
            throw new IllegalArgumentException("exactly 12 structured plan sections are required");
        }
        sectionRepository.saveAll(drafts.stream()
            .map(draft -> StructuredPlanSection.create(
                plan,
                primaryType(draft.sectionCode()),
                draft,
                catalog.require(draft.sectionCode()).sequence(),
                json(draft.evidence()),
                json(draft.sourceBlockReferences())
            ))
            .toList());
    }

    private PlanSectionType primaryType(BusinessPlanSectionCode code) {
        return switch (code) {
            case BUSINESS_OVERVIEW -> PlanSectionType.OVERVIEW;
            case MARKET_SIZE -> PlanSectionType.MARKET;
            case TARGET_CUSTOMER -> PlanSectionType.TARGET_CUSTOMER;
            case COMPETITIVE_ANALYSIS -> PlanSectionType.COMPETITION;
            case PRODUCT_SERVICE -> PlanSectionType.PRODUCT_SERVICE;
            case BUSINESS_MODEL -> PlanSectionType.BUSINESS_MODEL;
            case COST_PROFITABILITY, SALES_GOALS_FINANCIAL_PROJECTIONS ->
                PlanSectionType.FINANCIAL;
            case TECHNOLOGY_PRODUCTION -> PlanSectionType.TECHNOLOGY_OPERATION;
            case LEGAL_PERMITS -> PlanSectionType.LEGAL_REGULATION;
            case SCHEDULE_RISK -> PlanSectionType.SCHEDULE;
            case EVIDENCE_LIST -> PlanSectionType.EVIDENCE;
        };
    }

    private String processingSummary(StructuredPlanMappingResult mapping) {
        var summary = new StructuredPlanProcessingSummary(
            mapping.sectionDrafts().stream()
                .map(section -> new StructuredPlanProcessingSummary.SectionStatus(
                    section.sectionCode().name(),
                    section.status().name()
                ))
                .toList(),
            mapping.warnings()
        );
        return json(summary);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("typed document metadata cannot be serialized", exception);
        }
    }
}
