package com.aivle.backend.marketing.content;

import com.jayway.jsonpath.JsonPath;
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
import java.time.LocalDateTime;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketingContentApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired ServiceSettingRepository settings;
    @Autowired BaselinePersonaRepository personas;
    @Autowired ClusterPersonaPolicyRepository personaPolicies;

    private User owner;
    private User other;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = users.saveAndFlush(User.register(
            "marketing-owner",
            "marketing-owner@example.com",
            "hash",
            "마케팅 소유자",
            null, null, null
        ));
        other = users.saveAndFlush(User.register(
            "marketing-other",
            "marketing-other@example.com",
            "hash",
            "다른 사용자",
            null, null, null
        ));
        project = projects.saveAndFlush(Project.create(
            owner,
            "검증된 서비스",
            "고객의 반복 업무를 줄이는 서비스",
            "SaaS"
        ));
    }

    @Test
    void createsAutosavesVersionsListsAndSoftDeletesContent() throws Exception {
        String created = mockMvc.perform(owner(post(path()))
                .header("X-Request-Id", "marketing-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("SQUARE_1080", null, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content.currentVersion").value(1))
            .andExpect(jsonPath("$.data.generationMethod").value("VALIDATION_TEMPLATE"))
            .andExpect(jsonPath("$.data.aiGenerated").value(false))
            .andExpect(jsonPath("$.data.sourceSnapshotJson").value(
                org.hamcrest.Matchers.containsString("검증된 서비스")
            ))
            .andReturn().getResponse().getContentAsString();
        Number contentId = JsonPath.read(created, "$.data.content.id");
        Number entityVersion = JsonPath.read(created, "$.data.entityVersion");

        mockMvc.perform(owner(patch(path() + "/{contentId}", contentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(entityVersion.longValue())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.current.headline").value("직접 편집한 헤드라인"));

        mockMvc.perform(owner(post(path() + "/{contentId}/versions", contentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody("두 번째 버전")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.versionNumber").value(2));

        mockMvc.perform(owner(get(path() + "/{contentId}/versions", contentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].versionNumber").value(2));

        mockMvc.perform(owner(post(path() + "/{contentId}/draft-copy", contentId)
                .queryParam("alternative", "0")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.generationMethod").value("VALIDATION_TEMPLATE"))
            .andExpect(jsonPath("$.data.aiGenerated").value(false));

        mockMvc.perform(owner(delete(path() + "/{contentId}", contentId)))
            .andExpect(status().isNoContent());
        mockMvc.perform(owner(get(path())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsOtherOwnerAndInvalidCustomSize() throws Exception {
        String created = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("SQUARE_1080", null, null)))
            .andReturn().getResponse().getContentAsString();
        Number contentId = JsonPath.read(created, "$.data.content.id");

        mockMvc.perform(other(get(path() + "/{contentId}", contentId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("MARKETING_CONTENT_ACCESS_DENIED"));

        mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("CUSTOM", null, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MARKETING_CONTENT_INVALID_SIZE"));
    }

    @Test
    void maintenanceBlocksWritesButKeepsExistingContentReadable() throws Exception {
        String created = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("SQUARE_1080", null, null)))
            .andReturn().getResponse().getContentAsString();
        Number contentId = JsonPath.read(created, "$.data.content.id");
        settings.saveAndFlush(new ServiceSetting(
            ServiceSettingKey.MAINTENANCE_MODE.name(),
            "true",
            owner.getId(),
            LocalDateTime.now()
        ));

        mockMvc.perform(owner(get(path() + "/{contentId}", contentId)))
            .andExpect(status().isOk());
        mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("SQUARE_1080", null, null)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("MAINTENANCE_MODE_ENABLED"));
        mockMvc.perform(owner(post(path() + "/{contentId}/draft-copy", contentId)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("MAINTENANCE_MODE_ENABLED"));
    }

    @Test
    void capturesPanelAndMarketSourcesAndRefreshesExplicitly() throws Exception {
        BaselinePersona persona = personas.findAll().get(0);
        personaPolicies.saveAndFlush(ClusterPersonaPolicy.create(
            persona,
            true,
            1,
            owner.getId(),
            LocalDateTime.now()
        ));
        String panelCreated = mockMvc.perform(owner(post(panelPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"마케팅 연결 인터뷰",
                      "purpose":"MESSAGE_REACTION",
                      "personaIds":[%d],
                      "questions":["관심 가치는 무엇인가요?","구매 우려는 무엇인가요?","어떤 문구가 좋은가요?"]
                    }
                    """.formatted(persona.getId())))
            .andReturn().getResponse().getContentAsString();
        Number panelId = JsonPath.read(panelCreated, "$.data.interview.id");
        mockMvc.perform(owner(post(panelPath() + "/{id}/run", panelId)))
            .andExpect(status().isOk());

        String marketCreated = mockMvc.perform(owner(post(marketPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"마케팅 연결 시장 반응",
                      "personaIds":[%d],
                      "messages":[{"id":"A","text":"빠르고 간편하게 검증하세요"}],
                      "primaryChannel":"SOCIAL",
                      "panelInterviewId":%d
                    }
                    """.formatted(persona.getId(), panelId.longValue())))
            .andReturn().getResponse().getContentAsString();
        Number marketId = JsonPath.read(marketCreated, "$.data.prediction.id");
        mockMvc.perform(owner(post(marketPath() + "/{id}/run", marketId)))
            .andExpect(status().isOk());

        String created = mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(
                    "SQUARE_1080",
                    null,
                    null,
                    panelId.longValue(),
                    marketId.longValue()
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content.panelInterviewId").value(panelId))
            .andExpect(jsonPath("$.data.content.marketResponseId").value(marketId))
            .andExpect(jsonPath("$.data.sourceSnapshotJson", containsString("commonNeeds")))
            .andExpect(jsonPath("$.data.sourceSnapshotJson", containsString("bestMessage")))
            .andExpect(jsonPath("$.data.copyEvidence.length()", greaterThan(0)))
            .andReturn().getResponse().getContentAsString();
        Number contentId = JsonPath.read(created, "$.data.content.id");

        mockMvc.perform(owner(post(path() + "/{id}/source-refresh", contentId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"panelInterviewId":%d,"marketResponseId":null,"generateDraft":false}
                    """.formatted(panelId.longValue())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.sourceSnapshotVersion").value(2))
            .andExpect(jsonPath("$.data.content.marketResponseId").value(
                org.hamcrest.Matchers.nullValue()
            ))
            .andExpect(jsonPath("$.data.content.currentVersion").value(1));
    }

    @Test
    void rejectsDraftAndOtherProjectValidationSources() throws Exception {
        BaselinePersona persona = personas.findAll().get(0);
        personaPolicies.saveAndFlush(ClusterPersonaPolicy.create(
            persona, true, 1, owner.getId(), LocalDateTime.now()
        ));
        String panelCreated = mockMvc.perform(owner(post(panelPath()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"미완료 인터뷰","purpose":"PROBLEM_DISCOVERY",
                     "personaIds":[%d],"questions":["질문1","질문2","질문3"]}
                    """.formatted(persona.getId())))
            .andReturn().getResponse().getContentAsString();
        Number panelId = JsonPath.read(panelCreated, "$.data.interview.id");

        mockMvc.perform(owner(post(path()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("SQUARE_1080", null, null, panelId.longValue(), null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value(
                "MARKETING_VALIDATION_RESULT_NOT_COMPLETED"
            ));

        Project otherProject = projects.saveAndFlush(Project.create(
            owner, "다른 프로젝트", "다른 검증 결과", "SaaS"
        ));
        mockMvc.perform(owner(post(
                "/api/v1/projects/" + otherProject.getId() + "/marketing-contents"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("SQUARE_1080", null, null, panelId.longValue(), null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value(
                "MARKETING_PANEL_INTERVIEW_INVALID"
            ));
    }

    private String path() {
        return "/api/v1/projects/" + project.getId() + "/marketing-contents";
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

    private String createBody(String format, Integer width, Integer height) {
        return createBody(format, width, height, null, null);
    }

    private String createBody(
        String format,
        Integer width,
        Integer height,
        Long panelInterviewId,
        Long marketResponseId
    ) {
        String dimensions = width == null
            ? "\"width\":null,\"height\":null"
            : "\"width\":%d,\"height\":%d".formatted(width, height);
        String sources = """
              "panelInterviewId":%s,
              "marketResponseId":%s,
            """.formatted(
                panelInterviewId == null ? "null" : panelInterviewId,
                marketResponseId == null ? "null" : marketResponseId
            );
        return """
            {
              "title":"첫 광고 시안",
              "purpose":"PRODUCT_INTRODUCTION",
              "channel":"SOCIAL",
              "format":"%s",
              %s,
              %s
              "targetOffer":"반복 업무를 줄이는 서비스",
              "brandName":"Venture Verify",
              "brandColor":"#0f8878",
              "tone":"PROFESSIONAL",
              "template":"HERO_CENTER"
            }
            """.formatted(format, dimensions, sources);
    }

    private String updateBody(long entityVersion) {
        return """
            {
              "title":"첫 광고 시안",
              "purpose":"PRODUCT_INTRODUCTION",
              "channel":"SOCIAL",
              "format":"SQUARE_1080",
              "width":1080,
              "height":1080,
              "entityVersion":%d,
              "draft":%s
            }
            """.formatted(entityVersion, draftBody("직접 편집한 헤드라인"));
    }

    private String draftBody(String headline) {
        return """
            {
              "headline":"%s",
              "subheadline":"검증 결과 기반 메시지",
              "bodyCopy":"사용자가 직접 수정할 수 있는 본문입니다.",
              "callToAction":"자세히 보기",
              "supportingText":"#검증 #서비스",
              "visualStyle":"PROFESSIONAL",
              "colorTheme":"VALIDATION_DEFAULT",
              "layoutTemplate":"HERO_CENTER",
              "backgroundType":"GRADIENT",
              "backgroundValue":"#0f8878,#17363a",
              "accentColor":"#0f8878",
              "textColor":"#ffffff",
              "textAlignment":"CENTER",
              "headlineSize":72,
              "showCta":true,
              "showPersonaTag":true,
              "contentJson":"{}"
            }
            """.formatted(headline);
    }
}
