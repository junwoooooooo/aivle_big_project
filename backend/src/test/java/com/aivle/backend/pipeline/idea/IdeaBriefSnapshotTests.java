package com.aivle.backend.pipeline.idea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import org.junit.jupiter.api.Test;

class IdeaBriefSnapshotTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void confirmationIsImmutableAndLaterEditingUsesANewDraftVersion() {
        IdeaBrief confirmed = IdeaBrief.initial(null, 7L);
        confirmed.updateOverview("overview is not an assumption");
        confirmed.applyAssessment("summary", "[]", "[]", "READY_FOR_REVIEW", 100);
        IdeaBriefField field = IdeaBriefField.userValue(
            confirmed, "problem", "first value", IdeaDecisionState.LOCKED
        );
        confirmed.readyForReview();
        confirmed.confirm(HASH, "confirm-1", HASH);

        assertThat(confirmed.getStatus()).isEqualTo(IdeaBriefStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedSnapshotId()).isEqualTo(confirmed.getId());
        assertThatThrownBy(() -> field.updateByUser("overwritten", IdeaDecisionState.OPEN))
            .isInstanceOf(IllegalStateException.class);

        IdeaBrief nextDraft = IdeaBrief.nextDraft(confirmed, 7L);
        nextDraft.copyCanonicalStateFrom(confirmed);
        IdeaBriefField copied = field.copyTo(nextDraft);
        copied.updateByUser("new draft value", IdeaDecisionState.PREFERRED);

        assertThat(nextDraft.getParentBriefId()).isEqualTo(confirmed.getId());
        assertThat(nextDraft.getConfirmedSnapshotId()).isEqualTo(confirmed.getId());
        assertThat(field.getFieldValue()).isEqualTo("first value");
        assertThat(copied.getFieldValue()).isEqualTo("new draft value");
        assertThat(nextDraft.getOverviewText()).isEqualTo("overview is not an assumption");
        assertThat(nextDraft.getUserFacingSummary()).isEqualTo("summary");
    }
}
