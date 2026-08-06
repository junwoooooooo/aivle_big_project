package com.aivle.backend.pipeline.legal.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, length = 1000) private String sourceUri;
    @Column(nullable = false, length = 71) private String contentHash;

    public static LegalEvidence create(LegalContextPack pack, String title, String sourceUri, String contentHash) {
        LegalEvidence evidence = new LegalEvidence();
        evidence.id = UUID.randomUUID().toString();
        evidence.contextPack = pack;
        evidence.projectId = pack.getProject().getId();
        evidence.title = title;
        evidence.sourceUri = sourceUri;
        evidence.contentHash = contentHash;
        return evidence;
    }
}
