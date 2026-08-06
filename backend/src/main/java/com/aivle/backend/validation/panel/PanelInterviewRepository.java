package com.aivle.backend.validation.panel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelInterviewRepository extends JpaRepository<PanelInterview, Long> {
    List<PanelInterview> findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long projectId);
    Optional<PanelInterview> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
}
