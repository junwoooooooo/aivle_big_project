package com.aivle.backend.admin;

import com.aivle.backend.analysis.feasibility.repository.FeasibilityAssessmentRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.common.entity.DocumentStatus;
import com.aivle.backend.common.entity.ProjectStage;
import com.aivle.backend.common.entity.ProjectStatus;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.entity.DocumentVersion;
import com.aivle.backend.document.repository.DocumentVersionRepository;
import com.aivle.backend.document.repository.ProjectDocumentRepository;
import com.aivle.backend.document.repository.StructuredPlanRepository;
import com.aivle.backend.persona.recommendation.repository.PersonaRecommendationRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final ProjectDocumentRepository documents;
    private final DocumentVersionRepository documentVersions;
    private final StructuredPlanRepository structuredPlans;
    private final LegalReviewRepository legalReviews;
    private final FeasibilityAssessmentRepository feasibilityAssessments;
    private final PersonaRecommendationRepository personaRecommendations;

    @Transactional(readOnly = true)
    public Page<ProjectListItem> list(ProjectQuery filter, Pageable pageable) {
        return projects.findAll(specification(filter), pageable).map(this::listItem);
    }

    @Transactional(readOnly = true)
    public ProjectDetail detail(Long projectId) {
        Project project = projects.findByIdAndDeletedAtIsNull(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        return new ProjectDetail(
            project.getId(),
            project.getTitle(),
            project.getDescription(),
            owner(project.getOwner()),
            ProjectArea.from(project.getStage()).name(),
            project.getStatus().name(),
            project.getStage().name(),
            project.getIndustryCategory(),
            project.getCreatedAt(),
            project.getUpdatedAt(),
            false,
            document(project.getId()),
            analyses(project)
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
            if (filter.area() != null) predicates.add(root.get("stage").in(filter.area().stages()));
            if (filter.status() != null) predicates.add(builder.equal(root.get("status"), filter.status()));
            if (filter.stage() != null) predicates.add(builder.equal(root.get("stage"), filter.stage()));
            if (hasText(filter.industryCategory())) {
                predicates.add(builder.equal(
                    builder.lower(root.get("industryCategory")),
                    filter.industryCategory().trim().toLowerCase(Locale.ROOT)
                ));
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
        return new ProjectListItem(
            project.getId(), project.getTitle(), owner(project.getOwner()),
            ProjectArea.from(project.getStage()).name(), project.getStatus().name(),
            project.getStage().name(), project.getIndustryCategory(),
            project.getCreatedAt(), project.getUpdatedAt()
        );
    }

    private OwnerSummary owner(User owner) {
        if (owner.isDeleted()) {
            return new OwnerSummary(owner.getId(), null, "탈퇴한 사용자", true);
        }
        return new OwnerSummary(owner.getId(), owner.getUsername(), owner.getName(), false);
    }

    private DocumentSummary document(Long projectId) {
        var activeDocuments = documents.findAllByProjectIdAndStatusAndDeletedAtIsNull(projectId, DocumentStatus.ACTIVE);
        if (activeDocuments.isEmpty()) return DocumentSummary.notStarted();
        var versions = documentVersions.findCurrentVersions(activeDocuments.stream().map(value -> value.getId()).toList());
        DocumentVersion latest = versions.stream()
            .max(Comparator.comparing(DocumentVersion::getUploadedAt).thenComparing(DocumentVersion::getId))
            .orElse(null);
        if (latest == null) return DocumentSummary.notStarted();
        String structuredStatus = structuredPlans.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .map(value -> value.getStatus().name())
            .orElse("NOT_STARTED");
        return new DocumentSummary(
            true,
            latest.getVersionNumber(),
            latest.getStoredFile().getOriginalFilename(),
            latest.getUploadedAt(),
            latest.getParseStatus().name(),
            structuredStatus
        );
    }

    private AnalysisCollection analyses(Project project) {
        Long projectId = project.getId();
        Long ownerId = project.getOwner().getId();
        AnalysisState legal = legalReviews
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, ownerId)
            .map(value -> new AnalysisState(true, value.getStatus().name(), value.getStartedAt(), value.getCompletedAt(), null))
            .orElseGet(AnalysisState::notStarted);
        AnalysisState feasibility = feasibilityAssessments
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, ownerId)
            .map(value -> new AnalysisState(true, value.getStatus().name(), value.getStartedAt(), value.getCompletedAt(), null))
            .orElseGet(AnalysisState::notStarted);
        AnalysisState persona = personaRecommendations
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, ownerId)
            .map(value -> new AnalysisState(
                true,
                value.getStatus().name(),
                value.getAnalysisJob().getStartedAt(),
                value.getCompletedAt(),
                null
            ))
            .orElseGet(AnalysisState::notStarted);
        return new AnalysisCollection(legal, feasibility, persona);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String contains(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static java.util.Optional<Long> parseLong(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    public record ProjectQuery(
        String keyword,
        String owner,
        ProjectArea area,
        ProjectStatus status,
        ProjectStage stage,
        String industryCategory,
        LocalDate createdFrom,
        LocalDate createdTo
    ) { }

    public record OwnerSummary(Long id, String username, String displayName, boolean deleted) { }

    public record ProjectListItem(
        Long id,
        String title,
        OwnerSummary owner,
        String area,
        String status,
        String stage,
        String industryCategory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) { }

    public record ProjectDetail(
        Long id,
        String title,
        String description,
        OwnerSummary owner,
        String area,
        String status,
        String stage,
        String industryCategory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted,
        DocumentSummary document,
        AnalysisCollection analyses
    ) { }

    public record DocumentSummary(
        boolean available,
        Integer version,
        String originalFilename,
        LocalDateTime uploadedAt,
        String processingStatus,
        String structuredStatus
    ) {
        static DocumentSummary notStarted() {
            return new DocumentSummary(false, null, null, null, "NOT_STARTED", "NOT_STARTED");
        }
    }

    public record AnalysisCollection(AnalysisState legalReview, AnalysisState feasibility, AnalysisState personaRecommendation) { }

    public record AnalysisState(
        boolean available,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorCode
    ) {
        static AnalysisState notStarted() {
            return new AnalysisState(false, "NOT_STARTED", null, null, null);
        }
    }
}
