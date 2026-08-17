package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketing.api.MarketingApiModels.CreateRequest;
import com.aivle.backend.pipeline.marketing.api.MarketingApiModels.Length;
import com.aivle.backend.pipeline.marketing.application.MarketingContentService;
import com.aivle.backend.pipeline.marketing.application.MarketingSourceSnapshotService;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentType;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:marketing-taskrun-seam;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
@Transactional
class MarketingTaskRunCreationSeamTests {
    @Autowired MarketingContentService marketing;
    @Autowired TaskRunService taskRuns;
    @Autowired TaskRunRepository runRepository;
    @Autowired CanonicalInputHasher hasher;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @MockitoBean MarketingSourceSnapshotService sourceSnapshots;
    @MockitoBean CurrentConceptSourceResolver currentConcepts;

    @Test
    void publicCreateBuildsAQueuedTaskRunWithTheExactCanonicalHashAndSchema() {
        User owner = users.saveAndFlush(User.create("marketing-seam@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "marketing seam", null, null));
        MarketingSourceSnapshot source = MarketingSourceSnapshot.createPortfolio(
            "source-1", project.getId(), "seed-1", 11L, "concept-1", 3, 4, "2.0",
            "sha256:" + "a".repeat(64), "{\"conceptName\":\"검증 사업\"}",
            owner.getId(), Instant.parse("2026-08-13T00:00:00Z")
        );
        when(sourceSnapshots.requireCurrent(project.getId())).thenReturn(source);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        BmPlanPreparationService.PlanView plan = mock(BmPlanPreparationService.PlanView.class);
        when(seed.getId()).thenReturn("seed-1");
        when(seed.getProjectId()).thenReturn(project.getId());
        when(selection.getId()).thenReturn(11L);
        when(selection.getHypothesisRevision()).thenReturn(3);
        when(plan.revision()).thenReturn(4);
        Source authority = new Source(selection, seed, plan);
        when(currentConcepts.require(eq(project.getId()), anyString())).thenReturn(authority);
        when(currentConcepts.currentOrNull(project.getId())).thenReturn(authority);

        var created = marketing.create(owner.getId(), project.getId(), new CreateRequest(
            MarketingContentService.REQUEST_CONTRACT, source.getId(),
            MarketingContentType.BLOG_INTRO, "blog", "출시 안내", "명확함",
            Length.SHORT, List.of("필수"), List.of("금지"), null, null
        ), "marketing-create-key", "marketing-correlation");

        TaskRun run = runRepository.findById(created.content().taskRunId()).orElseThrow();
        String recomputed = hasher.hash(
            TaskType.MARKETING_CONTENT_GENERATION, "1.0", "ko-KR", run.getInputSnapshot());
        assertThat(run.getState()).isEqualTo(TaskRunState.QUEUED);
        assertThat(run.getTaskSchemaVersion()).isEqualTo("1.0");
        assertThat(run.getInputHash()).isEqualTo(recomputed);

        TaskRun replay = taskRuns.create(
            owner.getId(), project.getId(), run.getTaskType(), run.getSubjectType(),
            run.getSubjectId(), run.getInputSnapshot(), run.getInputHash(),
            run.getIdempotencyKey(), run.getCorrelationId(), run.getMaxAttempts());
        assertThat(replay.getId()).isEqualTo(run.getId());
    }
}
