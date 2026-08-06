package com.aivle.backend.pipeline.planning.repository;
import com.aivle.backend.pipeline.planning.domain.FinalizedPlanningSnapshot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinalizedPlanningSnapshotRepository extends JpaRepository<FinalizedPlanningSnapshot, String> {
    Optional<FinalizedPlanningSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(Long projectId);
    Optional<FinalizedPlanningSnapshot> findByProjectIdAndSourceSelectionSnapshotIdAndDeletedAtIsNull(Long projectId, String sourceId);
    List<FinalizedPlanningSnapshot> findAllByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(Long projectId);
}
