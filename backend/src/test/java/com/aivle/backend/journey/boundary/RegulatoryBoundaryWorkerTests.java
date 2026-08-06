package com.aivle.backend.journey.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEventRepository;
import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.FieldSourceType;
import com.aivle.backend.journey.brief.OpportunityBriefService;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class RegulatoryBoundaryWorkerTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ConversationService conversations;
    @Autowired OpportunityBriefService briefs;
    @Autowired RegulatoryBoundaryApplicationService boundaries;
    @Autowired RegulatoryBoundaryWorker worker;
    @Autowired RegulatoryBoundaryVersionRepository versions;
    @Autowired TaskRunService tasks;
    @Autowired JobEventRepository events;
    @Autowired ObjectMapper mapper;
    @MockitoBean InternalAiExecutionClient client;

    @Test
    void adoptsDomainBeforePublishingTerminalEventAndDoesNotDuplicateVersion() {
        Started started = started("boundary-worker-success@example.com");
        when(client.execute(any(), anyString(), any())).thenAnswer(invocation -> response(
            invocation.getArgument(0), invocation.getArgument(1), validResult()));

        assertThat(worker.processOne()).isTrue();
        TaskRun task = tasks.getOwned(started.ownerId, started.projectId, started.jobId);
        assertThat(task.getState()).isEqualTo(TaskRunState.SUCCEEDED);
        RegulatoryBoundaryVersion version = versions.findByRunIdAndDeletedAtIsNull(started.runId).orElseThrow();
        assertThat(version.getStatus()).isEqualTo(RegulatoryBoundaryVersion.Status.READY);
        assertThat(events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            started.jobId, started.projectId).orElseThrow().getMessageKey()).isEqualTo("job.boundary.completed");
        assertThat(versions.countByProjectIdAndDeletedAtIsNull(started.projectId)).isEqualTo(1);
    }

    @Test
    void retryableFailureIsBoundedAndRequeuedWithoutCompletedEvent() {
        Started started = started("boundary-worker-retry@example.com");
        when(client.execute(any(), anyString(), any()))
            .thenThrow(new ExecutionFailure("DEPENDENCY_UNAVAILABLE", "MOLEG_DEPENDENCY_UNAVAILABLE", true));

        assertThat(worker.processOne()).isTrue();
        assertThat(tasks.getOwned(started.ownerId, started.projectId, started.jobId).getState())
            .isEqualTo(TaskRunState.QUEUED);
        assertThat(events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            started.jobId, started.projectId, 0).stream().map(value -> value.getMessageKey()))
            .contains("job.retry.scheduled").doesNotContain("job.boundary.completed");
        tasks.cancel(started.ownerId, started.projectId, started.jobId);
    }

    @Test
    void invalidProviderResultFailsPermanentlyWithoutDomainVersion() {
        Started started = started("boundary-worker-invalid@example.com");
        when(client.execute(any(), anyString(), any())).thenAnswer(invocation -> response(
            invocation.getArgument(0), invocation.getArgument(1), mapper.createObjectNode().put("providerBody", "raw")));

        assertThat(worker.processOne()).isTrue();
        assertThat(tasks.getOwned(started.ownerId, started.projectId, started.jobId).getState())
            .isEqualTo(TaskRunState.FAILED);
        assertThat(versions.findByRunIdAndDeletedAtIsNull(started.runId)).isEmpty();
        assertThat(events.findTopByJobIdAndProjectIdAndDeletedAtIsNullOrderBySequenceDesc(
            started.jobId, started.projectId).orElseThrow().getMessageKey()).isEqualTo("job.boundary.failed");
    }

    private Started started(String email) {
        User owner = users.saveAndFlush(User.create(email, "hash", email));
        Project project = projects.saveAndFlush(Project.create(owner, email, null, "AI"));
        var conversation = conversations.create(owner.getId(), project.getId(), null);
        OpportunityBriefVersion brief = briefs.createDraft(owner.getId(), project.getId(), conversation.getId(), null,
            "{\"targetRegion\":\"KR\"}", List.of(
                new OpportunityBriefService.FieldInput("targetRegion", "\"KR\"", FieldDecisionStatus.LOCKED,
                    FieldSourceType.USER_CONFIRMED, "user")));
        briefs.confirm(owner.getId(), project.getId(), brief.getId());
        var start = boundaries.start(owner.getId(), project.getId(), brief.getId());
        return new Started(owner.getId(), project.getId(), start.runId(), start.jobId());
    }

    private ExecutionResponse response(TaskRun run, String attemptId, tools.jackson.databind.JsonNode result) {
        return new ExecutionResponse("1.0", run.getTaskType().name(), "1.0", run.getId(), attemptId,
            run.getCorrelationId(), run.getInputHash(), "1.0", result,
            mapper.createArrayNode(), mapper.createArrayNode(), null);
    }

    private tools.jackson.databind.JsonNode validResult() {
        return mapper.readTree("""
            {"taskType":"REGULATORY_BOUNDARY_GENERATION","sourceStatus":"COMPLETE","registryVersion":"legal-registry-v1",
             "routes":[],"evidence":[{"evidenceId":"EVD-001","sourceType":"OFFICIAL_LAW","lawName":"개인정보 보호법",
             "article":"제15조","title":"개인정보의 수집","effectiveDate":"2026-01-01","officialUrl":"https://www.law.go.kr/a",
             "excerpt":"공식 발췌","plainSummary":"필요한 정보만 수집한다.","whyRelevant":"위치정보 처리",
             "sourceStatus":"COMPLETE","retrievedAt":"2026-08-05T00:00:00Z","contentHash":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],
             "rules":[{"ruleId":"RULE-1","ruleType":"REQUIRED_CONTROL","structureKey":"locationData","title":"위치정보 최소 처리",
             "description":"추천 기능의 통제","normalizedRequirement":"추천에 필요한 최소 위치정보만 목적 범위에서 처리한다.",
             "evidenceIds":["EVD-001"],"severity":"HIGH","sourceStatus":"COMPLETE","appliesWhen":{"collects":true},
             "userFacingReason":"불필요한 추적 방지","alternatives":[],"requiredQualifications":[],"requiredPartnerRole":null,
             "requiredDisclosure":"수집 목적 고지","affectedBriefFields":["regulatorySensitiveActivities"],
             "professionalReviewRecommended":false,"userActionOptions":[]}],"questions":[],"conflicts":[],"status":"READY",
             "userActionOptions":[],"sourceWarnings":[]}
            """);
    }

    private record Started(Long ownerId, Long projectId, Long runId, String jobId) { }
}
