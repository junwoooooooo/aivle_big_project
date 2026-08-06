package com.aivle.backend.pipeline.selection.repository;

import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptSelectionRepository extends JpaRepository<ConceptSelection, Long> {
    Optional<ConceptSelection> findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(Long projectId);
    Optional<ConceptSelection> findByProjectIdAndRequestHashAndCurrentSelectionTrueAndDeletedAtIsNull(Long projectId, String requestHash);
}
