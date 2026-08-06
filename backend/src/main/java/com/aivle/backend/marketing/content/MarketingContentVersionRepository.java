package com.aivle.backend.marketing.content;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingContentVersionRepository
    extends JpaRepository<MarketingContentVersion, Long> {
    List<MarketingContentVersion> findAllByMarketingContentIdOrderByVersionNumberDesc(Long contentId);
    Optional<MarketingContentVersion> findByMarketingContentIdAndVersionNumber(
        Long contentId,
        int versionNumber
    );
    Optional<MarketingContentVersion> findByIdAndMarketingContentId(
        Long id,
        Long contentId
    );
    Optional<MarketingContentVersion> findByAnalysisJobId(Long jobId);
}
