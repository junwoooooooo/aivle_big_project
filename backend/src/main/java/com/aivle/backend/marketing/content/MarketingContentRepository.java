package com.aivle.backend.marketing.content;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface MarketingContentRepository extends JpaRepository<MarketingContent, Long> {
    List<MarketingContent> findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long projectId);
    Optional<MarketingContent> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MarketingContent> findForUpdateByIdAndProjectIdAndDeletedAtIsNull(
        Long id, Long projectId);
}
