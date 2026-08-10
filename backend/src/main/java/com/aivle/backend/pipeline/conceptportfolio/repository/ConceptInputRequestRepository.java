package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputRequest;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputRequestStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptInputRequestRepository extends JpaRepository<ConceptInputRequest, String> {
    Optional<ConceptInputRequest> findByRunIdAndRequestHashAndDeletedAtIsNull(
        String runId, String requestHash);
    long countByRunIdAndStatusInAndDeletedAtIsNull(
        String runId, Collection<ConceptInputRequestStatus> statuses);
}
