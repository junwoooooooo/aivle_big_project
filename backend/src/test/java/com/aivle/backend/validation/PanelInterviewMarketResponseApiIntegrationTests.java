package com.aivle.backend.validation;

import com.aivle.backend.admin.ServiceSetting;
import com.aivle.backend.admin.ServiceSettingKey;
import com.aivle.backend.admin.ServiceSettingRepository;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.entity.ClusterPersonaPolicy;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.persona.catalog.repository.ClusterPersonaPolicyRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PanelInterviewMarketResponseApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired BaselinePersonaRepository personas;
    @Autowired ClusterPersonaPolicyRepository policies;
    @Autowired ServiceSettingRepository settings;

    private User owner;
    private User other;
    private Project project;
    private List<BaselinePersona> enabledPersonas;

    @BeforeEach
    void setUp() {
        owner = users.saveAndFlush(User.register(
            "validation-owner",
            "validation-owner@example.com",
            "hash",
            "검증 소유자",
            null, null, null
        ));
        other = users.saveAndFlush(User.register(
            "validation-other",
            "validation-other@example.com",
            "hash",
            "다른 사용자",
            null, null, null
        ));
        project = projects.saveAndFlush(Project.create(
            owner,
            "고객 검증 서비스",
            "사업 아이디어를 빠르고 명확하게 검증합니다.",
            "SaaS"
        ));
        enabledPersonas = personas.findAll().stream().limit(4).toList();
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < enabledPersonas.size(); index++) {
            policies.save(ClusterPersonaPolicy.create(
                enabledPersonas.get(index),
                true,
                index + 1,
                owner.getId(),
                now
            ));
        }
        policies.flush();
    }

    @Test
    void panelInterviewCreatesRunsSummarizesAndSoftDeletes() throws Exception {
        String created = mockMvc.perform(owner(post(panelPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(panelBody(enabledPersonas.subList(0, 2))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.interview.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.interview.personaCount").value(2))
            .andExpect(jsonPath("$.data.interview.questionCount").value(3))
            .andReturn().getResponse().getContentAsString();
        Number interviewId = JsonPath.read(created, "$.data.interview.id");

        mockMvc.perform(owner(post(panelPath() + "/{id}/run", interviewId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.interview.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.answers.length()").value(6))
            .andExpect(jsonPath("$.data.answers[0].sentiment", anyOf(
                is("POSITIVE"), is("NEUTRAL"), is("NEGATIVE"), is("MIXED")
            )))
            .andExpect(jsonPath("$.data.summary.commonNeeds.length()").value(3))
            .andExpect(jsonPath("$.data.disclaimer", containsString("실제 고객 조사 결과를 대체하지 않습니다")));

        mockMvc.perform(owner(delete(panelPath() + "/{id}", interviewId)))
            .andExpect(status().isNoContent());
        mockMvc.perform(owner(get(panelPath())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void panelInterviewEnforcesPersonaAndQuestionLimits() throws Exception {
        mockMvc.perform(owner(post(panelPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(panelBody(enabledPersonas)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("PANEL_INTERVIEW_PERSONA_LIMIT_EXCEEDED"));

        String oneQuestion = """
            {"title":"질문 부족","purpose":"PROBLEM_DISCOVERY",
             "personaIds":[%d],"questions":["질문 하나"]}
            """.formatted(enabledPersonas.get(0).getId());
        mockMvc.perform(owner(post(panelPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(oneQuestion))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("PANEL_INTERVIEW_QUESTION_REQUIRED"));
    }

    @Test
    void marketResponseIsDeterministicAndCanUsePanelSummary() throws Exception {
        String panel = mockMvc.perform(owner(post(panelPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(panelBody(enabledPersonas.subList(0, 1))))
            .andReturn().getResponse().getContentAsString();
        Number interviewId = JsonPath.read(panel, "$.data.interview.id");
        mockMvc.perform(owner(post(panelPath() + "/{id}/run", interviewId)))
            .andExpect(status().isOk());

        String created = mockMvc.perform(owner(post(marketPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(marketBody(interviewId.longValue(), 1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.prediction.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        Number predictionId = JsonPath.read(created, "$.data.prediction.id");

        String first = mockMvc.perform(owner(post(marketPath() + "/{id}/run", predictionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prediction.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.results[0].scores.interest", allOf(
                greaterThanOrEqualTo(20), lessThanOrEqualTo(95)
            )))
            .andExpect(jsonPath("$.data.sourceSnapshot.panelInterview.id").value(interviewId))
            .andReturn().getResponse().getContentAsString();
        Integer interest = JsonPath.read(first, "$.data.results[0].scores.interest");

        mockMvc.perform(owner(post(marketPath() + "/{id}/run", predictionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results[0].scores.interest").value(interest));
    }

    @Test
    void marketResponseEnforcesMessageLimitOwnershipAndMaintenance() throws Exception {
        mockMvc.perform(owner(post(marketPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(marketBody(null, 4)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MARKET_RESPONSE_MESSAGE_LIMIT_EXCEEDED"));

        String created = mockMvc.perform(owner(post(marketPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(marketBody(null, 1)))
            .andReturn().getResponse().getContentAsString();
        Number predictionId = JsonPath.read(created, "$.data.prediction.id");

        mockMvc.perform(other(get(marketPath() + "/{id}", predictionId)))
            .andExpect(status().isForbidden());

        settings.saveAndFlush(new ServiceSetting(
            ServiceSettingKey.MAINTENANCE_MODE.name(),
            "true",
            owner.getId(),
            LocalDateTime.now()
        ));
        mockMvc.perform(owner(get(marketPath() + "/{id}", predictionId)))
            .andExpect(status().isOk());
        mockMvc.perform(owner(post(marketPath() + "/{id}/run", predictionId)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("MAINTENANCE_MODE_ENABLED"));
    }

    private String panelPath() {
        return "/api/v1/projects/" + project.getId() + "/panel-interviews";
    }

    private String marketPath() {
        return "/api/v1/projects/" + project.getId() + "/market-responses";
    }

    private MockHttpServletRequestBuilder owner(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", owner.getId()).header("X-User-Role", "USER");
    }

    private MockHttpServletRequestBuilder other(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", other.getId()).header("X-User-Role", "USER");
    }

    private String panelBody(List<BaselinePersona> values) {
        String ids = values.stream().map(value -> value.getId().toString())
            .reduce((left, right) -> left + "," + right).orElse("");
        return """
            {
              "title":"가치 제안 예상 인터뷰",
              "purpose":"VALUE_PROPOSITION",
              "personaIds":[%s],
              "questions":[
                "가장 관심을 끄는 부분은 무엇인가요?",
                "필요하지 않다고 느낄 이유는 무엇인가요?",
                "결정할 때 가장 중요한 조건은 무엇인가요?"
              ]
            }
            """.formatted(ids);
    }

    private String marketBody(Long panelId, int messageCount) {
        String messages = java.util.stream.IntStream.range(0, messageCount)
            .mapToObj(index -> """
                {"id":"%s","text":"빠르고 간편하게 사업 아이디어를 검증하세요 %d"}
                """.formatted(String.valueOf((char) ('A' + index)), index))
            .reduce((left, right) -> left + "," + right).orElse("");
        String panel = panelId == null ? "null" : panelId.toString();
        return """
            {
              "title":"메시지 반응 비교",
              "personaIds":[%d],
              "messages":[%s],
              "priceContext":"월 구독형",
              "primaryChannel":"SOCIAL",
              "panelInterviewId":%s
            }
            """.formatted(enabledPersonas.get(0).getId(), messages, panel);
    }
}
