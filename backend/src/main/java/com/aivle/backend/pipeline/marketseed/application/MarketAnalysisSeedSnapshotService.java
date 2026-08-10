package com.aivle.backend.pipeline.marketseed.application;

import static com.aivle.backend.pipeline.marketseed.api.MarketAnalysisSeedApiModels.SnapshotView;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalEvidenceLinkRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.pipeline.selection.domain.ConceptHypothesisDecision;
import com.aivle.backend.pipeline.selection.domain.HypothesisLegalImpact;
import com.aivle.backend.pipeline.selection.domain.HypothesisLegalReviewStatus;
import com.aivle.backend.pipeline.selection.domain.HypothesisType;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MarketAnalysisSeedSnapshotService {
    private final ProjectRepository projects;
    private final ConceptSelectionRepository selections;
    private final ConceptHypothesisDecisionRepository decisions;
    private final ConceptRepository concepts;
    private final IdeaBriefRepository briefs;
    private final IdeaBriefFieldRepository briefFields;
    private final ConceptLegalAssessmentRepository assessments;
    private final ConceptLegalEvidenceLinkRepository evidenceLinks;
    private final MarketAnalysisSeedSnapshotRepository snapshots;
    private final MarketAnalysisSeedSnapshotFactory factory;
    private final SnapshotHasher hasher;
    private final ObjectMapper mapper;

    @Transactional
    public SnapshotView finalizeSnapshot(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        var selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        var existing = snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId);
        if (existing.isPresent()) return view(existing.get());
        var concept = concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(selection.getConceptId(), projectId)
            .filter(value -> value.getLegalStatus().isPubliclyEligible())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        List<ConceptHypothesisDecision> latest = latest(selection.getId());
        requireFinalDecisions(latest);
        var brief = briefs.findByIdAndProjectIdAndDeletedAtIsNull(concept.getSourceIdeaBriefSnapshotId(), projectId)
            .filter(value -> value.getStatus() == IdeaBriefStatus.CONFIRMED)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "확정된 원본 Idea Brief를 찾을 수 없습니다."));
        var legal = assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull(concept.getId(), projectId)
            .filter(value -> value.getStatus().isPubliclyEligible())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        String snapshotId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var body = factory.create(snapshotId, now, selection, concept, brief,
            briefFields.findAllByBriefIdOrderById(brief.getId()), latest, legal,
            evidenceLinks.findAllByAssessmentIdAndProjectIdAndDeletedAtIsNull(legal.getId(), projectId));
        String hash = hasher.hash(body);
        body.put("hash", hash);
        MarketAnalysisSeedSnapshot saved = snapshots.save(MarketAnalysisSeedSnapshot.create(snapshotId, projectId,
            selection.getId(), concept.getId(), MarketAnalysisSeedSnapshotFactory.SCHEMA_VERSION,
            concept.getSourceSnapshotHash(), hash, mapper.writeValueAsString(body), ownerId, now));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public SnapshotView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        var selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        return snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId)
            .map(this::view).orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
    }

    private List<ConceptHypothesisDecision> latest(Long selectionId) {
        Map<HypothesisType, ConceptHypothesisDecision> values = new EnumMap<>(HypothesisType.class);
        decisions.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(selectionId)
            .forEach(value -> values.putIfAbsent(value.getHypothesisType(), value));
        List<ConceptHypothesisDecision> result = new ArrayList<>();
        for (HypothesisType type : HypothesisType.values()) if (values.containsKey(type)) result.add(values.get(type));
        return result;
    }

    private void requireFinalDecisions(List<ConceptHypothesisDecision> values) {
        boolean complete = values.size() == HypothesisType.values().length && values.stream().allMatch(value ->
            value.accepted() && value.getFinalValueJson() != null
            && !(value.getLegalImpact() == HypothesisLegalImpact.LEGAL_SENSITIVE
                && (value.getLegalReviewStatus() == HypothesisLegalReviewStatus.FAILED
                    || value.getLegalReviewStatus() == HypothesisLegalReviewStatus.PENDING)));
        if (!complete) throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE);
    }

    private SnapshotView view(MarketAnalysisSeedSnapshot value) {
        return new SnapshotView(MarketAnalysisSeedSnapshotFactory.CONTRACT, value.getId(), value.getSchemaVersion(),
            value.getProjectId(), value.getSelectionId(), value.getConceptId(), value.getSourceSnapshotHash(),
            value.getSnapshotHash(), value.getFinalizedAt(), mapper.readTree(value.getSnapshotJson()));
    }
    private void requireOwnedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId).filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
