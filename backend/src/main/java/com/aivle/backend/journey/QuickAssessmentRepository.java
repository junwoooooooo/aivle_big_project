package com.aivle.backend.journey;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface QuickAssessmentRepository extends JpaRepository<QuickAssessment, Long> {
    @EntityGraph(attributePaths={"conceptVersion","conceptVersion.concept"})
    List<QuickAssessment> findByRunIdAndDeletedAtIsNullOrderByOverallScoreDesc(Long runId);
}
