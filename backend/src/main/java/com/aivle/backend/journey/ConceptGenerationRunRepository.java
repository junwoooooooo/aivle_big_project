package com.aivle.backend.journey;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface ConceptGenerationRunRepository extends JpaRepository<ConceptGenerationRun, Long> {
    @EntityGraph(attributePaths={"project","ideaVersion","taskRun"})
    Optional<ConceptGenerationRun> findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId, Long ideaVersionId);
    @EntityGraph(attributePaths={"project","ideaVersion","taskRun"})
    Optional<ConceptGenerationRun> findTopByProjectIdAndIdeaVersionIdAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId, Long ideaVersionId, ConceptAiRunBase.State state);
}
