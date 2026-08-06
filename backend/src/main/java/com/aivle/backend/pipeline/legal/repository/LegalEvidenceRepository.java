package com.aivle.backend.pipeline.legal.repository;

import com.aivle.backend.pipeline.legal.domain.LegalEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalEvidenceRepository extends JpaRepository<LegalEvidence, String> {
    List<LegalEvidence> findAllByContextPackIdAndProjectIdAndDeletedAtIsNull(String contextPackId, Long projectId);
}
