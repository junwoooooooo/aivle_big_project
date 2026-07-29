package com.aivle.backend.analysis.legal.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RevisionSuggestionRepository extends JpaRepository<RevisionSuggestion, Long> {
    List<RevisionSuggestion> findByRevisionRequestIdAndDeletedAtIsNullOrderByDisplayOrder(Long requestId);

    List<RevisionSuggestion> findByRevisionRequestIdInAndDeletedAtIsNullOrderByDisplayOrder(List<Long> requestIds);
}
