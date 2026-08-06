package com.aivle.backend.journey.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.FieldSourceType;
import com.aivle.backend.journey.brief.OpportunityBriefService;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.journey.conversation.IdeaConversation;
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

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
@Transactional
class RegulatoryBoundaryApplicationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired OpportunityBriefService briefs;
    @Autowired RegulatoryBoundaryApplicationService boundaries;
    @Autowired RegulatoryBoundaryService foundation;
    @Autowired RegulatoryBoundaryVersionRepository versions;

    @Test
    void requiresConfirmedCurrentBriefAndDoesNotPromoteDraft() {
        Context context = context("boundary-draft@example.com", false);
        var result = boundaries.start(context.owner.getId(), context.project.getId(), context.brief.getId());
        assertThat(result.status()).isEqualTo("NEEDS_INPUT");
        assertThat(result.missingPrerequisites()).containsExactly("confirmedOpportunityBrief");
        assertThat(result.jobId()).isNull();
    }

    @Test
    void createsOneDurableRunForSameConfirmedBriefAndHash() {
        Context context = context("boundary-idempotent@example.com", true);
        var first = boundaries.start(context.owner.getId(), context.project.getId(), context.brief.getId());
        var replay = boundaries.start(context.owner.getId(), context.project.getId(), context.brief.getId());
        assertThat(first.status()).isEqualTo("QUEUED");
        assertThat(replay.runId()).isEqualTo(first.runId());
        assertThat(replay.jobId()).isEqualTo(first.jobId());
        assertThat(boundaries.run(context.owner.getId(), context.project.getId(), first.runId()).confirmedBriefVersionId())
            .isEqualTo(context.brief.getId());
    }

    @Test
    void enforcesProjectIsolationForBriefAndBoundaryRun() {
        Context one = context("boundary-one@example.com", true);
        Context two = context("boundary-two@example.com", true);
        assertThatThrownBy(() -> boundaries.start(one.owner.getId(), one.project.getId(), two.brief.getId()))
            .isInstanceOf(BusinessException.class);
        var started = boundaries.start(one.owner.getId(), one.project.getId(), one.brief.getId());
        assertThatThrownBy(() -> boundaries.run(two.owner.getId(), one.project.getId(), started.runId()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void newerConfirmedBriefMarksHistoricalBoundaryStaleAndExcludesItFromCurrent() {
        Context context = context("boundary-stale-g4@example.com", true);
        RegulatoryBoundaryRun run = foundation.createRun(context.owner.getId(), context.project.getId(),
            context.brief.getId(), null);
        foundation.start(context.owner.getId(), context.project.getId(), run.getId());
        foundation.succeed(context.owner.getId(), context.project.getId(), run.getId());
        RegulatoryBoundaryVersion old = foundation.createVersion(context.owner.getId(), context.project.getId(),
            run.getId(), RegulatoryBoundaryVersion.Status.READY, "{\"status\":\"READY\",\"conflicts\":[],\"userActionOptions\":[],\"sourceWarnings\":[]}");
        OpportunityBriefVersion next = briefs.createDraft(context.owner.getId(), context.project.getId(),
            context.conversation.getId(), context.brief.getId(), "{\"targetRegion\":\"US\"}", List.of());
        briefs.confirm(context.owner.getId(), context.project.getId(), next.getId());

        var current = boundaries.current(context.owner.getId(), context.project.getId());
        assertThat(current.stale()).isTrue();
        assertThat(current.version()).isNull();
        assertThat(current.staleVersionId()).isEqualTo(old.getId());
        assertThat(versions.findById(old.getId()).orElseThrow().getStatus())
            .isEqualTo(RegulatoryBoundaryVersion.Status.STALE);
    }

    private Context context(String email, boolean confirm) {
        User owner = users.saveAndFlush(User.create(email, "hashed", email));
        Project project = projects.saveAndFlush(Project.create(owner, email, null, "AI"));
        IdeaConversation conversation = conversations.create(owner.getId(), project.getId(), null);
        OpportunityBriefVersion brief = briefs.createDraft(owner.getId(), project.getId(), conversation.getId(), null,
            "{\"targetRegion\":\"KR\",\"fixedConstraints\":[]}", List.of(
                new OpportunityBriefService.FieldInput("targetRegion", "\"KR\"", FieldDecisionStatus.LOCKED,
                    FieldSourceType.USER_CONFIRMED, "user"),
                new OpportunityBriefService.FieldInput("regulatorySensitiveActivities", "[]", FieldDecisionStatus.OPEN,
                    FieldSourceType.USER_CONFIRMED, "user")));
        if (confirm) briefs.confirm(owner.getId(), project.getId(), brief.getId());
        return new Context(owner, project, conversation, brief);
    }

    private record Context(User owner, Project project, IdeaConversation conversation, OpportunityBriefVersion brief) { }
}
