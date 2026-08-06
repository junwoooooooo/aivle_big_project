package com.aivle.backend.journey.boundary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoundaryRuleRepository extends JpaRepository<BoundaryRule, Long> {
    List<BoundaryRule> findByBoundaryVersionIdAndDeletedAtIsNullOrderByRuleKey(Long boundaryVersionId);
    List<BoundaryRule> findByBoundaryVersionIdAndDeletedAtIsNull(Long boundaryVersionId);
}
