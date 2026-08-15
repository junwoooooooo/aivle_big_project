package com.aivle.backend.pipeline.conceptportfolio.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_portfolio_concepts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptPortfolioConcept extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false) private ConceptPortfolioRun run;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Column(nullable = false, length = 200) private String candidateId;
    @Column(nullable = false, length = 200) private String lineageId;
    @Column(nullable = false, length = 200) private String planId;
    @Column(length = 200) private String parentCandidateId;
    @Column(nullable = false) private int displayOrder;
    @Column(nullable = false, length = 500) private String conceptName;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, length = 40) private String legalStatus;
    @Column(nullable = false, columnDefinition = "TEXT") private String candidateSnapshotJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String legalReviewJson;
    @Column(nullable = false, length = 71) private String canonicalHash;
    @Column(nullable = false) private boolean selectable;

    public static ConceptPortfolioConcept create(ConceptPortfolioRun run, int displayOrder,
            String candidateId, String lineageId, String planId, String parentCandidateId,
            String conceptName, String summary, String legalStatus, String candidateJson,
            String legalJson, String canonicalHash) {
        if (run == null || displayOrder < 1 || displayOrder > 5 || blank(candidateId)
                || blank(lineageId) || blank(planId) || blank(conceptName) || blank(summary)
                || blank(legalStatus) || blank(candidateJson) || blank(legalJson)
                || canonicalHash == null || !canonicalHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Portfolio Concept is invalid");
        }
        ConceptPortfolioConcept value = new ConceptPortfolioConcept();
        value.id = UUID.randomUUID().toString();
        value.run = run;
        value.project = run.getProject();
        value.candidateId = candidateId;
        value.lineageId = lineageId;
        value.planId = planId;
        value.parentCandidateId = parentCandidateId;
        value.displayOrder = displayOrder;
        value.conceptName = conceptName;
        value.summary = summary;
        value.legalStatus = legalStatus;
        value.candidateSnapshotJson = candidateJson;
        value.legalReviewJson = legalJson;
        value.canonicalHash = canonicalHash;
        value.selectable = true;
        return value;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
