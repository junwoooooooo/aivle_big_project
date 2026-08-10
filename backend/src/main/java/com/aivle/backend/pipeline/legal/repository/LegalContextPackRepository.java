package com.aivle.backend.pipeline.legal.repository;

import com.aivle.backend.pipeline.legal.domain.LegalContextPack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalContextPackRepository extends JpaRepository<LegalContextPack, String> {
    Optional<LegalContextPack> findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(Long projectId, String sourceSnapshotId);
}
