package com.aivle.backend.journey;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface DetailedAnalysisRepository extends JpaRepository<DetailedAnalysis, Long> {
    @EntityGraph(attributePaths={"conceptVersion","conceptVersion.concept"})
    List<DetailedAnalysis> findByRunIdAndDeletedAtIsNullOrderById(Long runId);
}
