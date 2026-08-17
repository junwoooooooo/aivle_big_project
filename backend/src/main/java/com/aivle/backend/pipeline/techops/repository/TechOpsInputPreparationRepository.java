package com.aivle.backend.pipeline.techops.repository;

import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TechOpsInputPreparationRepository extends JpaRepository<TechOpsInputPreparation, String> {
    Optional<TechOpsInputPreparation> findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(Long projectId, String sourceId);
    Optional<TechOpsInputPreparation> findFirstByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Long projectId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TechOpsInputPreparation p where p.id=:id and p.projectId=:projectId and p.deletedAt is null")
    Optional<TechOpsInputPreparation> findLocked(@Param("id") String id, @Param("projectId") Long projectId);
}
