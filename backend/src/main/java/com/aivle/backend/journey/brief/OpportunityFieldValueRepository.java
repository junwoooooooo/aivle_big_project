package com.aivle.backend.journey.brief;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpportunityFieldValueRepository extends JpaRepository<OpportunityFieldValue, Long> {
    List<OpportunityFieldValue> findByBriefVersionIdAndDeletedAtIsNullOrderByFieldKey(Long briefVersionId);
}
