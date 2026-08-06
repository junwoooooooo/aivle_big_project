package com.aivle.backend.journey.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.FieldSourceType;
import com.aivle.backend.journey.brief.OpportunityBriefService;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.journey.conversation.IdeaConversation;
import com.aivle.backend.journey.foundation.WorkspaceStaleService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegulatoryBoundaryFoundationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired OpportunityBriefService briefs;
    @Autowired RegulatoryBoundaryService boundaries;
    @Autowired BoundaryEvidenceRepository evidence;
    @Autowired BoundaryRuleRepository rules;
    @Autowired BoundaryQuestionRepository questions;
    @Autowired WorkspaceStaleService stale;

    @Test
    void enforcesRunTransitionsAndPersistsVersionRuleEvidenceAndQuestion() {
        Context context = context("boundary-owner@example.com", "boundary-owner");
        RegulatoryBoundaryRun run = boundaries.createRun(context.owner().getId(), context.project().getId(),
            context.brief().getId(), null);
        assertThat(run.getState()).isEqualTo(RegulatoryBoundaryRun.State.QUEUED);
        boundaries.start(context.owner().getId(), context.project().getId(), run.getId());
        boundaries.succeed(context.owner().getId(), context.project().getId(), run.getId());

        RegulatoryBoundaryVersion version = boundaries.createVersion(context.owner().getId(), context.project().getId(),
            run.getId(), RegulatoryBoundaryVersion.Status.NEEDS_INPUT, "{\"status\":\"NEEDS_INPUT\"}");
        BoundaryEvidence savedEvidence = evidence.save(BoundaryEvidence.create(version, "ev-1", "법률", "제1조",
            "목적", "공식 발췌", "2026-01-01", "https://law.example/1", "SOURCE_COMPLETE"));
        rules.save(BoundaryRule.create(version, "rule-1", BoundaryRule.RuleType.REQUIRED_CONTROL,
            "접근 권한을 통제한다", "공식 근거에 따른 운영 통제", "[\"regulated_activities\"]",
            "[" + savedEvidence.getId() + "]", "HIGH", "[]"));
        BoundaryQuestion question = questions.save(BoundaryQuestion.open(version, "question-1",
            "보관 기간은 얼마입니까?", "보관 통제를 결정해야 합니다", "regulated_activities"));
        question.answer("\"1 year\"", LocalDateTime.now());

        assertThat(boundaries.current(context.owner().getId(), context.project().getId()).getId()).isEqualTo(version.getId());
        assertThat(evidence.findByBoundaryVersionIdAndDeletedAtIsNullOrderByEvidenceKey(version.getId())).hasSize(1);
        assertThat(rules.findByBoundaryVersionIdAndDeletedAtIsNullOrderByRuleKey(version.getId())).hasSize(1);
        assertThat(questions.findByBoundaryVersionIdAndDeletedAtIsNullOrderByQuestionKey(version.getId()))
            .singleElement().extracting(BoundaryQuestion::getState).isEqualTo(BoundaryQuestion.State.ANSWERED);
        assertThatThrownBy(() -> run.start()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void newerConfirmedBriefMakesExistingBoundaryStaleAndCascadesDownstream() {
        Context context = context("boundary-stale@example.com", "boundary-stale");
        RegulatoryBoundaryRun run = boundaries.createRun(context.owner().getId(), context.project().getId(),
            context.brief().getId(), null);
        boundaries.start(context.owner().getId(), context.project().getId(), run.getId());
        boundaries.succeed(context.owner().getId(), context.project().getId(), run.getId());
        RegulatoryBoundaryVersion version = boundaries.createVersion(context.owner().getId(), context.project().getId(),
            run.getId(), RegulatoryBoundaryVersion.Status.READY, "{\"status\":\"READY\"}");

        OpportunityBriefVersion next = briefs.createDraft(context.owner().getId(), context.project().getId(),
            context.conversation().getId(), context.brief().getId(), "{\"target_region\":\"KR\",\"usage_context\":\"mobile\"}", List.of());
        briefs.confirm(context.owner().getId(), context.project().getId(), next.getId());

        assertThat(stale.boundaryIsStale(next, version)).isTrue();
        assertThat(stale.afterBriefChange())
            .isEqualTo(new WorkspaceStaleService.StaleCascade(true, true, true, true));
        assertThat(stale.afterBoundaryChange())
            .isEqualTo(new WorkspaceStaleService.StaleCascade(false, true, true, true));
    }

    private Context context(String email, String name) {
        User owner = users.saveAndFlush(User.create(email, "hashed", name));
        Project project = projects.saveAndFlush(Project.create(owner, name, null, "AI"));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        OpportunityBriefVersion brief = briefs.createDraft(owner.getId(), project.getId(), conversation.getId(), null,
            "{\"target_region\":\"KR\"}", List.of(new OpportunityBriefService.FieldInput("target_region", "\"KR\"",
                FieldDecisionStatus.LOCKED, FieldSourceType.USER_CONFIRMED, "message:1")));
        briefs.confirm(owner.getId(), project.getId(), brief.getId());
        return new Context(owner, project, conversation, brief);
    }

    private record Context(User owner, Project project, IdeaConversation conversation,
                           OpportunityBriefVersion brief) { }
}
