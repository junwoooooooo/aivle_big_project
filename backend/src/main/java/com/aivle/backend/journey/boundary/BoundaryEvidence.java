package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "boundary_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoundaryEvidence extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "boundary_version_id", nullable = false) private RegulatoryBoundaryVersion boundaryVersion;
    @Column(nullable = false, length = 100) private String evidenceKey;
    @Column(nullable = false, length = 300) private String lawName;
    @Column(length = 200) private String article;
    @Column(length = 500) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String excerpt;
    @Column(length = 40) private String effectiveDate;
    @Column(nullable = false, length = 1000) private String sourceUrl;
    @Column(nullable = false, length = 30) private String sourceStatus;
    @Column(nullable = false, length = 30) private String sourceType;
    @Column(nullable = false, columnDefinition = "TEXT") private String plainSummary;
    @Column(nullable = false, columnDefinition = "TEXT") private String whyRelevant;
    @Column(nullable = false) private LocalDateTime retrievedAt;
    @Column(nullable = false, length = 71) private String contentHash;

    public static BoundaryEvidence create(RegulatoryBoundaryVersion boundaryVersion, String evidenceKey,
            String lawName, String article, String title, String excerpt, String effectiveDate,
            String sourceUrl, String sourceStatus) {
        return create(boundaryVersion, evidenceKey, "OFFICIAL_LAW", lawName, article, title, excerpt,
            excerpt, "공식 근거와 관련된 실행 경계를 확인합니다.", effectiveDate, sourceUrl,
            normalizeStatus(sourceStatus), LocalDateTime.now(), "sha256:" + "0".repeat(64));
    }

    public static BoundaryEvidence create(RegulatoryBoundaryVersion boundaryVersion, String evidenceKey,
            String sourceType, String lawName, String article, String title, String excerpt,
            String plainSummary, String whyRelevant, String effectiveDate, String sourceUrl,
            String sourceStatus, LocalDateTime retrievedAt, String contentHash) {
        requireText(evidenceKey, "evidence key");
        requireText(lawName, "law name");
        requireText(excerpt, "excerpt");
        requireText(sourceUrl, "source URL");
        requireText(sourceStatus, "source status");
        requireText(sourceType, "source type");
        requireText(plainSummary, "plain summary");
        requireText(whyRelevant, "why relevant");
        if (retrievedAt == null || contentHash == null || !contentHash.matches("sha256:[0-9a-f]{64}"))
            throw new IllegalArgumentException("retrieval time and content hash are required");
        BoundaryEvidence value = new BoundaryEvidence();
        value.project = boundaryVersion.getProject();
        value.boundaryVersion = boundaryVersion;
        value.evidenceKey = evidenceKey;
        value.lawName = lawName;
        value.article = article;
        value.title = title;
        value.excerpt = excerpt;
        value.effectiveDate = effectiveDate;
        value.sourceUrl = sourceUrl;
        value.sourceStatus = sourceStatus;
        value.sourceType = sourceType;
        value.plainSummary = plainSummary;
        value.whyRelevant = whyRelevant;
        value.retrievedAt = retrievedAt;
        value.contentHash = contentHash;
        return value;
    }

    private static String normalizeStatus(String status) {
        return switch (status) {
            case "SOURCE_COMPLETE" -> "COMPLETE";
            case "SOURCE_PARTIAL" -> "PARTIAL";
            case "REGISTRY_GAP" -> "WARNING";
            default -> status;
        };
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
