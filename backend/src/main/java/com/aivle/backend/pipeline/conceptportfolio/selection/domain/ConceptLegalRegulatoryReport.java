package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_legal_regulatory_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptLegalRegulatoryReport extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long selectionId;
    @Column(nullable = false, length = 64) private String conceptId;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, length = 20) private String schemaVersion;
    @Column(nullable = false, length = 71) private String selectedConceptHash;
    @Column(nullable = false, length = 71) private String hypothesisSnapshotHash;
    @Column(nullable = false, length = 71) private String baseLegalHash;
    @Column(length = 71) private String deltaLegalHash;
    @Column(nullable = false, length = 71) private String officialEvidenceHash;
    @Column(nullable = false, columnDefinition = "TEXT") private String reportJson;
    @Column(nullable = false, length = 71) private String reportHash;
    @Column(nullable = false) private LocalDate basisDate;
    @Column(nullable = false) private Long createdByUserId;

    public static ConceptLegalRegulatoryReport create(String reportId, ConceptPortfolioSelection selection,
            String hypothesisHash, String deltaHash, String evidenceHash, String json,
            String reportHash, Long userId, LocalDate basisDate) {
        ConceptLegalRegulatoryReport value = new ConceptLegalRegulatoryReport();
        value.id = reportId; value.projectId = selection.getProjectId();
        value.selectionId = selection.getId(); value.conceptId = selection.getConceptId();
        value.status = "CURRENT"; value.schemaVersion = "1.0";
        value.selectedConceptHash = selection.getSelectedConceptHash(); value.baseLegalHash = selection.getBaseLegalHash();
        value.hypothesisSnapshotHash = hypothesisHash; value.deltaLegalHash = deltaHash;
        value.officialEvidenceHash = evidenceHash; value.reportJson = json; value.reportHash = reportHash;
        value.createdByUserId = userId; value.basisDate = basisDate;
        return value;
    }
    public void markStale() { status = "STALE"; }
}
