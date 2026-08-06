package com.aivle.backend.journey.brief;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService.AiField;
import com.aivle.backend.journey.brief.OpportunityBriefWorkspaceService.BriefIncompleteException;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.journey.conversation.IdeaConversation;
import com.aivle.backend.journey.conversation.IdeaMessage;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OpportunityBriefWorkspaceTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired OpportunityBriefWorkspaceService workspace;
    @Autowired ObjectMapper mapper;

    @Test
    void aiProposalKeepsProvenanceAndIsNeverAutoConfirmed() {
        Context context = context("brief-ai@example.com");
        var draft = workspace.mergeAiDraft(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), List.of(new AiField("problem", mapper.valueToTree("waste"),
                FieldDecisionStatus.OPEN, FieldSourceType.AI_PROPOSED, .8)), context.message.getId(), null);

        assertThat(draft.fields()).singleElement().satisfies(field -> {
            assertThat(field.sourceType()).isEqualTo(FieldSourceType.AI_PROPOSED);
            assertThat(field.userConfirmed()).isFalse();
            assertThat(field.sourceMessageId()).isEqualTo(context.message.getId());
        });
    }

    @Test
    void confirmReturnsExactMissingFields() {
        Context context = context("brief-missing@example.com");
        workspace.edit(context.owner.getId(), context.project.getId(), context.conversation.getId(),
            "problem", mapper.valueToTree("waste"), FieldDecisionStatus.LOCKED, context.message.getId());

        assertThatThrownBy(() -> workspace.confirm(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), List.of()))
            .isInstanceOfSatisfying(BriefIncompleteException.class, failure ->
                assertThat(failure.missingFields()).contains("desiredOutcome", "targetRegion",
                    "targetCustomerOrBeneficiaries", "regulatorySensitiveActivities"));
    }

    @Test
    void confirmationIsImmutableAndLaterEditCreatesNewVersion() {
        Context context = context("brief-confirm@example.com");
        put(context, "problem", "waste", FieldDecisionStatus.LOCKED);
        put(context, "targetCustomer", "shops", FieldDecisionStatus.PREFERRED);
        put(context, "desiredOutcome", "less waste", FieldDecisionStatus.LOCKED);
        put(context, "targetRegion", "KR", FieldDecisionStatus.LOCKED);
        put(context, "fixedConstraints", List.of("no hardware"), FieldDecisionStatus.LOCKED);
        put(context, "openDecisions", List.of("pricing"), FieldDecisionStatus.OPEN);
        put(context, "regulatorySensitiveActivities", List.of(), FieldDecisionStatus.OPEN);

        var confirmed = workspace.confirm(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), List.of());
        var edited = workspace.edit(context.owner.getId(), context.project.getId(), context.conversation.getId(),
            "desiredOutcome", mapper.valueToTree("zero waste"), FieldDecisionStatus.LOCKED, context.message.getId());

        assertThat(confirmed.state()).isEqualTo("CONFIRMED");
        assertThat(edited.state()).isEqualTo("DRAFT");
        assertThat(edited.version()).isGreaterThan(confirmed.version());
        assertThat(edited.id()).isNotEqualTo(confirmed.id());
    }

    private void put(Context context, String key, Object value, FieldDecisionStatus status) {
        workspace.edit(context.owner.getId(), context.project.getId(), context.conversation.getId(),
            key, mapper.valueToTree(value), status, context.message.getId());
    }
    private Context context(String email) {
        User owner = users.saveAndFlush(User.create(email, "hashed", email));
        Project project = projects.saveAndFlush(Project.create(owner, email, null, "AI"));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        IdeaMessage message = conversations.appendMessage(owner.getId(), project.getId(), conversation.getId(),
            IdeaMessage.Role.USER, "source");
        return new Context(owner, project, conversation, message);
    }
    private record Context(User owner, Project project, IdeaConversation conversation, IdeaMessage message) { }
}
