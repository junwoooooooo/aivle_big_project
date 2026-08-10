package com.aivle.backend.pipeline.legal.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "legal_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalEvidence extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "context_pack_id", nullable = false) private LegalContextPack contextPack;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "source_type", nullable = false, length = 30) private String sourceType;
    @Column(name = "law_id", length = 100) private String lawId;
    @Column(name = "official_identifier", nullable = false, length = 100) private String officialIdentifier;
    @Column(name = "law_name", nullable = false, length = 500) private String lawName;
    @Column(name = "article_reference", nullable = false, length = 200) private String articleReference;
    @Column(nullable = false, length = 500) private String title;
    @Column(name = "official_source_uri", nullable = false, length = 1000) private String officialSourceUri;
    @Column(nullable = false, length = 10) private String jurisdiction;
    @Column(name = "promulgation_date", length = 20) private String promulgationDate;
    @Column(name = "effective_date", length = 20) private String effectiveDate;
    @Column(name = "retrieved_at", nullable = false) private LocalDateTime retrievedAt;
    @Column(name = "content_hash", nullable = false, length = 71) private String contentHash;
    @Column(name = "bounded_provision_summary", nullable = false, length = 1000) private String boundedProvisionSummary;
    @Column(name = "query_key", nullable = false, length = 71) private String queryKey;
    @Column(name = "registry_version", nullable = false, length = 80) private String registryVersion;

    public static LegalEvidence officialLaw(LegalContextPack pack, String lawId, String officialIdentifier,
            String lawName, String articleReference, String title, String officialSourceUri,
            String promulgationDate, String effectiveDate, LocalDateTime retrievedAt, String contentHash,
            String boundedProvisionSummary, String queryKey, String registryVersion) {
        if (pack == null || blank(officialIdentifier) || blank(lawName) || blank(articleReference)
            || blank(officialSourceUri) || "https://www.law.go.kr/".equals(officialSourceUri)
            || !officialSourceUri.startsWith("https://www.law.go.kr/")
            || retrievedAt == null || !hash(contentHash) || !hash(queryKey)
            || blank(boundedProvisionSummary) || boundedProvisionSummary.length() > 1000
            || blank(registryVersion)) {
            throw new IllegalArgumentException("official provision evidence is invalid");
        }
        LegalEvidence evidence = new LegalEvidence();
        evidence.id = UUID.randomUUID().toString();
        evidence.contextPack = pack;
        evidence.projectId = pack.getProject().getId();
        evidence.sourceType = "OFFICIAL_LAW";
        evidence.lawId = lawId;
        evidence.officialIdentifier = officialIdentifier;
        evidence.lawName = lawName;
        evidence.articleReference = articleReference;
        evidence.title = title == null ? "" : title;
        evidence.officialSourceUri = officialSourceUri;
        evidence.jurisdiction = "KR";
        evidence.promulgationDate = promulgationDate;
        evidence.effectiveDate = effectiveDate;
        evidence.retrievedAt = retrievedAt;
        evidence.contentHash = contentHash;
        evidence.boundedProvisionSummary = boundedProvisionSummary;
        evidence.queryKey = queryKey;
        evidence.registryVersion = registryVersion;
        return evidence;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
