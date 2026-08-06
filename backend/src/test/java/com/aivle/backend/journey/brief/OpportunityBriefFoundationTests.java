package com.aivle.backend.journey.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.journey.conversation.IdeaConversation;
import com.aivle.backend.journey.foundation.SnapshotHasher;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OpportunityBriefFoundationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired OpportunityBriefService briefs;
    @Autowired SnapshotHasher hasher;

    @Test
    void persistsVersionsFieldsAndReturnsLatestConfirmedVersion() {
        Context context = context("brief-owner@example.com", "brief-owner");
        OpportunityBriefVersion first = briefs.createDraft(context.owner().getId(), context.project().getId(),
            context.conversation().getId(), null, "{\"target_region\":\"KR\",\"problem_or_opportunity\":\"waste\"}",
            List.of(
                new OpportunityBriefService.FieldInput("problem_or_opportunity", "\"waste\"",
                    FieldDecisionStatus.LOCKED, FieldSourceType.USER_CONFIRMED, "message:1"),
                new OpportunityBriefService.FieldInput("beneficiaries", null,
                    FieldDecisionStatus.OPEN, FieldSourceType.MISSING, null)));
        briefs.confirm(context.owner().getId(), context.project().getId(), first.getId());

        OpportunityBriefVersion second = briefs.createDraft(context.owner().getId(), context.project().getId(),
            context.conversation().getId(), first.getId(), "{\"problem_or_opportunity\":\"waste\",\"target_region\":\"KR\",\"usage_context\":\"home\"}",
            List.of(new OpportunityBriefService.FieldInput("usage_context", "\"home\"",
                FieldDecisionStatus.PREFERRED, FieldSourceType.SOURCE_EXTRACTED, "document:2")));
        briefs.confirm(context.owner().getId(), context.project().getId(), second.getId());

        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(briefs.currentConfirmed(context.owner().getId(), context.project().getId()).getId())
            .isEqualTo(second.getId());
        assertThat(briefs.fields(context.owner().getId(), context.project().getId(), first.getId()))
            .extracting(OpportunityFieldValue::getSourceType)
            .containsExactly(FieldSourceType.MISSING, FieldSourceType.USER_CONFIRMED);
    }

    @Test
    void canonicalHashIsDeterministicForObjectOrderUnicodeAndDecimalRepresentation() {
        String left = hasher.hash("{\"b\":1.00,\"a\":\"é\"}");
        String right = hasher.hash("{\"a\":\"é\",\"b\":1}");
        assertThat(left).isEqualTo(right).startsWith("sha256:").hasSize(71);
    }

    @Test
    void missingAndNonMissingValuesCannotBeSilentlyConverted() {
        Context context = context("brief-values@example.com", "brief-values");
        assertThatThrownBy(() -> briefs.createDraft(context.owner().getId(), context.project().getId(),
            context.conversation().getId(), null, "{}", List.of(
                new OpportunityBriefService.FieldInput("target_region", "\"KR\"",
                    FieldDecisionStatus.OPEN, FieldSourceType.MISSING, null))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> briefs.createDraft(context.owner().getId(), context.project().getId(),
            context.conversation().getId(), null, "{}", List.of(
                new OpportunityBriefService.FieldInput("target_region", null,
                    FieldDecisionStatus.OPEN, FieldSourceType.AI_PROPOSED, null))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currentBriefIsProjectIsolated() {
        Context context = context("brief-private@example.com", "brief-private");
        User outsider = users.saveAndFlush(User.create("brief-outsider@example.com", "hashed", "brief-outsider"));

        assertThatThrownBy(() -> briefs.currentConfirmed(outsider.getId(), context.project().getId()))
            .isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    private Context context(String email, String name) {
        User owner = users.saveAndFlush(User.create(email, "hashed", name));
        Project project = projects.saveAndFlush(Project.create(owner, name, null, "AI"));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        return new Context(owner, project, conversation);
    }

    private record Context(User owner, Project project, IdeaConversation conversation) { }
}
