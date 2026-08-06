package com.aivle.backend.journey;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface QuickAssessmentRunRepository extends JpaRepository<QuickAssessmentRun, Long> {
    @EntityGraph(attributePaths={"project","ideaVersion","taskRun"})
    Optional<QuickAssessmentRun> findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId, Long ideaVersionId);
}
