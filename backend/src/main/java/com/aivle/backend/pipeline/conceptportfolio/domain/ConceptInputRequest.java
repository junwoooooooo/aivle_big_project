package com.aivle.backend.pipeline.conceptportfolio.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_input_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptInputRequest extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false) private ConceptPortfolioRun run;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "continuation_id") private ConceptPortfolioContinuation continuation;
    @Column(length = 200) private String candidateId;
    @Column(length = 200) private String lineageId;
    @Column(length = 40) private String scope;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ConceptInputRequestStatus status;
    @Column(columnDefinition = "TEXT") private String sourceQuestion;
    @Column(columnDefinition = "TEXT") private String presentationQuestionKo;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(columnDefinition = "TEXT") private String possibleUserAction;
    @Column(columnDefinition = "TEXT") private String safeSummary;
    @Column(nullable = false, columnDefinition = "TEXT") private String unknownFactsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String affectedFieldsJson;
    @Column(columnDefinition = "TEXT") private String artifactJson;
    @Column(nullable = false, length = 71) private String requestHash;
    private LocalDateTime answeredAt;
    private LocalDateTime resolvedAt;
    @Column(length = 64) private String continuationTaskRunId;

    public static ConceptInputRequest open(ConceptPortfolioRun run,
            ConceptPortfolioContinuation continuation, String candidateId, String lineageId,
            String scope, String sourceQuestion, String reason, String possibleUserAction,
            String safeSummary, String unknownFactsJson, String affectedFieldsJson,
            String artifactJson, String requestHash) {
        if (run == null || unknownFactsJson == null || affectedFieldsJson == null
                || requestHash == null || !requestHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Concept InputRequest is invalid");
        }
        ConceptInputRequest value = new ConceptInputRequest();
        value.id = UUID.randomUUID().toString();
        value.project = run.getProject();
        value.run = run;
        value.continuation = continuation;
        value.candidateId = candidateId;
        value.lineageId = lineageId;
        value.scope = scope;
        value.status = ConceptInputRequestStatus.OPEN;
        value.sourceQuestion = sourceQuestion;
        value.reason = reason;
        value.possibleUserAction = possibleUserAction;
        value.safeSummary = safeSummary;
        value.unknownFactsJson = unknownFactsJson;
        value.affectedFieldsJson = affectedFieldsJson;
        value.artifactJson = artifactJson;
        value.requestHash = requestHash;
        return value;
    }

    public void answer(String taskRunId, LocalDateTime now) {
        if (status != ConceptInputRequestStatus.OPEN || taskRunId == null || taskRunId.isBlank()) {
            throw new IllegalStateException("Only an OPEN InputRequest can be answered");
        }
        status = ConceptInputRequestStatus.ANSWERED;
        answeredAt = now;
        continuationTaskRunId = taskRunId;
    }

    public void attachRetry(String taskRunId) {
        if (status != ConceptInputRequestStatus.ANSWERED
                || taskRunId == null || taskRunId.isBlank()) {
            throw new IllegalStateException("Only an ANSWERED InputRequest can be retried");
        }
        continuationTaskRunId = taskRunId;
    }

    public void resolve(LocalDateTime now) {
        if (status != ConceptInputRequestStatus.ANSWERED) {
            throw new IllegalStateException("Only an ANSWERED InputRequest can be resolved");
        }
        status = ConceptInputRequestStatus.RESOLVED;
        resolvedAt = now;
    }

    public void cancel(LocalDateTime now) {
        if (status != ConceptInputRequestStatus.OPEN && status != ConceptInputRequestStatus.ANSWERED) {
            throw new IllegalStateException("InputRequest cannot be cancelled");
        }
        status = ConceptInputRequestStatus.CANCELLED;
        resolvedAt = now;
    }
}
