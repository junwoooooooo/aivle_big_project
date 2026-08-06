package com.aivle.backend.document.entity;

import com.aivle.backend.common.entity.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.structure.AiStructuredPlanResult;
import com.aivle.backend.document.structure.StructuredPlanMappingResult;

@Entity
@Table(
    name = "structured_plans",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_version", columnNames = {"project_id", "version_number"}),
        @UniqueConstraint(name = "uk_plan_source_document_version", columnNames = {"source_document_version_id"})
    }
)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StructuredPlan extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_document_version_id", nullable = false) private DocumentVersion sourceDocumentVersion;
    @Column(nullable = false) private Integer versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StructuredPlanStatus status;
    @Column(nullable = false) private Integer completionRate;
    @Column(columnDefinition = "TEXT") private String rawExtractedJson;
    @Column(nullable = false) private Boolean confirmedByUser;
    private LocalDateTime confirmedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedBy;
    @Column(length = 100) private String provider;
    @Column(length = 100) private String modelName;
    @Column(length = 100) private String promptVersion;
    @Column(length = 100) private String parserName;
    @Column(length = 100) private String parserVersion;
    @Column(length = 64) private String rawResultHash;

    private StructuredPlan(
        Project project,
        DocumentVersion sourceDocumentVersion,
        int versionNumber,
        ParsedDocument parsedDocument,
        AiStructuredPlanResult aiResult,
        StructuredPlanMappingResult mapping,
        String processingSummaryJson
    ) {
        this.project = project;
        this.sourceDocumentVersion = sourceDocumentVersion;
        this.versionNumber = versionNumber;
        this.status = mapping.structuredPlanStatus();
        this.completionRate = mapping.completionRate();
        this.rawExtractedJson = processingSummaryJson;
        this.confirmedByUser = false;
        this.provider = aiResult.provider();
        this.modelName = aiResult.model();
        this.promptVersion = aiResult.promptVersion();
        this.parserName = parsedDocument.parserName();
        this.parserVersion = parsedDocument.parserVersion();
        this.rawResultHash = aiResult.rawResultHash();
    }

    public static StructuredPlan create(
        Project project,
        DocumentVersion sourceDocumentVersion,
        ParsedDocument parsedDocument,
        AiStructuredPlanResult aiResult,
        StructuredPlanMappingResult mapping,
        String processingSummaryJson
    ) {
        return new StructuredPlan(
            project,
            sourceDocumentVersion,
            sourceDocumentVersion.getVersionNumber(),
            parsedDocument,
            aiResult,
            mapping,
            processingSummaryJson
        );
    }

    public boolean isEditable() {
        return status != StructuredPlanStatus.CONFIRMED && !isDeleted();
    }

    public void recalculateCompletion(int completed, int required) {
        if (!isEditable()) {
            throw new IllegalStateException("confirmed plans are immutable");
        }
        if (required <= 0 || completed < 0 || completed > required) {
            throw new IllegalArgumentException("invalid completion counts");
        }
        this.completionRate = (completed * 100) / required;
        this.status = completed == required
            ? StructuredPlanStatus.DRAFT
            : StructuredPlanStatus.NEEDS_INPUT;
    }

    public void confirm(User user, LocalDateTime now) {
        if (status != StructuredPlanStatus.DRAFT || completionRate != 100) {
            throw new IllegalStateException("only complete draft plans can be confirmed");
        }
        this.status = StructuredPlanStatus.CONFIRMED;
        this.confirmedByUser = true;
        this.confirmedAt = now;
        this.confirmedBy = user;
    }
}
