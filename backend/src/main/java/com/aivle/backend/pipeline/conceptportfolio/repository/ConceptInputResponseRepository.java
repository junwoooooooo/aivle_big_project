package com.aivle.backend.pipeline.conceptportfolio.repository;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputResponse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptInputResponseRepository extends JpaRepository<ConceptInputResponse, String> {
    Optional<ConceptInputResponse> findByInputRequestIdAndIdempotencyKeyAndDeletedAtIsNull(
        String inputRequestId, String idempotencyKey);
    Optional<ConceptInputResponse> findFirstByInputRequestIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
        String inputRequestId);
}
