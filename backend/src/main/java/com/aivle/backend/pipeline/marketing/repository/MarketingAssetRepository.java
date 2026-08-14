package com.aivle.backend.pipeline.marketing.repository;

import com.aivle.backend.pipeline.marketing.domain.MarketingAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingAssetRepository extends JpaRepository<MarketingAsset, String> {
    List<MarketingAsset> findAllByContentIdAndDeletedAtIsNull(String contentId);
    List<MarketingAsset> findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(String contentId);
}
