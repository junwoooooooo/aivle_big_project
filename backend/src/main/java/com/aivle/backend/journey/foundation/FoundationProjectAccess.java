package com.aivle.backend.journey.foundation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Component;

@Component
public class FoundationProjectAccess {
    private final ProjectRepository projects;

    public FoundationProjectAccess(ProjectRepository projects) {
        this.projects = projects;
    }

    public Project requireOwned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    public Project requireOwnedForUpdate(Long ownerId, Long projectId) {
        Project project = projects.findByIdForUpdate(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
        if (!project.getOwner().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return project;
    }
}
