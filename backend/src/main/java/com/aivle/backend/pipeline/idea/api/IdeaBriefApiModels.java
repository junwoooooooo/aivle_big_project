package com.aivle.backend.pipeline.idea.api;

import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.domain.IdeaFieldProvenance;
import com.aivle.backend.pipeline.idea.domain.IdeaQuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class IdeaBriefApiModels {
    private IdeaBriefApiModels() {}

    public record DeriveRequest(
        @NotBlank @Size(max = 20000) String ideaOverview,
        @NotBlank @Size(max = 20000) String problem,
        @NotBlank @Size(max = 20000) String targetUsers,
        @Valid OptionalSeedRequest optionalSeed,
        Set<Long> attachmentFileIds
    ) {}

    public record OptionalSeedRequest(
        @Size(max = 20000) String targetRegion,
        @Size(max = 20000) String knownCompetitors,
        @Size(max = 20000) String revenueModel,
        @Size(max = 20000) String price,
        @Size(max = 20000) String channels,
        @Size(max = 20000) String differentiators,
        @Valid SeedConstraintsRequest constraints
    ) {}

    public record SeedConstraintsRequest(
        @Size(max = 20000) String budgetConstraint,
        @Size(max = 20000) String teamConstraint,
        @Size(max = 20000) String timelineConstraint,
        @Size(max = 20000) String otherConstraint
    ) {}

    public record PatchFieldsRequest(@NotEmpty @Valid List<FieldCommand> fields) {}

    public record FieldCommand(
        @NotBlank @Size(max = 80) String fieldKey,
        @Size(max = 20000) String value,
        IdeaDecisionState decisionState
    ) {}

    public record AnswersRequest(@NotEmpty @Valid List<AnswerCommand> answers) {}

    public record AnswerCommand(
        @NotBlank String questionId,
        @NotBlank @Size(max = 20000) String answerJson
    ) {}

    public record ConfirmRequest(Long expectedVersion) {}

    public record PatchInterpretationRequest(
        @NotBlank @Size(max = 20000) String interpretedProblem,
        @NotBlank @Size(max = 20000) String interpretedTargetUsers,
        @NotBlank @Size(max = 20000) String usageContext,
        @NotBlank @Size(max = 20000) String industryCategory,
        @NotBlank @Size(max = 20000) String researchScope,
        @NotBlank @Size(max = 20000) String conciseIdeaDefinition,
        @Size(max = 20000) String targetRegionInterpretation,
        @Size(max = 20000) String relevantKnownCompetitorContext
    ) {}

    public record ReviewCommitmentsRequest(@NotEmpty @Valid List<CommitmentDecisionCommand> commitments) {}

    public record CommitmentDecisionCommand(
        @NotBlank @Size(max = 80) String fieldKey,
        @NotBlank @Size(max = 40) String action,
        @Size(max = 20000) String value
    ) {}

    public record IdeaBriefResponse(
        String briefId,
        IdeaBriefStatus status,
        String overview,
        List<FieldView> fields,
        List<QuestionView> questions,
        List<FieldCatalogView> fieldCatalog,
        SafetyReviewView safetyReview,
        IdeaInterpretationView interpretation,
        String userFacingSummary,
        List<ContradictionView> contradictions,
        ReadinessView readiness,
        int clarificationRound,
        int maxClarificationRounds,
        boolean assessmentCurrent,
        boolean executionStateConsistent,
        boolean recoveryRequired,
        String activeJobId,
        String confirmedSnapshotId,
        LocalDateTime updatedAt
    ) {
        public IdeaBriefResponse(String briefId, IdeaBriefStatus status, String overview,
                List<FieldView> fields, List<QuestionView> questions, List<FieldCatalogView> fieldCatalog,
                String userFacingSummary, List<ContradictionView> contradictions, ReadinessView readiness,
                int clarificationRound, int maxClarificationRounds, String activeJobId,
                String confirmedSnapshotId, LocalDateTime updatedAt) {
            this(briefId, status, overview, fields, questions, fieldCatalog, null, null, userFacingSummary,
                contradictions, readiness, clarificationRound, maxClarificationRounds, false, true, false,
                activeJobId, confirmedSnapshotId, updatedAt);
        }
    }

    public record SafetyReviewView(
        String decision,
        List<String> categories,
        List<String> restrictions,
        String userFacingReason
    ) {}

    public record IdeaInterpretationView(
        String interpretedProblem,
        String interpretedTargetUsers,
        String usageContext,
        String industryCategory,
        String researchScope,
        String conciseIdeaDefinition,
        String targetRegionInterpretation,
        String relevantKnownCompetitorContext,
        List<CommitmentCandidateView> commitmentCandidates,
        String source,
        String authority,
        boolean userEdited,
        boolean confirmed
    ) {}

    public record CommitmentCandidateView(
        String fieldKey,
        String value,
        String evidenceQuote,
        String source,
        String origin,
        String authority
    ) {}

    public record FieldView(
        String fieldKey,
        String value,
        IdeaDecisionState decisionState,
        IdeaFieldProvenance provenance,
        boolean explicitlyUndecided
    ) {}

    public record FieldCatalogView(
        String key,
        String label,
        boolean requiredForConcept,
        IdeaDecisionState defaultDecisionState,
        boolean regulatorySensitive,
        Set<IdeaQuestionType> allowedQuestionTypes
    ) {}

    public record ContradictionView(List<String> fieldKeys, String summary) {}

    public record QuestionView(
        String questionId,
        String targetFieldKey,
        IdeaQuestionType type,
        String prompt,
        String optionsJson,
        boolean answered,
        String answerJson
    ) {}

    public record ReadinessView(
        int totalRequiredFieldCount,
        int completedRequiredFieldCount,
        List<String> missingFieldKeys,
        int unansweredQuestionCount,
        int contradictionCount,
        int score,
        boolean readyForConfirm
    ) {}
}
