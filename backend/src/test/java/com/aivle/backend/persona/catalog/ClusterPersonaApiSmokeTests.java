package com.aivle.backend.persona.catalog;

import com.aivle.backend.admin.ServiceSetting;
import com.aivle.backend.admin.ServiceSettingKey;
import com.aivle.backend.admin.ServiceSettingRepository;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClusterPersonaApiSmokeTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired BaselinePersonaRepository personas;
    @Autowired ServiceSettingRepository settings;

    private User admin;
    private User owner;
    private Project project;
    private List<BaselinePersona> catalog;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        admin = User.register(
            "e3-admin",
            "e3-admin@example.com",
            "test-password-hash",
            "E3 관리자",
            null,
            null,
            null
        );
        admin.updateRole(UserRole.ADMIN, null, now);
        admin = users.saveAndFlush(admin);
        owner = users.saveAndFlush(User.register(
            "e3-owner",
            "e3-owner@example.com",
            "test-password-hash",
            "E3 사용자",
            null,
            null,
            null
        ));
        project = projects.saveAndFlush(Project.create(
            owner,
            "E3 Persona Smoke",
            "API smoke project",
            "TEST"
        ));
        catalog = personas.findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
            BaselinePersonaCatalog.VERSION
        );
    }

    @Test
    void publicPolicyAndAuthorizationBoundaryExposeDefaultOffState() throws Exception {
        mockMvc.perform(get("/api/v1/service-policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clusterPersonaEnabled").value(false));

        mockMvc.perform(get("/api/v1/admin/personas"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(user(get("/api/v1/admin/personas")))
            .andExpect(status().isForbidden());
        mockMvc.perform(admin(get("/api/v1/admin/personas")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(56));

        mockMvc.perform(user(get(
                "/api/v1/projects/{projectId}/personas/available",
                project.getId()
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false))
            .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(user(put(
                "/api/v1/projects/{projectId}/personas/selection",
                project.getId()
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personaId\":%d}".formatted(catalog.get(0).getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CLUSTER_PERSONA_DISABLED"));
    }

    @Test
    void selectionAndRecommendationStateRemainSeparateWhenSelectionIsHidden()
        throws Exception {
        BaselinePersona selected = catalog.get(0);
        BaselinePersona fallback = catalog.get(1);
        setVisibility(selected, true);
        setVisibility(fallback, true);
        setClusterPersonaEnabled(true);

        mockMvc.perform(user(put(
                "/api/v1/projects/{projectId}/personas/selection",
                project.getId()
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personaId\":%d}".formatted(selected.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].selected").value(true));

        setVisibility(selected, false);

        mockMvc.perform(user(get(
                "/api/v1/projects/{projectId}/personas/available",
                project.getId()
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(fallback.getId()))
            .andExpect(jsonPath("$.data.selectedUnavailable.id").value(selected.getId()))
            .andExpect(jsonPath("$.data.selectedUnavailable.selected").value(true))
            .andExpect(jsonPath("$.data.selectedUnavailable.available").value(false));

        mockMvc.perform(user(put(
                "/api/v1/projects/{projectId}/personas/selection",
                project.getId()
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personaId\":%d}".formatted(selected.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CLUSTER_PERSONA_NOT_ALLOWED"));
    }

    @Test
    void maximumVisibilityOrderingAndLastVisiblePolicyAreEnforced() throws Exception {
        List<BaselinePersona> visible = catalog.subList(0, 6);
        for (BaselinePersona persona : visible) {
            setVisibility(persona, true);
        }

        setVisibilityExpecting(
            catalog.get(6),
            true,
            "CLUSTER_PERSONA_LIMIT_EXCEEDED"
        );

        List<BaselinePersona> reversedPersonas = new ArrayList<>(visible);
        Collections.reverse(reversedPersonas);
        List<Long> reversed = reversedPersonas.stream()
            .map(BaselinePersona::getId)
            .toList();
        String order = reversed.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        mockMvc.perform(admin(put("/api/v1/admin/personas/order"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"personaIds":[%s],"reason":"E3 순서 검증"}
                    """.formatted(order)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(reversed.get(0)));

        setClusterPersonaEnabled(true);
        for (int index = 0; index < 5; index++) {
            setVisibility(visible.get(index), false);
        }
        setVisibilityExpecting(
            visible.get(5),
            false,
            "CLUSTER_PERSONA_SELECTION_REQUIRED"
        );
    }

    @Test
    void maintenanceModeBlocksProjectPersonaSelection() throws Exception {
        BaselinePersona persona = catalog.get(0);
        setVisibility(persona, true);
        setClusterPersonaEnabled(true);
        settings.saveAndFlush(new ServiceSetting(
            ServiceSettingKey.MAINTENANCE_MODE.name(),
            "true",
            admin.getId(),
            LocalDateTime.now()
        ));

        mockMvc.perform(user(put(
                "/api/v1/projects/{projectId}/personas/selection",
                project.getId()
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personaId\":%d}".formatted(persona.getId())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("MAINTENANCE_MODE_ENABLED"));
    }

    private void setVisibility(BaselinePersona persona, boolean enabled) throws Exception {
        mockMvc.perform(admin(patch(
                "/api/v1/admin/personas/{personaId}",
                persona.getId()
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"enabled":%s,"reason":"E3 API smoke"}
                    """.formatted(enabled)))
            .andExpect(status().isOk());
    }

    private void setVisibilityExpecting(
        BaselinePersona persona,
        boolean enabled,
        String errorCode
    ) throws Exception {
        mockMvc.perform(admin(patch(
                "/api/v1/admin/personas/{personaId}",
                persona.getId()
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"enabled":%s,"reason":"E3 정책 경계 검증"}
                    """.formatted(enabled)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value(errorCode));
    }

    private void setClusterPersonaEnabled(boolean enabled) throws Exception {
        mockMvc.perform(admin(patch(
                "/api/v1/admin/settings/CLUSTER_PERSONA_ENABLED"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"value":"%s","reason":"E3 API smoke"}
                    """.formatted(enabled)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value(enabled));
    }

    private MockHttpServletRequestBuilder admin(
        MockHttpServletRequestBuilder request
    ) {
        return request
            .header("X-User-Id", admin.getId())
            .header("X-User-Role", "ADMIN");
    }

    private MockHttpServletRequestBuilder user(
        MockHttpServletRequestBuilder request
    ) {
        return request
            .header("X-User-Id", owner.getId())
            .header("X-User-Role", "USER");
    }
}
