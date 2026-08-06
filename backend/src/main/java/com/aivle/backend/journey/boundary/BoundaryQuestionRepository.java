package com.aivle.backend.journey.boundary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoundaryQuestionRepository extends JpaRepository<BoundaryQuestion, Long> {
    List<BoundaryQuestion> findByBoundaryVersionIdAndDeletedAtIsNullOrderByQuestionKey(Long boundaryVersionId);
}
