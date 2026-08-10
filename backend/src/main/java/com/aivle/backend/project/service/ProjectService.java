package com.aivle.backend.project.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.audit.AuditEventType;
import com.aivle.backend.audit.DomainAuditService;
import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.project.dto.request.*;
import com.aivle.backend.project.dto.response.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.text.Normalizer;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DomainAuditService auditService;
    private final ServicePolicyService servicePolicy;

    @Transactional
    public ProjectDetailResponse create(Long userId, CreateProjectRequest request) {
        servicePolicy.requireWriteAvailableForUser(userId);
        User owner = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        ensureUniqueTitle(userId, request.title(), null);
        return detail(projectRepository.save(Project.create(owner, request.title(), request.description(), request.industryCategory())));
    }

    public List<ProjectSummaryResponse> findAll(Long userId) {
        return projectRepository
                .findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::summary)
                .toList();
    }

    public ProjectDetailResponse find(Long userId, Long projectId) {
        return detail(ownedProject(userId, projectId));
    }

    @Transactional
    public ProjectDetailResponse update(Long userId, Long projectId, UpdateProjectRequest request) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Project project = ownedProject(userId, projectId);
        ensureUniqueTitle(userId, request.title(), projectId);
        project.updateBasicInfo(request.title(), request.description(), request.industryCategory());
        return detail(project);
    }

    @Transactional
    public void delete(Long userId, Long projectId, String requestId) {
        servicePolicy.requireWriteAvailableForUser(userId);
        Project project = ownedProject(userId, projectId);
        project.softDelete();
        auditService.record(userId, projectId, AuditEventType.PROJECT_DELETED, "PROJECT", projectId, requestId, java.util.Map.of());
    }

    private Project ownedProject(Long userId, Long projectId) {
        return projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void ensureUniqueTitle(Long userId, String title, Long ignoredProjectId) {
        String normalizedTitle = normalizeTitle(title);
        boolean duplicated = projectRepository.findAllByOwnerIdAndDeletedAtIsNull(userId).stream()
                .filter(project -> ignoredProjectId == null || !project.getId().equals(ignoredProjectId))
                .anyMatch(project -> normalizeTitle(project.getTitle()).equals(normalizedTitle));
        if (duplicated) throw new BusinessException(ErrorCode.PROJECT_NAME_ALREADY_EXISTS);
    }

    private String normalizeTitle(String title) {
        return Normalizer.normalize(title == null ? "" : title.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC)
                .toLowerCase(java.util.Locale.ROOT);
    }

    private ProjectSummaryResponse summary(Project p) {
        return new ProjectSummaryResponse(p.getId(), p.getTitle(), p.getIndustryCategory(),
                p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private ProjectDetailResponse detail(Project p) {
        return new ProjectDetailResponse(p.getId(), p.getOwner().getId(), p.getTitle(), p.getDescription(),
                p.getIndustryCategory(), p.getStatus(), p.getStartedAt(), p.getCompletedAt(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }
}
