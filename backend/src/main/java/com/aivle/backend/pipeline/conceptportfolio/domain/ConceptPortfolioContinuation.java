package com.aivle.backend.pipeline.conceptportfolio.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_portfolio_continuations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptPortfolioContinuation extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false) private ConceptPortfolioRun run;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @Column(nullable = false, length = 20) private String contextVersion;
    @Column(nullable = false, length = 71) private String contextHash;
    @Column(nullable = false, columnDefinition = "TEXT") private String contextJson;

    public static ConceptPortfolioContinuation create(ConceptPortfolioRun run, String contextVersion,
            String contextHash, String contextJson) {
        if (run == null || contextVersion == null || contextVersion.isBlank()
                || contextHash == null || !contextHash.matches("sha256:[0-9a-f]{64}")
                || contextJson == null || contextJson.isBlank()) {
            throw new IllegalArgumentException("Continuation Context is invalid");
        }
        ConceptPortfolioContinuation value = new ConceptPortfolioContinuation();
        value.id = UUID.randomUUID().toString();
        value.run = run;
        value.project = run.getProject();
        value.contextVersion = contextVersion;
        value.contextHash = contextHash;
        value.contextJson = contextJson;
        return value;
    }
}
