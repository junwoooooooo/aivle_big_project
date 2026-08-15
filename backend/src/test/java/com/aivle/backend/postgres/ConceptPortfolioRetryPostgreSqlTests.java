package com.aivle.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.domain.IdeaDecisionState;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("postgres")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConceptPortfolioRetryPostgreSqlTests extends PostgreSqlIntegrationTestSupport {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired IdeaBriefRepository briefs;
    @Autowired IdeaBriefFieldRepository fields;
    @Autowired ConceptPortfolioRunRepository runs;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate table users restart identity cascade");
    }

    @Test
    void failedCurrentRunIsFlushedBeforeFreshCurrentInsert() throws Exception {
        User owner = users.saveAndFlush(User.create(
            "retry-" + UUID.randomUUID() + "@example.com", "hashed", "retry-owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "retry-project", null, "AI"));
        IdeaBrief brief = IdeaBrief.initial(project, owner.getId());
        brief.updateOverview("사업 아이디어");
        brief.applyAssessment("검토 준비 완료", "[]", "[]", "READY_FOR_REVIEW", 100, HASH);
        brief.applySafetyAndInterpretation("ALLOW", "[]", "[]", "안전 확인 완료", "{}");
        brief.readyForReview();
        briefs.saveAndFlush(brief);
        fields.saveAllAndFlush(List.of(
            IdeaBriefField.userValue(brief, "ideaOverview", "사업 아이디어", IdeaDecisionState.LOCKED),
            IdeaBriefField.userValue(brief, "problem", "해결할 문제", IdeaDecisionState.LOCKED),
            IdeaBriefField.userValue(brief, "targetUsers", "예상 사용자", IdeaDecisionState.LOCKED)
        ));
        brief.confirm(HASH, "confirm-key", HASH);
        briefs.saveAndFlush(brief);

        ConceptPortfolioRun previous = ConceptPortfolioRun.queued(
            project, brief, 5, HASH, "failed-key", owner.getId());
        previous.markFailed("DEPENDENCY_UNAVAILABLE");
        runs.saveAndFlush(previous);

        mvc.perform(post("/api/v3/projects/{projectId}/concept-portfolio-runs", project.getId())
                .header("X-User-Id", owner.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"ideaBriefSnapshotId":"%s","maxConcepts":5,"idempotencyKey":"fresh-retry-key"}
                    """.formatted(brief.getId())))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.productStatus").value("QUEUED"));

        ConceptPortfolioRun oldHistory = runs.findById(previous.getId()).orElseThrow();
        ConceptPortfolioRun current = runs.findCurrentOwned(owner.getId(), project.getId()).orElseThrow();
        assertThat(oldHistory.isCurrent()).isFalse();
        assertThat(oldHistory.getProductStatus()).isEqualTo(ConceptPortfolioRunStatus.STALE);
        assertThat(current.getId()).isNotEqualTo(previous.getId());
        assertThat(current.isCurrent()).isTrue();
        assertThat(current.getProductStatus()).isEqualTo(ConceptPortfolioRunStatus.QUEUED);
        assertThat(jdbc.queryForObject("""
            select count(*) from concept_portfolio_runs
            where project_id = ? and is_current = true and deleted_at is null
            """, Integer.class, project.getId())).isEqualTo(1);
    }
}
