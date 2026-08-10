package com.aivle.backend.admin;

import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProjectService {
    private final ProjectRepository projects;
    private final ProjectModuleStatusService moduleStatuses;

    @Transactional(readOnly = true)
    public Page<ProjectListItem> list(ProjectQuery filter, Pageable pageable) {
        return projects.findAll(specification(filter), pageable).map(this::listItem);
    }

    @Transactional(readOnly = true)
    public ProjectDetail detail(Long projectId) {
        Project project = projects.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        List<ModuleState> modules = moduleStatuses.findAll(project.getOwner().getId(), projectId)
            .stream().map(ModuleState::from).toList();
        return new ProjectDetail(
            project.getId(), project.getTitle(), project.getDescription(), owner(project.getOwner()),
            project.getStatus().name(), project.getIndustryCategory(), project.getCreatedAt(),
            project.getUpdatedAt(), modules
        );
    }

    private Specification<Project> specification(ProjectQuery filter) {
        return (root, query, builder) -> {
            var owner = root.join("owner");
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.isNull(owner.get("deletedAt")));
            if (hasText(filter.keyword())) {
                String pattern = contains(filter.keyword());
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)
                ));
            }
            if (hasText(filter.owner())) {
                String pattern = contains(filter.owner());
                var ownerPredicates = new ArrayList<Predicate>();
                ownerPredicates.add(builder.like(builder.lower(owner.get("username")), pattern));
                ownerPredicates.add(builder.like(builder.lower(owner.get("name")), pattern));
                parseLong(filter.owner()).ifPresent(id -> ownerPredicates.add(builder.equal(owner.get("id"), id)));
                predicates.add(builder.or(ownerPredicates.toArray(Predicate[]::new)));
            }
            if (filter.status() != null) predicates.add(builder.equal(root.get("status"), filter.status()));
            if (hasText(filter.industryCategory())) {
                predicates.add(builder.equal(builder.lower(root.get("industryCategory")),
                    filter.industryCategory().trim().toLowerCase(Locale.ROOT)));
            }
            if (filter.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom().atStartOfDay()));
            }
            if (filter.createdTo() != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), filter.createdTo().plusDays(1).atStartOfDay()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ProjectListItem listItem(Project project) {
        return new ProjectListItem(project.getId(), project.getTitle(), owner(project.getOwner()),
            project.getStatus().name(), project.getIndustryCategory(), project.getCreatedAt(), project.getUpdatedAt());
    }

    private OwnerSummary owner(User owner) {
        return owner.isDeleted()
            ? new OwnerSummary(owner.getId(), null, "Deleted user", true)
            : new OwnerSummary(owner.getId(), owner.getUsername(), owner.getName(), false);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String contains(String value) { return "%" + value.trim().toLowerCase(Locale.ROOT) + "%"; }
    private static java.util.Optional<Long> parseLong(String value) {
        try { return java.util.Optional.of(Long.parseLong(value.trim())); }
        catch (NumberFormatException exception) { return java.util.Optional.empty(); }
    }

    public record ProjectQuery(String keyword, String owner, ProjectStatus status,
                               String industryCategory, LocalDate createdFrom, LocalDate createdTo) { }
    public record OwnerSummary(Long id, String username, String displayName, boolean deleted) { }
    public record ProjectListItem(Long id, String title, OwnerSummary owner, String status,
                                  String industryCategory, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record ProjectDetail(Long id, String title, String description, OwnerSummary owner, String status,
                                String industryCategory, LocalDateTime createdAt, LocalDateTime updatedAt,
                                List<ModuleState> modules) { }
    public record ModuleState(String module, String status, String activeRunId, String sourceSnapshotId,
                              LocalDateTime updatedAt) {
        static ModuleState from(ProjectModuleStatusResponse value) {
            return new ModuleState(value.module().name(), value.status().name(), value.activeRunId(),
                value.sourceSnapshotId(), value.updatedAt());
        }
    }
}
