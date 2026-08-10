package com.aivle.backend.pipeline.legal.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.concept.domain.Concept;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_legal_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptLegalAssessment extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "concept_id", nullable = false) private Concept concept;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "context_pack_id", nullable = false) private LegalContextPack contextPack;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ConceptLegalStatus status;
    @Column(nullable = false, length = 1000) private String safeSummary;
    @Column(nullable = false, columnDefinition = "TEXT") private String assessmentJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String legalTraceJson;

    public static ConceptLegalAssessment create(Concept concept, LegalContextPack pack, ConceptLegalStatus status, String safeSummary,
                                                 String assessmentJson, String legalTraceJson) {
        ConceptLegalAssessment assessment = new ConceptLegalAssessment();
        assessment.id = UUID.randomUUID().toString();
        assessment.concept = concept;
        assessment.contextPack = pack;
        assessment.projectId = concept.getProjectId();
        assessment.status = status;
        assessment.safeSummary = safeSummary;
        assessment.assessmentJson = assessmentJson;
        assessment.legalTraceJson = legalTraceJson;
        return assessment;
    }
}
