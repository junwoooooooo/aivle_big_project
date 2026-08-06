package com.aivle.backend.analysis.legal.entity;

import com.aivle.backend.common.entity.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "legal_findings")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalFinding extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "legal_review_id", nullable = false) private LegalReview legalReview;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 100) private LegalCategory category;
    @Column(nullable = false) private Integer displayOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private LegalApplicability applicability;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RiskLevel severity;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String legalBasis;
    @Column(length = 255) private String sourceName;
    @Column(columnDefinition = "TEXT") private String sourceUrl;
    @Column(columnDefinition = "TEXT") private String recommendation;
    @Column(columnDefinition = "TEXT") private String rationale;
    @Column(columnDefinition = "TEXT") private String evidenceJson;
    @Column(columnDefinition = "TEXT") private String sourceSectionCodesJson;
    @Column(nullable = false) private Boolean requiresProfessionalReview;
    @Column(precision = 5, scale = 4) private java.math.BigDecimal confidence;

    public static LegalFinding create(
        LegalReview review, LegalCategory category, int displayOrder,
        LegalApplicability applicability, RiskLevel risk, String title, String finding,
        String rationale, String recommendation, String evidenceJson,
        String sourceSectionCodesJson, boolean professionalReview, java.math.BigDecimal confidence
    ) {
        LegalFinding item = new LegalFinding();
        item.legalReview = review;
        item.category = category;
        item.displayOrder = displayOrder;
        item.applicability = applicability;
        item.severity = risk;
        item.title = title;
        item.description = finding;
        item.rationale = rationale;
        item.recommendation = recommendation;
        item.evidenceJson = evidenceJson;
        item.sourceSectionCodesJson = sourceSectionCodesJson;
        item.requiresProfessionalReview = professionalReview;
        item.confidence = confidence;
        return item;
    }
}
