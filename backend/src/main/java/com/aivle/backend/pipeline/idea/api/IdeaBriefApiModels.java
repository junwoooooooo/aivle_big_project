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
        @NotBlank @Size(max = 20000) String overview,
        @Valid List<FieldCommand> fields,
        Set<Long> attachmentFileIds
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

    public record IdeaBriefResponse(
        String briefId,
        IdeaBriefStatus status,
        List<FieldView> fields,
        List<QuestionView> questions,
        ReadinessView readiness,
        String activeJobId,
        String confirmedSnapshotId,
        LocalDateTime updatedAt
    ) {}

    public record FieldView(
        String fieldKey,
        String value,
        IdeaDecisionState decisionState,
        IdeaFieldProvenance provenance
    ) {}

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
        int fieldCount,
        int missingFieldCount,
        int unansweredQuestionCount,
        boolean readyForConfirm
    ) {}
}
