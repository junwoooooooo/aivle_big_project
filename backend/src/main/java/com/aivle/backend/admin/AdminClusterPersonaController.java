package com.aivle.backend.admin;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/personas")
@RequiredArgsConstructor
public class AdminClusterPersonaController {
    private final AdminAccessService access;
    private final AdminClusterPersonaService personas;
    private final AdminAuditService audits;

    @GetMapping
    public ApiResponse<List<AdminClusterPersonaService.PersonaPolicyResponse>> list(
        HttpServletRequest request
    ) {
        access.requireAdmin();
        return ApiResponse.success(personas.list(), requestId(request));
    }

    @PatchMapping("/{personaId}")
    public ApiResponse<AdminClusterPersonaService.PersonaPolicyResponse> visibility(
        @PathVariable Long personaId,
        @Valid @RequestBody VisibilityRequest body,
        HttpServletRequest request
    ) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        try {
            return ApiResponse.success(
                personas.changeVisibility(
                    actor, personaId, body.enabled(), body.reason(), context
                ),
                requestId(request)
            );
        } catch (BusinessException failure) {
            failure(
                actor, AdminAuditAction.CLUSTER_PERSONA_VISIBILITY_CHANGED,
                personaId, body.reason(), failure, context
            );
            throw failure;
        }
    }

    @PutMapping("/order")
    public ApiResponse<List<AdminClusterPersonaService.PersonaPolicyResponse>> reorder(
        @Valid @RequestBody OrderRequest body,
        HttpServletRequest request
    ) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        try {
            return ApiResponse.success(
                personas.reorder(actor, body.personaIds(), body.reason(), context),
                requestId(request)
            );
        } catch (BusinessException failure) {
            failure(
                actor, AdminAuditAction.CLUSTER_PERSONA_ORDER_CHANGED,
                null, body.reason(), failure, context
            );
            throw failure;
        }
    }

    private void failure(
        User actor,
        AdminAuditAction action,
        Long targetId,
        String reason,
        BusinessException failure,
        AdminAuditContext context
    ) {
        audits.recordFailureSafely(
            actor.getId(),
            action,
            AdminAuditTargetType.PERSONA,
            targetId,
            targetId == null ? "군집 페르소나 표시 순서" : "Persona #" + targetId,
            reason,
            failure.getErrorCode().name(),
            context,
            Map.of()
        );
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }

    public record VisibilityRequest(@NotNull Boolean enabled, @NotBlank String reason) { }

    public record OrderRequest(
        @NotEmpty @Size(max = AdminClusterPersonaService.MAX_VISIBLE) List<Long> personaIds,
        @NotBlank String reason
    ) { }
}
