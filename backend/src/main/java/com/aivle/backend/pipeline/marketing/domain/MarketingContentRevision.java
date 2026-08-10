package com.aivle.backend.pipeline.marketing.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "pipeline_marketing_content_revisions", uniqueConstraints = @UniqueConstraint(name = "uk_pipeline_marketing_revision_number", columnNames = {"content_id", "revision_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingContentRevision extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "content_id", nullable = false, length = 64) private String contentId;
    @Column(name = "revision_number", nullable = false) private int revisionNumber;
    @Enumerated(EnumType.STRING) @Column(name = "revision_type", nullable = false, length = 30) private MarketingRevisionType revisionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private MarketingRevisionOrigin origin;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(name = "created_by_user_id") private Long createdByUserId;

    public static MarketingContentRevision create(String contentId, int number, MarketingRevisionType type,
            MarketingRevisionOrigin origin, String json, Long userId) {
        MarketingContentRevision value = new MarketingContentRevision(); value.id = UUID.randomUUID().toString();
        value.contentId = contentId; value.revisionNumber = number; value.revisionType = type;
        value.origin = origin; value.resultJson = json; value.createdByUserId = userId; return value;
    }
}
