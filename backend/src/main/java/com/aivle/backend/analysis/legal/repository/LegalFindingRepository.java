package com.aivle.backend.analysis.legal.repository;

import com.aivle.backend.analysis.legal.entity.LegalFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LegalFindingRepository extends JpaRepository<LegalFinding, Long> {
    List<LegalFinding> findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(Long reviewId);
}
