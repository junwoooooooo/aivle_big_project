package com.aivle.backend;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DomainFoundationIntegrationTests {
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired JdbcClient jdbcClient;
    @Autowired MockMvc mockMvc;

    @Test
    void userRepositoryFindsEmailIgnoringCase() {
        userRepository.saveAndFlush(User.create("owner@example.com", "hashed", "owner"));
        assertThat(userRepository.existsByEmailIgnoreCase("OWNER@EXAMPLE.COM")).isTrue();
        assertThat(userRepository.findByEmailIgnoreCase("Owner@Example.com")).isPresent();
    }

    @Test
    void projectStartsAtDocumentAndDraft() {
        User owner = userRepository.saveAndFlush(User.create("owner@example.com", "hashed", "owner"));
        Project project = projectRepository.saveAndFlush(Project.create(owner, "idea", null, "AI"));

        assertThat(project.getStage()).isEqualTo(ProjectStage.DOCUMENT);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    void projectEnumIsPersistedAsString() {
        User owner = userRepository.saveAndFlush(User.create("owner@example.com", "hashed", "owner"));
        Project project = projectRepository.saveAndFlush(Project.create(owner, "idea", null, "AI"));
        String stored = jdbcClient.sql("select stage from projects where id = :id")
                .param("id", project.getId()).query(String.class).single();
        assertThat(stored).isEqualTo("DOCUMENT");
    }

    @Test
    void softDeletedProjectIsExcludedByExplicitRepositoryQuery() {
        User owner = userRepository.saveAndFlush(User.create("owner@example.com", "hashed", "owner"));
        Project project = projectRepository.saveAndFlush(Project.create(owner, "idea", null, null));
        project.softDelete();
        projectRepository.saveAndFlush(project);
        assertThat(projectRepository.findByIdAndDeletedAtIsNull(project.getId())).isEmpty();
        assertThat(projectRepository
                .findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(owner.getId()))
                .isEmpty();
    }

    @Test
    void jobProgressRejectsOutOfRangeValues() {
        AnalysisJob job = AnalysisJob.queued(null, JobType.MARKET_ANALYSIS, "{}");
        assertThatThrownBy(() -> job.updateProgress(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> job.updateProgress(101)).isInstanceOf(IllegalArgumentException.class);
        job.updateProgress(100);
        assertThat(job.getProgress()).isEqualTo(100);
    }

    @Test
    void missingCurrentUserReturnsStandardErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void h2ServiceSchemaContainsCoreTables() {
        Integer userCount = jdbcClient.sql("select count(*) from users")
                .query(Integer.class).single();
        Integer projectCount = jdbcClient.sql("select count(*) from projects")
                .query(Integer.class).single();
        Integer jobCount = jdbcClient.sql("select count(*) from analysis_jobs")
                .query(Integer.class).single();
        assertThat(userCount).isZero();
        assertThat(projectCount).isZero();
        assertThat(jobCount).isZero();
    }
}
