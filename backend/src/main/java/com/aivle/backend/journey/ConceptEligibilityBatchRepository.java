package com.aivle.backend.journey;
import java.util.Optional;import org.springframework.data.jpa.repository.*;
public interface ConceptEligibilityBatchRepository extends JpaRepository<ConceptEligibilityBatch,Long>{
 @Override @EntityGraph(attributePaths={"project","ideaOriginVersion","ideaOriginVersion.sourceIdeaVersion","legalGuardrailSet"}) Optional<ConceptEligibilityBatch> findById(Long id);
 @EntityGraph(attributePaths={"project","ideaOriginVersion","legalGuardrailSet","legalGuardrailSet.legalPrecheckVersion"}) Optional<ConceptEligibilityBatch> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
 @EntityGraph(attributePaths={"project","ideaOriginVersion","legalGuardrailSet"}) Optional<ConceptEligibilityBatch> findTopByProjectIdAndInputSnapshotHashAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId,String hash);
}
