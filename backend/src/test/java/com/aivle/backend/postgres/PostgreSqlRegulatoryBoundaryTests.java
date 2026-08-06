package com.aivle.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.journey.boundary.*;
import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.FieldSourceType;
import com.aivle.backend.journey.brief.OpportunityBriefService;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@Tag("postgres")
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class PostgreSqlRegulatoryBoundaryTests extends PostgreSqlIntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired OpportunityBriefService briefs;
    @Autowired RegulatoryBoundaryApplicationService application;
    @Autowired RegulatoryBoundaryService foundation;
    @Autowired RegulatoryBoundaryVersionRepository versions;
    @Autowired RegulatoryBoundaryRunRepository runs;
    @Autowired BoundaryEvidenceRepository evidence;
    @Autowired TaskRunService tasks;

    @Test
    void sameBriefCreatesOneRunAndBoundaryLeaseCanRecover() {
        Context context = context();
        var first = application.start(context.owner.getId(), context.project.getId(), context.brief.getId());
        var replay = application.start(context.owner.getId(), context.project.getId(), context.brief.getId());
        assertThat(replay.runId()).isEqualTo(first.runId());
        var claim = tasks.claim(first.jobId(), "worker-one", Duration.ZERO, Duration.ofMinutes(2));
        tasks.startExecution(first.jobId(), claim.taskAttemptId(), claim.claimToken());
        assertThat(tasks.recoverExpired(Duration.ZERO, List.of(TaskType.REGULATORY_BOUNDARY_GENERATION))).isEqualTo(1);
        var recovered = tasks.claimNext(TaskType.REGULATORY_BOUNDARY_GENERATION, "worker-two",
            Duration.ofMinutes(1), Duration.ofMinutes(2));
        assertThat(recovered).isNotNull();
        assertThat(tasks.getOwnedForWorker(first.jobId()).getState()).isEqualTo(TaskRunState.RUNNING);
    }

    @Test
    void evidenceContentAndBoundaryVersionAreUniquePerContract() {
        Context context = context();
        RegulatoryBoundaryRun run = terminalRun(context);
        RegulatoryBoundaryVersion version = versions.saveAndFlush(RegulatoryBoundaryVersion.create(run, 1,
            RegulatoryBoundaryVersion.Status.READY, "{\"status\":\"READY\"}", "sha256:" + "b".repeat(64)));
        BoundaryEvidence first = BoundaryEvidence.create(version, "EVD-1", "OFFICIAL_LAW", "법률", "제1조", "제목",
            "발췌", "요약", "관련성", "2026-01-01", "https://www.law.go.kr/a", "COMPLETE",
            LocalDateTime.now(), "sha256:" + "c".repeat(64));
        evidence.saveAndFlush(first);
        assertThatThrownBy(() -> evidence.saveAndFlush(BoundaryEvidence.create(version, "EVD-2", "OFFICIAL_LAW",
            "법률", "제1조", "제목2", "발췌", "요약2", "관련성2", "2026-01-01",
            "https://www.law.go.kr/a", "COMPLETE", LocalDateTime.now(), "sha256:" + "c".repeat(64))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void projectIsolationAndBriefChangeStaleAreEnforced() {
        Context one = context(); Context two = context();
        assertThatThrownBy(() -> application.start(one.owner.getId(), one.project.getId(), two.brief.getId()))
            .isInstanceOf(BusinessException.class);
        RegulatoryBoundaryRun run = terminalRun(one);
        RegulatoryBoundaryVersion old = versions.saveAndFlush(RegulatoryBoundaryVersion.create(run, 1,
            RegulatoryBoundaryVersion.Status.READY,
            "{\"status\":\"READY\",\"conflicts\":[],\"userActionOptions\":[],\"sourceWarnings\":[]}",
            "sha256:" + "d".repeat(64)));
        OpportunityBriefVersion next = briefs.createDraft(one.owner.getId(), one.project.getId(),
            one.conversationId, one.brief.getId(), "{\"targetRegion\":\"US\"}", List.of());
        briefs.confirm(one.owner.getId(), one.project.getId(), next.getId());
        assertThat(application.current(one.owner.getId(), one.project.getId()).version()).isNull();
        assertThat(versions.findById(old.getId()).orElseThrow().getStatus())
            .isEqualTo(RegulatoryBoundaryVersion.Status.STALE);
    }

    private RegulatoryBoundaryRun terminalRun(Context context) {
        RegulatoryBoundaryRun run = foundation.createRun(context.owner.getId(), context.project.getId(), context.brief.getId(), null);
        foundation.start(context.owner.getId(), context.project.getId(), run.getId());
        foundation.succeed(context.owner.getId(), context.project.getId(), run.getId());
        return runs.findByIdAndProjectIdAndDeletedAtIsNull(run.getId(), context.project.getId()).orElseThrow();
    }

    private Context context() {
        String suffix = UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("boundary-pg-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "boundary-pg", null, "AI"));
        var conversation = conversations.create(owner.getId(), project.getId(), null);
        OpportunityBriefVersion brief = briefs.createDraft(owner.getId(), project.getId(), conversation.getId(), null,
            "{\"targetRegion\":\"KR\"}", List.of(new OpportunityBriefService.FieldInput(
                "targetRegion", "\"KR\"", FieldDecisionStatus.LOCKED, FieldSourceType.USER_CONFIRMED, "user")));
        briefs.confirm(owner.getId(), project.getId(), brief.getId());
        return new Context(owner, project, conversation.getId(), brief);
    }
    private record Context(User owner, Project project, Long conversationId, OpportunityBriefVersion brief) { }
}
