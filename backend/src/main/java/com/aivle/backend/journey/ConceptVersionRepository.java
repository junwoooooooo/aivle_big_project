package com.aivle.backend.journey;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ConceptVersionRepository extends JpaRepository<ConceptVersion, Long> {
    @EntityGraph(attributePaths={"project","ideaVersion","concept"})
    @Query("select v from ConceptVersion v where v.project.id=:projectId and v.ideaVersion.id=:ideaVersionId and v.deletedAt is null order by v.concept.displayOrder, v.versionNumber desc")
    List<ConceptVersion> findCurrentForIdea(@Param("projectId") Long projectId, @Param("ideaVersionId") Long ideaVersionId);
    @EntityGraph(attributePaths={"project","ideaVersion","concept"})
    List<ConceptVersion> findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(Collection<Long> ids, Long projectId, Long ideaVersionId);
    @EntityGraph(attributePaths={"project","ideaVersion","concept","eligibilityBatch"})
    @Query("select v from ConceptVersion v where v.eligibilityBatch.id=:batchId and v.eligibilityStatus=:status and v.deletedAt is null order by v.concept.displayOrder")
    List<ConceptVersion> findByEligibilityBatchIdAndEligibilityStatusAndDeletedAtIsNullOrderByConceptDisplayOrder(
        @Param("batchId") Long batchId,@Param("status") String eligibilityStatus);
}
