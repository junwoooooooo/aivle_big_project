package com.aivle.backend.journey;
import java.util.List;import org.springframework.data.jpa.repository.*;
public interface ConceptDraftRepository extends JpaRepository<ConceptDraft,Long>{
 @EntityGraph(attributePaths={"batch","generationTaskRun"}) List<ConceptDraft> findByBatchIdAndDeletedAtIsNullOrderBySequenceNumber(Long batchId);
}
