package com.aivle.backend.admin;

import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final Clock jobClock;
    private final AdminTaskRunService taskRuns;
    public AdminController.OverviewResponse overview() {
        long active = users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.USER, UserStatus.ACTIVE)
            + users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.ACTIVE);
        long locked = users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.USER, UserStatus.LOCKED)
            + users.countByRoleAndStatusAndDeletedAtIsNull(UserRole.ADMIN, UserStatus.LOCKED);
        long inProgressProjects = projects.countAdminVisibleByStatusIn(
            List.of(ProjectStatus.DRAFT, ProjectStatus.ACTIVE)
        );
        long pausedProjects = projects.countAdminVisibleByStatus(ProjectStatus.PAUSED);
        return new AdminController.OverviewResponse(
            new AdminController.UserMetrics(
                users.countByDeletedAtIsNull(),
                active,
                locked,
                users.countByStatusAndDeletedAtIsNull(UserStatus.DISABLED),
                users.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)
            ),
            new AdminController.ProjectMetrics(
                projects.countAdminVisible(),
                inProgressProjects,
                pausedProjects,
                projects.countAdminVisibleByStatus(ProjectStatus.COMPLETED),
                projects.countAdminVisibleCreatedSince(
                    LocalDateTime.now(jobClock).minusDays(7)
                )
            ),
            jobMetrics(),
            LocalDateTime.now(jobClock)
        );
    }

    private AdminController.JobMetrics jobMetrics() {
        var jobs = taskRuns.overview();
        return new AdminController.JobMetrics(
            "AVAILABLE".equals(jobs.availabilityStatus()),
            jobs.configurationStatus() + ":" + jobs.availabilityStatus(),
            jobs.pending(), jobs.running(), jobs.failed()
        );
    }
}
