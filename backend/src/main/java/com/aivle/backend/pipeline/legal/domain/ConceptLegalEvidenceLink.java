package com.aivle.backend.pipeline.legal.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_legal_evidence_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptLegalEvidenceLink extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assessment_id", nullable = false) private ConceptLegalAssessment assessment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "evidence_id", nullable = false) private LegalEvidence evidence;
    @Column(name = "project_id", nullable = false) private Long projectId;

    public static ConceptLegalEvidenceLink create(ConceptLegalAssessment assessment, LegalEvidence evidence) {
        if (!assessment.getProjectId().equals(evidence.getProjectId())) {
            throw new IllegalArgumentException("assessment and evidence must belong to the same project");
        }
        ConceptLegalEvidenceLink link = new ConceptLegalEvidenceLink();
        link.assessment = assessment;
        link.evidence = evidence;
        link.projectId = assessment.getProjectId();
        return link;
    }
}
