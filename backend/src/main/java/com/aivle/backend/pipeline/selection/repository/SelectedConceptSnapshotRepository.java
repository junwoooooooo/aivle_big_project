package com.aivle.backend.pipeline.selection.repository;

import com.aivle.backend.pipeline.selection.domain.SelectedConceptSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectedConceptSnapshotRepository extends JpaRepository<SelectedConceptSnapshot, String> {
    Optional<SelectedConceptSnapshot> findBySelectionIdAndProjectIdAndDeletedAtIsNull(Long selectionId, Long projectId);
    Optional<SelectedConceptSnapshot> findFirstByProjectIdAndDeletedAtIsNullOrderBySequenceDesc(Long projectId);
    Optional<SelectedConceptSnapshot> findByIdAndProjectIdAndDeletedAtIsNull(String id, Long projectId);
}
