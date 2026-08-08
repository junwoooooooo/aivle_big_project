package com.aivle.backend.pipeline.marketing.application;

import static com.aivle.backend.pipeline.marketing.api.MarketingSourceApiModels.SnapshotView;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
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
    private final ConceptSelectionRepository selections;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final MarketingSourceSnapshotRepository sources;
    private final ConceptRepository concepts;
    private final MarketingSourceSnapshotFactory factory;
    private final ObjectMapper mapper;

    @Transactional
    public SnapshotView finalizeSnapshot(Long ownerId, Long projectId) {
        requireOwnedForUpdate(ownerId, projectId);
        var marketSeed = currentMarketSeed(projectId);
        var existing = sources.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(marketSeed.getId(), projectId);
        if (existing.isPresent()) return view(existing.get());
        var concept = concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(marketSeed.getConceptId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        var built = factory.create(id, now, marketSeed, concept);
        var saved = sources.save(MarketingSourceSnapshot.create(id, projectId, marketSeed.getId(),
            marketSeed.getSelectionId(), marketSeed.getConceptId(), MarketingSourceSnapshotFactory.SCHEMA_VERSION,
            built.hash(), mapper.writeValueAsString(built.body()), ownerId, now));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public SnapshotView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return view(requireCurrent(projectId));
    }

    @Transactional(readOnly = true)
    public MarketingSourceSnapshot requireCurrent(Long projectId) {
        var marketSeed = currentMarketSeed(projectId);
        return sources.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(marketSeed.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_SOURCE_UNAVAILABLE,
                "Marketing Source Snapshot을 먼저 확정해 주세요."));
    }

    @Transactional(readOnly = true)
    public MarketingSourceSnapshot findCurrent(Long projectId) {
        try { return requireCurrent(projectId); }
        catch (BusinessException missing) { return null; }
    }

    private com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot currentMarketSeed(Long projectId) {
        var selection = selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_SELECTION_REQUIRED));
        return marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(selection.getId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
    }
    private SnapshotView view(MarketingSourceSnapshot value) {
        return new SnapshotView(MarketingSourceSnapshotFactory.CONTRACT, value.getId(), value.getSchemaVersion(),
            value.getProjectId(), value.getSelectionId(), value.getConceptId(), value.getSourceMarketSeedSnapshotId(),
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
