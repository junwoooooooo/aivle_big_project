package com.aivle.backend.pipeline.planning.repository;
import com.aivle.backend.pipeline.planning.domain.PlanningSnapshot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanningSnapshotRepository extends JpaRepository<PlanningSnapshot, String> {
    Optional<PlanningSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(Long projectId);
}
