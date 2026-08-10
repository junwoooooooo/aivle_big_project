package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_portfolio_delta_legal_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptPortfolioDeltaLegalReview extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private Long selectionId;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false, length = 64) private String conceptId;
    @Column(nullable = false, length = 64) private String taskRunId;
    @Column(nullable = false) private int hypothesisRevision;
    @Column(nullable = false, length = 71) private String reviewToken;
    @Column(nullable = false, columnDefinition = "TEXT") private String hypothesisTypesJson;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private boolean approved;
    @Column(nullable = false, columnDefinition = "TEXT") private String legalReviewJson;
    @Column(nullable = false, length = 71) private String resultHash;

    public static ConceptPortfolioDeltaLegalReview create(ConceptPortfolioSelection selection, String taskRunId,
            int hypothesisRevision, String token, String types, String status, boolean approved,
            String legal, String hash) {
        if (hypothesisRevision < 0) throw new IllegalArgumentException("Hypothesis revision is invalid");
        ConceptPortfolioDeltaLegalReview value = new ConceptPortfolioDeltaLegalReview();
        value.id = UUID.randomUUID().toString(); value.selectionId = selection.getId();
        value.projectId = selection.getProjectId(); value.conceptId = selection.getConceptId();
        value.taskRunId = taskRunId; value.hypothesisRevision = hypothesisRevision;
        value.reviewToken = token; value.hypothesisTypesJson = types;
        value.status = status; value.approved = approved; value.legalReviewJson = legal; value.resultHash = hash;
        return value;
    }
}
