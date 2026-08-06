package com.aivle.backend.journey.conceptcore;import java.util.*;import org.springframework.data.jpa.repository.*;
public interface ConceptSlotRepository extends JpaRepository<ConceptSlot,Long>{@EntityGraph(attributePaths={"batch"}) List<ConceptSlot> findByBatchIdAndDeletedAtIsNullOrderBySlotIndex(Long batchId);}
