package com.aivle.backend.project;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jwt-test")
class ProjectApiIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from audit_events");
        jdbcTemplate.update("delete from refresh_tokens");
        jdbcTemplate.update("delete from projects");
        jdbcTemplate.update("delete from users");
    }

    @AfterEach
    void cleanAfter() {
        clean();
    }

    @Test
    void createsListsAndReadsOwnedProject() throws Exception {
        String accessToken = signup("owner@example.com");

        String response = mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "검증 프로젝트",
                      "description": "실제 API 연결",
                      "industryCategory": "SaaS"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("검증 프로젝트"))
            .andExpect(jsonPath("$.data.stage").value("DOCUMENT"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();

        Number projectId = JsonPath.read(response, "$.data.id");
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(projectId))
            .andExpect(jsonPath("$.data[0].updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.description").value("실제 API 연결"));
    }

    @Test
    void hidesAnotherUsersProjectAsNotFound() throws Exception {
        String ownerToken = signup("owner@example.com");
        String otherToken = signup("other@example.com");
        String response = mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"소유자 프로젝트\"}"))
            .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.data.id");

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidProjectWithoutPersistingPlaceholderData() throws Exception {
        String accessToken = signup("owner@example.com");
        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("title"));
    }

    @Test
    void softDeletesOwnedProjectAndRemovesItFromTheHub() throws Exception {
        String accessToken = signup("owner@example.com");
        String response = mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Project to remove\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.data.id");

        mockMvc.perform(delete("/api/v1/projects/{projectId}", projectId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsNormalizedDuplicateProjectTitlesButAllowsAnotherOwnerAndSoftDeletedTitle() throws Exception {
        String ownerToken = signup("duplicate-owner@example.com");
        String otherToken = signup("duplicate-other@example.com");
        String response = mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Project  Alpha\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Number projectId = JsonPath.read(response, "$.data.id");

        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" project alpha \"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PROJECT_NAME_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"PROJECT ALPHA\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/projects/{projectId}", projectId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Project Alpha\"}"))
            .andExpect(status().isCreated());
    }

    private String signup(String email) throws Exception {
        String username = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "email": "%s",
                      "password": "a safe project passphrase",
                      "displayName": "Tester"
                    }
                    """.formatted(username, email)))
            .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "a safe project passphrase"
                    }
                    """.formatted(username)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.tokens.accessToken");
    }
}
