package com.aivle.backend.journey;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface JourneyFinancialAnalysisRepository extends JpaRepository<JourneyFinancialAnalysis, Long> {
    @EntityGraph(attributePaths={"conceptVersion","conceptVersion.concept"})
    List<JourneyFinancialAnalysis> findByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderById(Long projectId, Long ideaVersionId);
}
