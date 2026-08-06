package com.aivle.backend.pipeline.legal.repository;

import com.aivle.backend.pipeline.legal.domain.LegalEvidence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalEvidenceRepository extends JpaRepository<LegalEvidence, String> {
    List<LegalEvidence> findAllByContextPackIdAndProjectIdAndDeletedAtIsNull(String contextPackId, Long projectId);
    Optional<LegalEvidence> findByContextPackIdAndQueryKeyAndArticleReferenceAndContentHashAndDeletedAtIsNull(
        String contextPackId, String queryKey, String articleReference, String contentHash);
}
