package com.aivle.backend.pipeline.marketing.repository;

import com.aivle.backend.pipeline.marketing.domain.MarketingContentRevision;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingContentRevisionRepository extends JpaRepository<MarketingContentRevision, String> {
    List<MarketingContentRevision> findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc(String contentId);
    Optional<MarketingContentRevision> findFirstByContentIdAndDeletedAtIsNullOrderByRevisionNumberDesc(String contentId);
    Optional<MarketingContentRevision> findByContentIdAndRevisionNumberAndDeletedAtIsNull(String contentId, int revisionNumber);
}
