package com.aivle.backend.pipeline.concept.repository;

import com.aivle.backend.pipeline.concept.domain.ConceptAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptAttemptRepository extends JpaRepository<ConceptAttempt, String> {
    List<ConceptAttempt> findAllBySlotIdOrderByAttemptNumber(String slotId);
}
