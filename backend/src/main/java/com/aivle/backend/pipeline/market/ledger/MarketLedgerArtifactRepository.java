package com.aivle.backend.pipeline.market.ledger;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketLedgerArtifactRepository extends JpaRepository<MarketLedgerArtifact, String> {
    Optional<MarketLedgerArtifact> findByMarketTaskRunIdAndTaskAttemptIdAndStateAndDeletedAtIsNull(
        String taskRunId, String attemptId, MarketLedgerArtifact.State state);
    Optional<MarketLedgerArtifact> findByMarketResearchVersionIdAndStateAndDeletedAtIsNull(
        Long versionId, MarketLedgerArtifact.State state);
    List<MarketLedgerArtifact> findAllByMarketTaskRunIdAndStateAndDeletedAtIsNull(
        String taskRunId, MarketLedgerArtifact.State state);
}
