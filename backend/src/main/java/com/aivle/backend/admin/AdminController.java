package com.aivle.backend.admin;

import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.service.UserDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminAccessService access;
    private final AdminUserService adminUsers;
    private final AdminProjectService adminProjects;
    private final AdminAuditQueryService auditQuery;
    private final AdminAuditService audits;
    private final AdminOverviewService overviewService;
    private final AdminReauthenticationService reauthentication;
    private final AdminSettingService adminSettings;
    private final UserDeletionService userDeletionService;
    private final AdminTaskRunService adminTaskRuns;

    @PostMapping("/reauthenticate")
    public ApiResponse<AdminReauthenticationService.IssuedToken> reauthenticate(@Valid @RequestBody ReauthenticationRequest body, HttpServletRequest request) {
        return ApiResponse.success(reauthentication.issue(access.requireAdmin(), body.password(), body.purpose(), AdminAuditContext.from(request)), requestId(request));
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewResponse> overview(HttpServletRequest request) {
        access.requireAdmin();
        return ApiResponse.success(overviewService.overview(), requestId(request));
    }

    @GetMapping("/users")
    public ApiResponse<Page<AdminUserService.AdminUserResponse>> users(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        HttpServletRequest request
    ) {
        access.requireAdmin();
        return ApiResponse.success(adminUsers.list(keyword, parseRole(role), parseStatus(status), pageable(page, size, sort)), requestId(request));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<AdminUserService.AdminUserResponse> user(@PathVariable Long userId, HttpServletRequest request) {
        access.requireAdmin();
        return ApiResponse.success(adminUsers.detail(userId), requestId(request));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserService.AdminUserResponse> updateStatus(@PathVariable Long userId, @Valid @RequestBody StatusRequest body, @RequestHeader(name = "X-Admin-Action-Token", required = false) String actionToken, HttpServletRequest request) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        try {
            UserStatus nextStatus = parseStatus(body.status());
            return ApiResponse.success(
                adminUsers.changeStatus(actor, userId, nextStatus, body.reason(), actionToken, context),
                requestId(request)
            );
        } catch (BusinessException failure) {
            auditFailure(actor, AdminAuditAction.USER_STATUS_CHANGED, AdminAuditTargetType.USER, userId, body.reason(), failure, context);
            throw failure;
        }
    }

    @PatchMapping("/users/{userId}/role")
    public ApiResponse<AdminUserService.AdminUserResponse> updateRole(@PathVariable Long userId, @Valid @RequestBody RoleRequest body, @RequestHeader(name = "X-Admin-Action-Token", required = false) String actionToken, HttpServletRequest request) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        try {
            return ApiResponse.success(
                adminUsers.changeRole(actor, userId, parseRole(body.role()), body.reason(), actionToken, context),
                requestId(request)
            );
        } catch (BusinessException failure) {
            auditFailure(actor, AdminAuditAction.USER_ROLE_CHANGED, AdminAuditTargetType.USER, userId, body.reason(), failure, context);
            throw failure;
        }
    }

    @PostMapping("/users/{userId}/sessions/revoke")
    public ApiResponse<Void> revokeSessions(@PathVariable Long userId, @Valid @RequestBody ReasonRequest body, HttpServletRequest request) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        try {
            adminUsers.revokeSessions(actor, userId, body.reason(), context);
            return ApiResponse.success(null, requestId(request));
        } catch (BusinessException failure) {
            auditFailure(actor, AdminAuditAction.USER_SESSION_REVOKED, AdminAuditTargetType.USER, userId, body.reason(), failure, context);
            throw failure;
        }
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(
        @PathVariable Long userId,
        @Valid @RequestBody ReasonRequest body,
        @RequestHeader(name = "X-Admin-Action-Token", required = false) String actionToken,
        HttpServletRequest request
    ) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        try {
            userDeletionService.deleteByAdmin(actor, userId, body.reason(), actionToken, context);
            return ApiResponse.success(null, requestId(request));
        } catch (BusinessException failure) {
            auditFailure(
                actor,
                AdminAuditAction.USER_DELETED_BY_ADMIN,
                AdminAuditTargetType.USER,
                userId,
                body.reason(),
                failure,
                context
            );
            throw failure;
        }
    }

    @GetMapping("/projects")
    public ApiResponse<Page<AdminProjectService.ProjectListItem>> projectList(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String industryCategory,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "updatedAt,desc") String sort,
        HttpServletRequest request
    ) {
        access.requireAdmin();
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var filter = new AdminProjectService.ProjectQuery(
            keyword, owner, parseProjectStatus(status), industryCategory, createdFrom, createdTo
        );
        return ApiResponse.success(adminProjects.list(filter, projectPageable(page, size, sort)), requestId(request));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<AdminProjectService.ProjectDetail> project(@PathVariable Long projectId, HttpServletRequest request) {
        access.requireAdmin();
        return ApiResponse.success(adminProjects.detail(projectId), requestId(request));
    }

    @GetMapping("/audit")
    public ApiResponse<Page<AdminAuditQueryService.AuditListItem>> audit(
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) String requestId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "occurredAt,desc") String sort,
        HttpServletRequest request
    ) {
        access.requireAdmin();
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var filter = new AdminAuditQueryService.AuditQuery(
            actor, parseAuditAction(action), parseAuditResult(result), parseAuditTargetType(targetType),
            requestId, occurredFrom, occurredTo
        );
        return ApiResponse.success(auditQuery.list(filter, auditPageable(page, size, sort)), requestId(request));
    }

    @GetMapping("/audit/{auditId}")
    public ApiResponse<AdminAuditQueryService.AuditDetail> auditDetail(@PathVariable Long auditId, HttpServletRequest request) {
        access.requireAdmin();
        return ApiResponse.success(auditQuery.detail(auditId), requestId(request));
    }

    @GetMapping("/settings")
    public ApiResponse<List<AdminSettingService.SettingResponse>> settings(HttpServletRequest request) {
        access.requireAdmin();
        return ApiResponse.success(adminSettings.list(), requestId(request));
    }

    @PatchMapping("/settings/{key}")
    public ApiResponse<AdminSettingService.SettingResponse> updateSetting(@PathVariable String key, @Valid @RequestBody SettingRequest body, @RequestHeader(name = "X-Admin-Action-Token", required = false) String actionToken, HttpServletRequest request) {
        User actor = access.requireAdmin();
        AdminAuditContext context = AdminAuditContext.from(request);
        return ApiResponse.success(
            adminSettings.update(actor, key, body.value(), body.reason(), actionToken, context),
            requestId(request)
        );
    }

    @GetMapping("/ai/services")
    public ApiResponse<AvailabilityResponse> aiServices(HttpServletRequest request) {
        access.requireAdmin();
        var jobs = adminTaskRuns.overview();
        return ApiResponse.success(
            new AvailabilityResponse(
                "AVAILABLE".equals(jobs.availabilityStatus()),
                jobs.configurationStatus() + ":" + jobs.availabilityStatus(),
                List.of()
            ),
            requestId(request)
        );
    }

    @GetMapping("/jobs")
    public ApiResponse<AdminTaskRunService.JobOverview> jobs(HttpServletRequest request) {
        access.requireAdmin();
        return ApiResponse.success(adminTaskRuns.overview(), requestId(request));
    }

    private Pageable pageable(int page, int size, String sort) {
        String[] parts = sort.split(",", 2);
        boolean supported = switch (parts[0]) {
            case "lastLoginAt", "username", "createdAt", "displayName" -> true;
            default -> false;
        };
        String property = supported && "displayName".equals(parts[0]) ? "name" : supported ? parts[0] : "createdAt";
        Sort.Direction direction = supported && parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(direction, property));
    }
    private Pageable projectPageable(int page, int size, String sort) {
        String[] parts = sort.split(",", 2);
        boolean supported = switch (parts[0]) {
            case "createdAt", "updatedAt", "title", "status" -> true;
            default -> false;
        };
        String property = supported ? parts[0] : "updatedAt";
        Sort.Direction direction = supported && parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(direction, property));
    }
    private Pageable auditPageable(int page, int size, String sort) {
        String[] parts = sort.split(",", 2);
        boolean supported = switch (parts[0]) {
            case "occurredAt", "actorUsername", "action", "result" -> true;
            default -> false;
        };
        String property = switch (supported ? parts[0] : "occurredAt") {
            case "actorUsername" -> "actor.username";
            case "action" -> "eventType";
            default -> supported ? parts[0] : "occurredAt";
        };
        Sort.Direction direction = supported && parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(direction, property));
    }
    private UserRole parseRole(String value) { if (value == null || value.isBlank()) return null; try { return UserRole.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_REQUEST); } }
    private UserStatus parseStatus(String value) { if (value == null || value.isBlank()) return null; try { return UserStatus.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_REQUEST); } }
    private com.aivle.backend.common.entity.ProjectStatus parseProjectStatus(String value) { if (value == null || value.isBlank()) return null; try { return com.aivle.backend.common.entity.ProjectStatus.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_REQUEST); } }
    private AdminAuditAction parseAuditAction(String value) { if (value == null || value.isBlank()) return null; try { return AdminAuditAction.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_REQUEST); } }
    private AdminAuditResult parseAuditResult(String value) { if (value == null || value.isBlank()) return null; try { return AdminAuditResult.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_REQUEST); } }
    private AdminAuditTargetType parseAuditTargetType(String value) { if (value == null || value.isBlank()) return null; try { return AdminAuditTargetType.valueOf(value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_REQUEST); } }
    private String requestId(HttpServletRequest request) { return request.getHeader("X-Request-Id"); }
    private void auditFailure(User actor, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId,
                              String reason, BusinessException failure, AdminAuditContext context) {
        audits.recordFailureSafely(
            actor.getId(), action, targetType, targetId,
            targetId == null ? targetType.name() : targetId.toString(),
            reason, failure.getErrorCode().name(), context, Map.of()
        );
    }

    public record StatusRequest(String status, @NotBlank(message = "reason is required") String reason) { }
    public record RoleRequest(String role, @NotBlank(message = "reason is required") String reason) { }
    public record ReasonRequest(@NotBlank(message = "reason is required") String reason) { }
    public record SettingRequest(String value, @NotBlank(message = "reason is required") String reason) { }
    public record ReauthenticationRequest(@NotBlank String password, @NotNull AdminActionPurpose purpose) { }
    public record UserMetrics(long total, long active, long locked, long disabled, long admins) { }
    public record ProjectMetrics(long total, long inProgress, long paused, long completed, long createdLast7Days) { }
    public record JobMetrics(boolean available, String reason, Long pending, Long running, Long failed) { }
    public record OverviewResponse(UserMetrics users, ProjectMetrics projects, JobMetrics jobs, LocalDateTime generatedAt) { }
    public record AvailabilityResponse(boolean available, String reason, List<Object> items) { }
}
