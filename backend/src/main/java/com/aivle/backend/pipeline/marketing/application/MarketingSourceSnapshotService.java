package com.aivle.backend.pipeline.marketing.application;

import static com.aivle.backend.pipeline.marketing.api.MarketingSourceApiModels.SnapshotView;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MarketingSourceSnapshotService {
    private final ProjectRepository projects;
    private final CurrentConceptSourceResolver currentConcepts;
    private final MarketingSourceSnapshotRepository sources;
    private final ConceptPortfolioConceptRepository portfolioConcepts;
    private final MarketingSourceSnapshotFactory factory;
    private final ObjectMapper mapper;

    @Transactional
    public SnapshotView finalizeSnapshot(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        Source current = currentConcepts.require(projectId,
            "현재 확정된 사업안으로 마케팅 초안을 만들 수 없습니다.");
        var marketSeed = current.seed();
        var existing = exact(current, projectId);
        if (existing.isPresent()) return view(existing.get());
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        var concept = portfolioConcepts.findByIdAndProjectIdAndDeletedAtIsNull(
                current.selection().getConceptId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        var built = factory.create(id, now, marketSeed, concept,
            current.selection().getHypothesisRevision(), current.bm());
        MarketingSourceSnapshot saved = sources.save(MarketingSourceSnapshot.createPortfolio(
            id, projectId, marketSeed.getId(), current.selection().getId(), current.selection().getConceptId(),
            current.selection().getHypothesisRevision(), current.bm().revision(),
            MarketingSourceSnapshotFactory.SCHEMA_VERSION, built.hash(),
            mapper.writeValueAsString(built.body()), ownerId, now));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public SnapshotView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return view(requireCurrent(projectId));
    }

    @Transactional(readOnly = true)
    public MarketingSourceSnapshot requireCurrent(Long projectId) {
        Source current = currentConcepts.require(projectId,
            "현재 확정된 사업안으로 마케팅 초안을 만들 수 없습니다.");
        return exact(current, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_SOURCE_UNAVAILABLE,
                "Marketing Source Snapshot을 먼저 확정해 주세요."));
    }

    @Transactional(readOnly = true)
    public MarketingSourceSnapshot findCurrent(Long projectId) {
        try { return requireCurrent(projectId); }
        catch (BusinessException missing) { return null; }
    }

    private java.util.Optional<MarketingSourceSnapshot> exact(Source source, Long projectId) {
        return sources.findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
            source.seed().getId(), source.selection().getHypothesisRevision(), source.bm().revision(), projectId);
    }
    private SnapshotView view(MarketingSourceSnapshot value) {
        boolean portfolio = "CONCEPT_PORTFOLIO_V2".equals(value.getSourceType());
        return new SnapshotView(MarketingSourceSnapshotFactory.CONTRACT, value.getId(), value.getSchemaVersion(),
            value.getProjectId(), portfolio ? value.getPortfolioSelectionId() : value.getSelectionId(),
            portfolio ? value.getPortfolioConceptId() : value.getConceptId(), value.getSourceSelectionRevision(),
            value.getSourceBmPlanRevision(), value.getSourceMarketSeedSnapshotId(),
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
