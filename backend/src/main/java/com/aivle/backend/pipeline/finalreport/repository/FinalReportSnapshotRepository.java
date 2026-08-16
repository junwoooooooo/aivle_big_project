package com.aivle.backend.pipeline.finalreport.repository;

import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalReportSnapshotRepository extends JpaRepository<FinalReportSnapshot, String> {
    Optional<FinalReportSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(Long projectId);
    Optional<FinalReportSnapshot> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
    Optional<FinalReportSnapshot> findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(Long projectId, String commandIdempotencyKey);
}
