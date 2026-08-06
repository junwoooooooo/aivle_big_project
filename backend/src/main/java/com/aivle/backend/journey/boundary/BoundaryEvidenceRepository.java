package com.aivle.backend.journey.boundary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoundaryEvidenceRepository extends JpaRepository<BoundaryEvidence, Long> {
    List<BoundaryEvidence> findByBoundaryVersionIdAndDeletedAtIsNullOrderByEvidenceKey(Long boundaryVersionId);
    List<BoundaryEvidence> findByBoundaryVersionIdAndDeletedAtIsNull(Long boundaryVersionId);
}
