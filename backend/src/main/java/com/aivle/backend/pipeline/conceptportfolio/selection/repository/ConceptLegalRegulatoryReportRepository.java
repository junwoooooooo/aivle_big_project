package com.aivle.backend.pipeline.conceptportfolio.selection.repository;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptLegalRegulatoryReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptLegalRegulatoryReportRepository extends JpaRepository<ConceptLegalRegulatoryReport, String> {
    Optional<ConceptLegalRegulatoryReport> findBySelectionIdAndStatusAndDeletedAtIsNull(Long selectionId, String status);
    List<ConceptLegalRegulatoryReport> findAllBySelectionIdAndStatusAndDeletedAtIsNull(Long selectionId, String status);
}
