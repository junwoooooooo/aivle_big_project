package com.aivle.backend.journey;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface DetailedAnalysisRunRepository extends JpaRepository<DetailedAnalysisRun, Long> {
    @EntityGraph(attributePaths={"project","ideaVersion","taskRun"})
    Optional<DetailedAnalysisRun> findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId, Long ideaVersionId);
}
